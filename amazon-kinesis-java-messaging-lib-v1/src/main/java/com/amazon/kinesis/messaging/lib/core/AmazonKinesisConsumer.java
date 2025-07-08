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
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.PutRecordsRequest;
import com.amazonaws.services.kinesis.model.PutRecordsRequestEntry;
import com.amazonaws.services.kinesis.model.PutRecordsResult;
import com.amazonaws.services.kinesis.model.PutRecordsResultEntry;
import com.fasterxml.jackson.databind.ObjectMapper;

// @formatter:off
@SuppressWarnings("java:S6204")
class AmazonKinesisConsumer<E> extends AbstractAmazonKinesisConsumer<AmazonKinesis, PutRecordsRequest, PutRecordsResult, E> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AmazonKinesisConsumer.class);

  public AmazonKinesisConsumer(
    final AmazonKinesis amazonKinesisClient,
    final StreamProperty streamProperty,
    final ObjectMapper objectMapper,
    final Queue<ListenableFutureRegistry> pendingRequests,
    final BlockingQueue<RequestEntry<E>> streamRequests,
    final ExecutorService executorService,
    final UnaryOperator<PutRecordsRequest> publishDecorator) {
    super(amazonKinesisClient, streamProperty, objectMapper, pendingRequests, streamRequests, executorService, publishDecorator);
  }

  @Override
  protected PutRecordsResult publish(final PutRecordsRequest putRecordsRequest) {
    return amazonKinesisClient.putRecords(putRecordsRequest);
  }

  @Override
  protected BiFunction<String, List<RequestEntryInternal>, PutRecordsRequest> supplierPublishRequest() {
    return (streamArn, requestEntries) -> {
      final List<PutRecordsRequestEntry> entries = requestEntries.stream()
        .map(entry -> new PutRecordsRequestEntry().withData(entry.getValue()))
        .collect(Collectors.toList());
      return new PutRecordsRequest().withRecords(entries).withStreamARN(streamArn);
    };
  }

  @Override
  protected void handleError(final PutRecordsRequest publishBatchRequest, final Throwable throwable) {
    final String code = throwable instanceof AmazonServiceException ? AmazonServiceException.class.cast(throwable).getErrorCode() : "000";
    final String message = throwable instanceof AmazonServiceException ? AmazonServiceException.class.cast(throwable).getErrorMessage() : throwable.getMessage();

    LOGGER.error(throwable.getMessage(), throwable);

    publishBatchRequest.getRecords().forEach(entry -> {
      final ListenableFutureRegistry listenableFuture = pendingRequests.poll();
      listenableFuture.fail(ResponseFailEntry.builder()
        .withCode(code)
        .withMessage(message)
        .build());
    });
  }

  @Override
  protected void handleResponse(final PutRecordsResult publishBatchResult) {
    for (final PutRecordsResultEntry entry : publishBatchResult.getRecords()) {
      final ListenableFutureRegistry listenableFuture = pendingRequests.poll();

      if (StringUtils.isEmpty(entry.getErrorCode())) {
        listenableFuture.success(ResponseSuccessEntry.builder()
          .withShardId(entry.getShardId())
          .withSequenceNumber(entry.getSequenceNumber())
          .build());
      }

      if (StringUtils.isNotEmpty(entry.getErrorCode())) {
        listenableFuture.fail(ResponseFailEntry.builder()
          .withCode(entry.getErrorCode())
          .withMessage(entry.getErrorMessage())
          .build());
      }
    }
  }

}
// @formatter:on
