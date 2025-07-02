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
import java.util.concurrent.ExecutorService;

import com.amazon.kinesis.messaging.lib.model.RequestEntry;

// @formatter:off
class AmazonKinesisProducer<E> extends AbstractAmazonKinesisProducer<E> {

  public AmazonKinesisProducer(
      final Queue<ListenableFutureRegistry> pendingRequests,
      final BlockingQueue<RequestEntry<E>> streamRequests,
      final ExecutorService executorService) {
    super(pendingRequests, streamRequests, executorService);
  }

}
// @formatter:on
