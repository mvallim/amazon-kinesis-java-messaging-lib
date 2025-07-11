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

import java.util.Objects;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AmazonKinesisThreadPoolExecutor extends ThreadPoolExecutor {

  private final AtomicInteger activeTaskCount = new AtomicInteger();

  private final AtomicInteger failedTaskCount = new AtomicInteger();

  private final AtomicInteger succeededTaskCount = new AtomicInteger();

  public AmazonKinesisThreadPoolExecutor(final int maximumPoolSize) {
    super(0, maximumPoolSize, 60, TimeUnit.SECONDS, new SynchronousQueue<>(), new BlockingSubmissionPolicy(30000));
  }

  public int getActiveTaskCount() {
    return activeTaskCount.get();
  }

  public int getFailedTaskCount() {
    return failedTaskCount.get();
  }

  public int getSucceededTaskCount() {
    return succeededTaskCount.get();
  }

  @Override
  protected void beforeExecute(final Thread thread, final Runnable runnable) {
    try {
      super.beforeExecute(thread, runnable);
    } finally {
      activeTaskCount.incrementAndGet();
    }
  }

  @Override
  protected void afterExecute(final Runnable runnable, final Throwable throwable) {
    try {
      super.afterExecute(runnable, throwable);
    } finally {
      if (Objects.nonNull(throwable)) {
        failedTaskCount.incrementAndGet();
      } else {
        succeededTaskCount.incrementAndGet();
      }
      activeTaskCount.decrementAndGet();
    }
  }

}
