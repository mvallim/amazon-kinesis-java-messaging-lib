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

import static org.awaitility.Awaitility.await;
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
import java.util.concurrent.TimeUnit;
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

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResultEntry;

// @formatter:off
@SuppressWarnings("java:S6204")
@ExtendWith(MockitoExtension.class)
class AmazonSnsProducerTest {

  private AmazonKinesisTemplate<Object> kinesisTemplate;

  @Mock
  private KinesisClient amazonKinesis;

  @Captor
  private ArgumentCaptor<ResponseSuccessEntry> argumentCaptorSuccess;

  @Captor
  private ArgumentCaptor<ResponseFailEntry> argumentCaptorFailure;

  @BeforeEach
  void before() {
    final StreamProperty streamProperty = StreamProperty.builder()
      .linger(50L)
      .maxBatchSize(10)
      .streamArn("arn:aws:kinesis:us-east-2:000000000000:stream/mystream")
      .build();

    kinesisTemplate = new AmazonKinesisTemplate<>(amazonKinesis, streamProperty, new RingBufferBlockingQueue<>(1024));
  }

  @Test
  void testSuccess() {
    final String id = UUID.randomUUID().toString();

    final PutRecordsResultEntry publishBatchResultEntry = PutRecordsResultEntry.builder()
      .shardId("shard-1")
      .sequenceNumber("012345678901234567890")
      .build();

    final PutRecordsResponse putRecordsResponse = PutRecordsResponse.builder()
      .records(Collections.singleton(publishBatchResultEntry))
      .build();

    when(amazonKinesis.putRecords(any(PutRecordsRequest.class))).thenReturn(putRecordsResponse);

    kinesisTemplate.send(RequestEntry.builder().withId(id).build()).addCallback(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getShardId(), is("shard-1"));
      assertThat(result.getSequenceNumber(), is("012345678901234567890"));
    });

    kinesisTemplate.await().thenAccept(result -> {
      kinesisTemplate.shutdown();
      verify(amazonKinesis, timeout(10000).times(1)).putRecords(any(PutRecordsRequest.class));
    }).join();

  }

  @Test
  void testFailure() {
    final PutRecordsResultEntry publishBatchResultEntry = PutRecordsResultEntry.builder()
      .errorCode("1")
      .errorMessage("ErrorMessage")
      .build();

    final PutRecordsResponse putRecordsResponse = PutRecordsResponse.builder()
      .records(Collections.singleton(publishBatchResultEntry))
      .build();

    when(amazonKinesis.putRecords(any(PutRecordsRequest.class))).thenReturn(putRecordsResponse);

    kinesisTemplate.send(RequestEntry.builder().build()).addCallback(null, result -> {
      assertThat(result, notNullValue());
      assertThat(result.getCode(), is("1"));
      assertThat(result.getMessage(), is("ErrorMessage"));
    });

    kinesisTemplate.await().thenAccept(result -> {
      kinesisTemplate.shutdown();
      verify(amazonKinesis, timeout(10000).times(1)).putRecords(any(PutRecordsRequest.class));
    }).join();

  }

  @Test
  void testSuccessMultipleEntry() {

    when(amazonKinesis.putRecords(any(PutRecordsRequest.class))).thenAnswer(invocation -> {
      final PutRecordsRequest request = invocation.getArgument(0, PutRecordsRequest.class);
      final List<PutRecordsResultEntry> resultEntries = request.records().stream()
        .map(entry -> PutRecordsResultEntry.builder().sequenceNumber("1234567890").shardId("shard-1").build())
        .collect(Collectors.toList());
      return PutRecordsResponse.builder().records(resultEntries).build();
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
      verify(amazonKinesis, atLeastOnce()).putRecords(any(PutRecordsRequest.class));
    }).join();

    for (final ResponseSuccessEntry responseSuccessEntry : argumentCaptorSuccess.getAllValues()) {
      successCallback.accept(responseSuccessEntry);
    }
  }

  @Test
  void testFailureMultipleEntry() {

    when(amazonKinesis.putRecords(any(PutRecordsRequest.class))).thenAnswer(invocation -> {
      final PutRecordsRequest request = invocation.getArgument(0, PutRecordsRequest.class);
      final List<PutRecordsResultEntry> resultEntries = request.records().stream()
        .map(entry -> PutRecordsResultEntry.builder().errorCode("1").errorMessage("ErrorMessage").build())
        .collect(Collectors.toList());
      return PutRecordsResponse.builder().records(resultEntries).build();
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
      verify(amazonKinesis, atLeastOnce()).putRecords(any(PutRecordsRequest.class));
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

    when(amazonKinesis.putRecords(any(PutRecordsRequest.class))).thenThrow(AwsServiceException.builder().awsErrorDetails(AwsErrorDetails.builder().build()).build());

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
  }

  @Test
  void testSuccessBlockingSubmissionPolicy() {
    final StreamProperty streamProperty = StreamProperty.builder()
        .linger(50L)
        .maxBatchSize(1)
        .streamArn("arn:aws:kinesis:us-east-2:000000000000:stream/mystream")
        .build();

    kinesisTemplate = new AmazonKinesisTemplate<>(amazonKinesis, streamProperty);

    when(amazonKinesis.putRecords(any(PutRecordsRequest.class))).thenAnswer(invocation -> {
      while (true) {
        await().pollDelay(1, TimeUnit.MILLISECONDS).until(() -> true);
      }
    });

    final ConsumerHelper<ResponseFailEntry> failureCallback = spy(new ConsumerHelper<>(result -> {
      assertThat(result, notNullValue());
    }));

    entries(2).forEach(entry -> {
      kinesisTemplate.send(entry).addCallback(null, failureCallback);
    });

    verify(failureCallback, timeout(40000).times(1)).accept(any());
    verify(amazonKinesis, atLeastOnce()).putRecords(any(PutRecordsRequest.class));
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
