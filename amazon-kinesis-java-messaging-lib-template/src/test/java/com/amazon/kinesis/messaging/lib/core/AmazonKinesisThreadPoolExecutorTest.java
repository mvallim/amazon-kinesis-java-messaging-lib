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

import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

// @formatter:off
class AmazonKinesisThreadPoolExecutorTest {

  @Test
  void testSuccessCounters() {
    final AmazonKinesisThreadPoolExecutor amazonKinesisThreadPoolExecutor = new AmazonKinesisThreadPoolExecutor(10);

    assertThat(amazonKinesisThreadPoolExecutor.getActiveTaskCount(), is(0));
    assertThat(amazonKinesisThreadPoolExecutor.getSucceededTaskCount(), is(0));
    assertThat(amazonKinesisThreadPoolExecutor.getFailedTaskCount(), is(0));
    assertThat(amazonKinesisThreadPoolExecutor.getCorePoolSize(), is(0));
  }

  @Test
  void testSuccessSucceededTaskCount() throws InterruptedException {
    final AmazonKinesisThreadPoolExecutor amazonKinesisThreadPoolExecutor = new AmazonKinesisThreadPoolExecutor(10);

    assertThat(amazonKinesisThreadPoolExecutor.getSucceededTaskCount(), is(0));

    for(int i = 0; i < 300; i++) {
      amazonKinesisThreadPoolExecutor.execute(() -> {
        try {
          Thread.sleep(1);
        } catch (final InterruptedException e) {
          e.printStackTrace();
        }
      });
    }

    amazonKinesisThreadPoolExecutor.shutdown();

    if (!amazonKinesisThreadPoolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
      amazonKinesisThreadPoolExecutor.shutdownNow();
    }

    assertThat(amazonKinesisThreadPoolExecutor.getActiveTaskCount(), is(0));
    assertThat(amazonKinesisThreadPoolExecutor.getSucceededTaskCount(), is(300));
    assertThat(amazonKinesisThreadPoolExecutor.getFailedTaskCount(), is(0));
  }

  @Test
  void testSuccessFailedTaskCount() throws InterruptedException {
    final AmazonKinesisThreadPoolExecutor amazonKinesisThreadPoolExecutor = new AmazonKinesisThreadPoolExecutor(10);

    assertThat(amazonKinesisThreadPoolExecutor.getSucceededTaskCount(), is(0));

    for(int i = 0; i < 300; i++) {
      amazonKinesisThreadPoolExecutor.execute(() -> { throw new RuntimeException(); });
    }

    amazonKinesisThreadPoolExecutor.shutdown();

    if (!amazonKinesisThreadPoolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
      amazonKinesisThreadPoolExecutor.shutdownNow();
    }

    assertThat(amazonKinesisThreadPoolExecutor.getActiveTaskCount(), is(0));
    assertThat(amazonKinesisThreadPoolExecutor.getSucceededTaskCount(), is(0));
    assertThat(amazonKinesisThreadPoolExecutor.getFailedTaskCount(), is(300));
  }

  @Test
  void testSuccessActiveTaskCount() throws InterruptedException {
    final AmazonKinesisThreadPoolExecutor amazonKinesisThreadPoolExecutor = new AmazonKinesisThreadPoolExecutor(10);

    assertThat(amazonKinesisThreadPoolExecutor.getSucceededTaskCount(), is(0));

    for(int i = 0; i < 10; i++) {
      amazonKinesisThreadPoolExecutor.execute(() -> {
        while(true) {
          try {
            Thread.sleep(1);
          } catch (final InterruptedException e) {
            e.printStackTrace();
          }
        }
      });
    }

    amazonKinesisThreadPoolExecutor.shutdown();

    if (!amazonKinesisThreadPoolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
      amazonKinesisThreadPoolExecutor.shutdownNow();
    }

    assertThat(amazonKinesisThreadPoolExecutor.getActiveTaskCount(), is(10));
    assertThat(amazonKinesisThreadPoolExecutor.getSucceededTaskCount(), is(0));
    assertThat(amazonKinesisThreadPoolExecutor.getFailedTaskCount(), is(0));
  }

  @Test
  void testSuccessBlockingSubmissionPolicy() throws InterruptedException {
    final AmazonKinesisThreadPoolExecutor amazonKinesisThreadPoolExecutor = new AmazonKinesisThreadPoolExecutor(1);

    amazonKinesisThreadPoolExecutor.execute(() -> {
      while(true) {
        try {
          Thread.sleep(1);
        } catch (final InterruptedException e) {
          e.printStackTrace();
        }
      }
    });

    catchThrowableOfType(() -> amazonKinesisThreadPoolExecutor.execute(() -> { }), RejectedExecutionException.class);
  }

}
// @formatter:on
