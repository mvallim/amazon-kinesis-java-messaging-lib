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

import java.util.concurrent.CompletableFuture;

import com.amazon.kinesis.messaging.lib.model.StreamProperty;
import com.amazon.kinesis.messaging.lib.model.RequestEntry;
import com.amazon.kinesis.messaging.lib.model.ResponseFailEntry;
import com.amazon.kinesis.messaging.lib.model.ResponseSuccessEntry;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

// @formatter:off
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
abstract class AbstractAmazonKinesisTemplate<C, R, O, E> {

  private final AbstractAmazonKinesisProducer<E> amazonKinesisProducer;

  private final AbstractAmazonKinesisConsumer<C, R, O, E> amazonKinesisConsumer;

  public ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> send(final RequestEntry<E> requestEntry) {
    return amazonKinesisProducer.send(requestEntry);
  }

  public void shutdown() {
    amazonKinesisProducer.shutdown();
    amazonKinesisConsumer.shutdown();
  }

  public CompletableFuture<Void> await() {
    return amazonKinesisConsumer.await();
  }

  protected static AmazonKinesisThreadPoolExecutor getAmazonKinesisThreadPoolExecutor(final StreamProperty topicProperty) {
    return new AmazonKinesisThreadPoolExecutor(1);
  }

}
// @formatter:on
