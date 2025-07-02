/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazon.kinesis.messaging.lib.core;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.kinesis.messaging.lib.core.RequestEntryInternalFactory.RequestEntryInternal;
import com.amazon.kinesis.messaging.lib.model.RequestEntry;
import com.amazon.kinesis.messaging.lib.model.ResponseFailEntry;
import com.amazon.kinesis.messaging.lib.model.ResponseSuccessEntry;
import com.amazon.kinesis.messaging.lib.model.StreamProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResultEntry;

// @formatter:off
@SuppressWarnings("java:S6204")
class AmazonKinesisConsumer<E> extends AbstractAmazonKinesisConsumer<KinesisClient, PutRecordsRequest, PutRecordsResponse, E> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AmazonKinesisConsumer.class);

  public AmazonKinesisConsumer(
      final KinesisClient amazonKinesisClient,
      final StreamProperty streamProperty,
      final ObjectMapper objectMapper,
      final Queue<ListenableFutureRegistry> pendingRequests,
      final BlockingQueue<RequestEntry<E>> streamRequests,
      final ExecutorService executorService,
      final UnaryOperator<PutRecordsRequest> publishDecorator) {
    super(amazonKinesisClient, streamProperty, objectMapper, pendingRequests, streamRequests, executorService, publishDecorator);
  }

  @Override
  protected PutRecordsResponse publish(final PutRecordsRequest publishBatchRequest) {
    return amazonKinesisClient.putRecords(publishBatchRequest);
  }

  @Override
  protected BiFunction<String, List<RequestEntryInternal>, PutRecordsRequest> supplierPublishRequest() {
    return (streamArn, requestEntries) -> {
      final List<PutRecordsRequestEntry> entries = requestEntries.stream()
        .map(entry -> PutRecordsRequestEntry.builder().data(SdkBytes.fromByteBuffer(entry.getValue())).build())
        .collect(Collectors.toList());
      return PutRecordsRequest.builder().records(entries).streamARN(streamArn).build();
    };
  }

  @Override
  protected void handleError(final PutRecordsRequest publishBatchRequest, final Throwable throwable) {
    final String code = throwable instanceof AwsServiceException ? AwsServiceException.class.cast(throwable).awsErrorDetails().errorCode() : "000";
    final String message = throwable instanceof AwsServiceException ? AwsServiceException.class.cast(throwable).awsErrorDetails().errorMessage() : throwable.getMessage();

    LOGGER.error(throwable.getMessage(), throwable);

    publishBatchRequest.records().forEach(entry -> {
      final ListenableFutureRegistry listenableFuture = pendingRequests.poll();
      listenableFuture.fail(ResponseFailEntry.builder()
        .withCode(code)
        .withMessage(message)
        .build());
    });
  }

  @Override
  protected void handleResponse(final PutRecordsResponse publishBatchResult) {
    for (final PutRecordsResultEntry entry : publishBatchResult.records()) {
      final ListenableFutureRegistry listenableFuture = pendingRequests.poll();

      if (StringUtils.isEmpty(entry.errorCode())) {
        listenableFuture.success(ResponseSuccessEntry.builder()
          .withShardId(entry.shardId())
          .withSequenceNumber(entry.sequenceNumber())
          .build());
      }

      if (StringUtils.isNotEmpty(entry.errorCode())) {
        listenableFuture.fail(ResponseFailEntry.builder()
          .withCode(entry.errorCode())
          .withMessage(entry.errorMessage())
          .build());
      }
    }
  }

}
// @formatter:on
