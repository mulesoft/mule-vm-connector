/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.test;

import static java.time.LocalDateTime.now;
import static java.util.Arrays.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.api.metadata.DataType.fromObject;
import static org.mule.runtime.api.metadata.DataType.fromType;

import com.google.common.base.Objects;
import io.qameta.allure.Description;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.streaming.CursorProvider;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.streaming.bytes.CursorStreamProviderFactory;
import org.mule.runtime.core.api.streaming.bytes.InMemoryCursorStreamConfig;
import org.mule.runtime.core.api.streaming.bytes.factory.InMemoryCursorStreamProviderFactory;
import org.mule.tck.core.streaming.SimpleByteBufferManager;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

import org.junit.Test;

public class VMPublishTestCase extends VMTestCase {

  private static final String PUBLISH_TO_TRANSIENT_FLOW_NAME = "publishToTransient";

  private static final TypedValue SIMPLE_VALUE = new TypedValue("Hello", JSON_DATA_TYPE);

  private static final TypedValue SERIALIZABLE_VALUE =
      new TypedValue(new SerializableDummy("dummy"), fromType(SerializableDummy.class));

  private static final TypedValue NON_SERIALIZABLE_VALUE =
      new TypedValue(new NonSerializableDummy("dummy"), fromType(NonSerializableDummy.class));


  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  public static class DelayProcessor implements Processor {

    @Override
    public CoreEvent process(CoreEvent event) {
      try {
        Thread.sleep(300);
      } catch (InterruptedException e) {

      }
      return event;
    }
  }

  @Override
  protected String[] getConfigFiles() {
    return new String[] {"vm-listener-config.xml", "vm-publish-config.xml", "vm-configs.xml"};
  }

  @Test
  public void publishToTransient() throws Exception {
    assertPublish(PUBLISH_TO_TRANSIENT_FLOW_NAME, TRANSIENT_QUEUE_NAME);
  }

  @Test
  public void publishToPersistent() throws Exception {
    assertPublish("publishToPersistent", PERSISTENT_QUEUE_NAME);
  }

  @Test
  @Description("Publish a serializable object into a transient queue")
  public void publishSerializableToTransient() throws Exception {
    assertPublish("publishToTransient", TRANSIENT_QUEUE_NAME, SERIALIZABLE_VALUE);
  }

  @Test
  @Description("Publish a list of serializable objects into a persistent queue")
  public void publishListOfSerializableToTransient() throws Exception {
    List<TypedValue> values = asList(SERIALIZABLE_VALUE, SERIALIZABLE_VALUE);
    assertPublish("publishToTransient", TRANSIENT_QUEUE_NAME, new TypedValue<>(values, fromObject(values)));
  }

  @Test
  @Description("Publish a serializable object into a persistent queue")
  public void publishSerializableToPersistent() throws Exception {
    assertPublish("publishToPersistent", PERSISTENT_QUEUE_NAME, SERIALIZABLE_VALUE);
  }

  @Test
  @Description("Publish a list of serializable objects into a persistent queue")
  public void publishListOfSerializableToPersistent() throws Exception {
    List<TypedValue> values = asList(SERIALIZABLE_VALUE, SERIALIZABLE_VALUE);
    assertPublish("publishToPersistent", PERSISTENT_QUEUE_NAME, new TypedValue<>(values, fromObject(values)));
  }

  @Test
  @Description("Publish a non serializable object into a transient queue")
  // This test fails because the published message is made with a toString() representation of the payload
  public void publishNonSerializableToTransient() throws Exception {
    assertPublish("publishToTransient", TRANSIENT_QUEUE_NAME, NON_SERIALIZABLE_VALUE);
  }

  @Test
  @Description("Publish a non serializable object into a persistent queue.  It shouldn't be allowed")
  // The current behaviour is to make a payload.toString() and publish it. Should it raise an exception?
  public void publishNonSerializableToPersistent() throws Exception {
    // expectedException.expect(SomeException.class);
    assertPublish("publishToPersistent", PERSISTENT_QUEUE_NAME, NON_SERIALIZABLE_VALUE);
  }

  @Test
  @Description("Publish a list of non serializable objects into a transient queue")
  public void publishNestedNonSerializableToTransient() throws Exception {
    List<NonSerializableDummy> values = asList(new NonSerializableDummy("A"), new NonSerializableDummy("b"));

    assertPublish("publishToTransient", TRANSIENT_QUEUE_NAME, new TypedValue<>(values, fromObject(values)));
  }

  @Test
  @Description("Publish a list of non serializable objects into a persistent queue")
  // If there are non serializable objects inside a list or any object graph, it fails trying to serialize the payload
  public void publishNestedNonSerializableToPersistent() throws Exception {
    // expectedException.expectCause(instanceOf(org.mule.runtime.api.serialization.SerializationException.class));
    // expectedException.expectMessage("Could not serialize object.");

    List<NonSerializableDummy> values = asList(new NonSerializableDummy("A"), new NonSerializableDummy("b"));

    publish("publishToPersistent", new TypedValue<>(values, fromObject(values)));
  }

  @Test
  public void publishStream() throws Exception {
    assertPublishStream(new ByteArrayInputStream(JSON_PAYLOAD.getBytes()));
  }

