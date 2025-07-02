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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazon.kinesis.messaging.lib.concurrent.RingBufferBlockingQueue;
import com.amazon.kinesis.messaging.lib.core.helper.ConsumerHelper;
import com.amazon.kinesis.messaging.lib.model.RequestEntry;
import com.amazon.kinesis.messaging.lib.model.ResponseFailEntry;
import com.amazon.kinesis.messaging.lib.model.ResponseSuccessEntry;
import com.amazon.kinesis.messaging.lib.model.StreamProperty;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.PutRecordsRequest;
import com.amazonaws.services.kinesis.model.PutRecordsResult;
import com.amazonaws.services.kinesis.model.PutRecordsResultEntry;

// @formatter:off
@ExtendWith(MockitoExtension.class)
class AmazonKinesisProducerTest {

  private AmazonKinesisTemplate<Object> kinesisTemplate;

  @Mock
  private AmazonKinesis amazonKinesis;

  @Captor
  private ArgumentCaptor<ResponseSuccessEntry> argumentCaptorSuccess;

  @Captor
  private ArgumentCaptor<ResponseFailEntry> argumentCaptorFailure;

  @BeforeEach
  public void before() throws Exception {
    final StreamProperty streamProperty = StreamProperty.builder()
      .linger(50L)
      .maxBatchSize(10)
      .streamArn("arn:aws:kinesis:us-east-2:000000000000:stream/mystream")
      .build();

    kinesisTemplate = new AmazonKinesisTemplate<>(amazonKinesis, streamProperty, new RingBufferBlockingQueue<>(1024));
  }

