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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.amazon.kinesis.messaging.lib.model.RequestEntry;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;

// @formatter:off
@RequiredArgsConstructor
final class RequestEntryInternalFactory {

  private final ObjectMapper objectMapper;

  public RequestEntryInternal create(final RequestEntry<?> requestEntry, final byte[] bytes) {
    return RequestEntryInternal.builder()
        .withCreateTime(requestEntry.getCreateTime())
        .withId(requestEntry.getId())
        .withValue(ByteBuffer.wrap(bytes))
        .build();
  }

  public RequestEntryInternal create(final RequestEntry<?> requestEntry) {
    return create(requestEntry, convertPayload(requestEntry));
  }

  @SneakyThrows
  public byte[] convertPayload(final RequestEntry<?> requestEntry) {
    return requestEntry.getValue() instanceof String
      ? String.class.cast(requestEntry.getValue()).getBytes(StandardCharsets.UTF_8)
      : objectMapper.writeValueAsBytes(requestEntry.getValue());
  }

  @Getter
  @ToString
  @RequiredArgsConstructor
  @Builder(setterPrefix = "with")
  static class RequestEntryInternal {

    private final long createTime;

    private final String id;

    private final ByteBuffer value;

    public int size() {
      return value.capacity();
    }

  }

}
// @formatter:on
