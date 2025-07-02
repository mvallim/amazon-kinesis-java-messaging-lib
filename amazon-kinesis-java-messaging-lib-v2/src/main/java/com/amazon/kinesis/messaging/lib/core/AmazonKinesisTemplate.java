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

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

import com.amazon.kinesis.messaging.lib.concurrent.RingBufferBlockingQueue;
import com.amazon.kinesis.messaging.lib.model.RequestEntry;
import com.amazon.kinesis.messaging.lib.model.StreamProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;

// @formatter:off
public class AmazonKinesisTemplate<E> extends AbstractAmazonKinesisTemplate<KinesisClient, PutRecordsRequest, PutRecordsResponse, E> {

  private AmazonKinesisTemplate(
      final KinesisClient amazonKinesisClient,
      final StreamProperty streamProperty,
      final Queue<ListenableFutureRegistry> pendingRequests,
      final BlockingQueue<RequestEntry<E>> streamRequests,
      final ObjectMapper objectMapper,
      final UnaryOperator<PutRecordsRequest> publishDecorator) {
    super(
      new AmazonKinesisProducer<>(pendingRequests, streamRequests, Executors.newSingleThreadExecutor()),
      new AmazonKinesisConsumer<>(amazonKinesisClient, streamProperty, objectMapper, pendingRequests, streamRequests, getAmazonKinesisThreadPoolExecutor(streamProperty), publishDecorator)
    );
  }

  public AmazonKinesisTemplate(final KinesisClient amazonKinesisClient, final StreamProperty streamProperty) {
    this(amazonKinesisClient, streamProperty, UnaryOperator.identity());
  }

  public AmazonKinesisTemplate(final KinesisClient amazonKinesisClient, final StreamProperty streamProperty, final UnaryOperator<PutRecordsRequest> publishDecorator) {
    this(amazonKinesisClient, streamProperty, new ObjectMapper(), publishDecorator);
  }

  public AmazonKinesisTemplate(final KinesisClient amazonKinesisClient, final StreamProperty streamProperty, final BlockingQueue<RequestEntry<E>> streamRequests) {
    this(amazonKinesisClient, streamProperty, streamRequests, UnaryOperator.identity());
  }

  public AmazonKinesisTemplate(final KinesisClient amazonKinesisClient, final StreamProperty streamProperty, final BlockingQueue<RequestEntry<E>> streamRequests, final UnaryOperator<PutRecordsRequest> publishDecorator) {
    this(amazonKinesisClient, streamProperty, streamRequests, new ObjectMapper(), publishDecorator);
  }

  public AmazonKinesisTemplate(final KinesisClient amazonKinesisClient, final StreamProperty streamProperty, final ObjectMapper objectMapper) {
    this(amazonKinesisClient, streamProperty, objectMapper, UnaryOperator.identity());
  }

  public AmazonKinesisTemplate(final KinesisClient amazonKinesisClient, final StreamProperty streamProperty, final ObjectMapper objectMapper, final UnaryOperator<PutRecordsRequest> publishDecorator) {
    this(amazonKinesisClient, streamProperty, new RingBufferBlockingQueue<>(streamProperty.getMaxBatchSize()), objectMapper, publishDecorator);
  }

  public AmazonKinesisTemplate(final KinesisClient amazonKinesisClient, final StreamProperty streamProperty, final BlockingQueue<RequestEntry<E>> streamRequests, final ObjectMapper objectMapper) {
    this(amazonKinesisClient, streamProperty, streamRequests, objectMapper, UnaryOperator.identity());
  }

  public AmazonKinesisTemplate(final KinesisClient amazonKinesisClient, final StreamProperty streamProperty, final BlockingQueue<RequestEntry<E>> streamRequests, final ObjectMapper objectMapper, final UnaryOperator<PutRecordsRequest> publishDecorator) {
    this(amazonKinesisClient, streamProperty, new ConcurrentLinkedQueue<>(), streamRequests, objectMapper, publishDecorator);
  }

}
// @formatter:on