  @Test
  void testSuccess() {
    final PutRecordsResultEntry publishBatchResultEntry = new PutRecordsResultEntry();
    publishBatchResultEntry.setShardId("shard-1");
    publishBatchResultEntry.setSequenceNumber("012345678901234567890");

    final PutRecordsResult publishBatchResult = new PutRecordsResult();
    publishBatchResult.setRecords(Collections.singleton(publishBatchResultEntry));

    when(amazonKinesis.putRecords(any())).thenReturn(publishBatchResult);

    kinesisTemplate.send(RequestEntry.builder().build()).addCallback(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getShardId(), is("shard-1"));
      assertThat(result.getSequenceNumber(), is("012345678901234567890"));
    });

    kinesisTemplate.await().thenAccept(result -> {
      kinesisTemplate.shutdown();
      verify(amazonKinesis, timeout(10000).times(1)).putRecords(any());
    }).join();

  }

  @Test
  void testFailure() {
    final PutRecordsResultEntry publishBatchResultEntry = new PutRecordsResultEntry();
    publishBatchResultEntry.setErrorCode("1");
    publishBatchResultEntry.setErrorMessage("ErrorMessage");

    final PutRecordsResult publishBatchResult = new PutRecordsResult();
    publishBatchResult.setRecords(Collections.singleton(publishBatchResultEntry));

    when(amazonKinesis.putRecords(any())).thenReturn(publishBatchResult);

    kinesisTemplate.send(RequestEntry.builder().build()).addCallback(null, result -> {
      assertThat(result, notNullValue());
      assertThat(result.getCode(), is("1"));
      assertThat(result.getMessage(), is("ErrorMessage"));
    });

    kinesisTemplate.await().thenAccept(result -> {
      kinesisTemplate.shutdown();
      verify(amazonKinesis, timeout(10000).times(1)).putRecords(any());
    }).join();

  }

  @Test
  void testSuccessMultipleEntry() {

    when(amazonKinesis.putRecords(any())).thenAnswer(invocation -> {
      final PutRecordsRequest request = invocation.getArgument(0, PutRecordsRequest.class);
      final List<PutRecordsResultEntry> resultEntries = request.getRecords().stream()
        .map(entry -> new PutRecordsResultEntry().withSequenceNumber("1234567890").withShardId("shard-1"))
        .collect(Collectors.toList());
      return new PutRecordsResult().withRecords(resultEntries);
    });

    final ConsumerHelper<ResponseSuccessEntry> successCallback = spy(new ConsumerHelper<>(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getShardId(), is("shard-1"));
      assertThat(result.getSequenceNumber(), is("1234567890"));
    }));

    entries(30000).forEach(entry -> {
      kinesisTemplate.send(entry).addCallback(successCallback);
    });

    kinesisTemplate.await().thenAccept(result -> {
      verify(successCallback, timeout(300000).times(30000)).accept(argumentCaptorSuccess.capture());
      verify(amazonKinesis, atLeastOnce()).putRecords(any());
    }).join();

    for (final ResponseSuccessEntry responseSuccessEntry : argumentCaptorSuccess.getAllValues()) {
      successCallback.accept(responseSuccessEntry);
    }
  }

  @Test
  void testFailureMultipleEntry() {

    when(amazonKinesis.putRecords(any())).thenAnswer(invocation -> {
      final PutRecordsRequest request = invocation.getArgument(0, PutRecordsRequest.class);
      final List<PutRecordsResultEntry> resultEntries = request.getRecords().stream()
        .map(entry -> new PutRecordsResultEntry().withErrorCode("1").withErrorMessage("ErrorMessage"))
        .collect(Collectors.toList());
      return new PutRecordsResult().withRecords(resultEntries);
    });

    final ConsumerHelper<ResponseFailEntry> failureCallback = spy(new ConsumerHelper<>(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getCode(), is("1"));
      assertThat(result.getMessage(), is("ErrorMessage"));
    }));

    entries(30000).forEach(entry -> {
      kinesisTemplate.send(entry).addCallback(null, failureCallback);
    });

    kinesisTemplate.await().thenAccept(result -> {
      verify(failureCallback, timeout(300000).times(30000)).accept(argumentCaptorFailure.capture());
      verify(amazonKinesis, atLeastOnce()).putRecords(any());
    }).join();

    for (final ResponseFailEntry responseFailEntry : argumentCaptorFailure.getAllValues()) {
      failureCallback.accept(responseFailEntry);
    }
  }

  @Test
  void testFailRiseRuntimeException() {
    final String id = UUID.randomUUID().toString();

    when(amazonKinesis.putRecords(any(PutRecordsRequest.class))).thenThrow(new RuntimeException("RuntimeException"));

    final ConsumerHelper<ResponseFailEntry> failureCallback = spy(new ConsumerHelper<>(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getCode(), is("000"));
      assertThat(result.getMessage(), is("RuntimeException"));
    }));

    kinesisTemplate.send(RequestEntry.builder().withId(id).build()).addCallback(null, failureCallback);

    kinesisTemplate.await().thenAccept(result -> {
      verify(failureCallback, timeout(10000).times(1)).accept(argumentCaptorFailure.capture());
      verify(amazonKinesis, timeout(10000).times(1)).putRecords(any(PutRecordsRequest.class));
    }).join();

    failureCallback.accept(argumentCaptorFailure.getValue());
  }

  @Test
  void testFailRiseAwsServiceException() {
    final String id = UUID.randomUUID().toString();

    when(amazonKinesis.putRecords(any(PutRecordsRequest.class))).thenThrow(new AmazonServiceException("error"));

    final ConsumerHelper<ResponseFailEntry> failureCallback = spy(new ConsumerHelper<>(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getCode(), nullValue());
      assertThat(result.getMessage(), is("error"));
    }));

    kinesisTemplate.send(RequestEntry.builder().withId(id).build()).addCallback(null, failureCallback);

    kinesisTemplate.await().thenAccept(result -> {
      verify(failureCallback, timeout(10000).times(1)).accept(argumentCaptorFailure.capture());
      verify(amazonKinesis, timeout(10000).times(1)).putRecords(any(PutRecordsRequest.class));
    }).join();

    failureCallback.accept(argumentCaptorFailure.getValue());
  }

  @Test
  void testSuccessBlockingSubmissionPolicy() {
    final StreamProperty streamProperty = StreamProperty.builder()
        .linger(50L)
        .maxBatchSize(1)
        .streamArn("arn:aws:kinesis:us-east-2:000000000000:stream/mystream")
        .build();

    final AmazonKinesisTemplate<Object> kinesisTemplate = new AmazonKinesisTemplate<>(amazonKinesis, streamProperty);

    when(amazonKinesis.putRecords(any())).thenAnswer(invocation -> {
      while (true) {
        Thread.sleep(1);
      }
    });

    final ConsumerHelper<ResponseFailEntry> failureCallback = spy(new ConsumerHelper<>(result -> {
      assertThat(result, notNullValue());
    }));

    entries(2).forEach(entry -> {
      kinesisTemplate.send(entry).addCallback(null, failureCallback);;
    });

    verify(failureCallback, timeout(40000).times(1)).accept(any());
    verify(amazonKinesis, atLeastOnce()).putRecords(any());
  }

  private List<RequestEntry<Object>> entries(final int amount) {
    final LinkedList<RequestEntry<Object>> entries = new LinkedList<>();

    for (int i = 0; i < amount; i++) {
      entries.add(RequestEntry.builder()
        .withValue(RandomStringUtils.secure().nextAlphanumeric(36))
        .build());
    }

    return entries;
  }

}
// @formatter:on