  @Test
  @Description("Publish a list of repeatable streams into a persistent queue")
  // If the repeatable stream is inside a list or any object graph, it fails trying to serialize the payload
  public void publishListOfRepeatableStream() throws Exception {

    // expectedException.expectCause(instanceOf(org.mule.runtime.api.serialization.SerializationException.class));
    // expectedException.expectMessage("Could not serialize object.");

    CursorStreamProviderFactory cursorStreamProviderFactory =
        new InMemoryCursorStreamProviderFactory(new SimpleByteBufferManager(),
                                                InMemoryCursorStreamConfig.getDefault(),
                                                streamingManager);

    List<CursorProvider> providers = asList(
                                            (CursorProvider) cursorStreamProviderFactory
                                                .of(testEvent(), new ByteArrayInputStream(JSON_PAYLOAD.getBytes())),
                                            (CursorProvider) cursorStreamProviderFactory
                                                .of(testEvent(), new ByteArrayInputStream(JSON_PAYLOAD.getBytes())));

    publish("publishToPersistent", new TypedValue<>(asList(providers), fromObject(providers)));
  }

  @Test
  public void publishRepeatableStream() throws Exception {
    CursorStreamProviderFactory cursorStreamProviderFactory = new InMemoryCursorStreamProviderFactory(
                                                                                                      new SimpleByteBufferManager(),
                                                                                                      InMemoryCursorStreamConfig
                                                                                                          .getDefault(),
                                                                                                      streamingManager);

    CursorProvider provider = (CursorProvider) cursorStreamProviderFactory.of(
                                                                              testEvent(),
                                                                              new ByteArrayInputStream(JSON_PAYLOAD.getBytes()));

    assertPublishStream(provider);
  }

  @Test
  public void publishWithDefaultCorrelationId() throws Exception {
    CoreEvent event = assertPublish(PUBLISH_TO_TRANSIENT_FLOW_NAME, TRANSIENT_QUEUE_NAME, MY_CORRELATION_ID);
    assertThat(event.getCorrelationId(), is(MY_CORRELATION_ID));
  }

  @Test
  public void publishWithCustomCorrelationId() throws Exception {
    CoreEvent event = assertPublish("publishWithCustomCorrelationId", TRANSIENT_QUEUE_NAME);
    assertThat(event.getCorrelationId(), is(MY_CORRELATION_ID));
  }

  @Test
  public void neverSendCorrelationId() throws Exception {
    CoreEvent event = assertPublish("neverSendCorrelationId", TRANSIENT_QUEUE_NAME);
    assertThat(event.getCorrelationId(), is(not(MY_CORRELATION_ID)));
  }

  private void assertPublishStream(Object content) throws Exception {
    TypedValue<?> value = new TypedValue<>(content, JSON_DATA_TYPE);
    LocalDateTime now = now();

    publish("publishToPersistent", value);

    CoreEvent event = getCapturedEvent();
    Message message = event.getMessage();
    TypedValue<byte[]> payload = message.getPayload();

    assertThat(new String(payload.getValue()), is(equalTo(JSON_PAYLOAD)));
    assertThat(payload.getDataType().getMediaType().matches(value.getDataType().getMediaType()), is(true));
    assertAttributes(message.getAttributes(), PERSISTENT_QUEUE_NAME, now);
  }

  private CoreEvent assertPublish(String flowName, String queueName) throws Exception {
    return assertPublish(flowName, queueName, SIMPLE_VALUE, null);
  }

  private CoreEvent assertPublish(String flowName, String queueName, String correlationId) throws Exception {
    return assertPublish(flowName, queueName, SIMPLE_VALUE, correlationId);
  }

  private CoreEvent assertPublish(String flowName, String queueName, TypedValue<?> value) throws Exception {
    return assertPublish(flowName, queueName, value, null);
  }

  private CoreEvent assertPublish(String flowName, String queueName, TypedValue<?> value, String correlationId) throws Exception {
    LocalDateTime now = now();
    publish(flowName, value, correlationId);

    CoreEvent event = getCapturedEvent();
    Message message = event.getMessage();
    TypedValue payload = message.getPayload();

    assertThat(payload.getValue(), is(equalTo(value.getValue())));
    assertThat(payload.getDataType(), equalTo(value.getDataType()));
    assertAttributes(message.getAttributes(), queueName, now);

    return event;
  }

  private CoreEvent publish(String flowName, TypedValue<?> value) throws Exception {
    return publish(flowName, value, null);
  }

  private CoreEvent publish(String flowName, TypedValue<?> value, String correlationId) throws Exception {
    return flowRunner(flowName)
        .withPayload(value.getValue())
        .withMediaType(value.getDataType().getMediaType())
        .withSourceCorrelationId(correlationId)
        .run();
  }


}


class SerializableDummy implements Serializable {

  private String name;

  public SerializableDummy(String name) {
    this.name = name;
  }

  public String getName() {
    return this.name;
  }

  @Override
  public String toString() {
    return "Dummy{" + "name='" + name + "'}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    SerializableDummy dummy = (SerializableDummy) o;
    return Objects.equal(name, dummy.name);
  }
}


class NonSerializableDummy {

  private String name;

  public NonSerializableDummy(String name) {
    this.name = name;
  }

  public String getName() {
    return this.name;
  }

  @Override
  public String toString() {
    return "Dummy{" + "name='" + name + "'}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    NonSerializableDummy dummy = (NonSerializableDummy) o;
    return Objects.equal(name, dummy.name);
  }
}
