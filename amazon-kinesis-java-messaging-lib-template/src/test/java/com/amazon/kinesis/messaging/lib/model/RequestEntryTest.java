/*
 * Copyright 2022 the original author or authors.
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

package com.amazon.kinesis.messaging.lib.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

// @formatter:off
class RequestEntryTest {

  @Test
  void testSuccess() {
    final RequestEntry<Object> requestEntry = RequestEntry.builder()
      .withCreateTime(12345)
      .withId("id")
      .withValue("value")
      .build();

    assertThat(requestEntry.getCreateTime(), equalTo(12345L));
    assertThat(requestEntry.getId(), equalTo("id"));
    assertThat(requestEntry.getValue(), equalTo("value"));
  }

}
// @formatter:on
