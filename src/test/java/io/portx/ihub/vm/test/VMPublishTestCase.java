/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.test;

import static java.time.LocalDateTime.now;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

import io.portx.ihub.vm.api.VMMessageAttributes;
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
import java.util.Map;

import org.junit.Test;

public class VMPublishTestCase extends VMTestCase {

  private static final String PUBLISH_TO_TRANSIENT_FLOW_NAME = "publishToTransient";

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
  public void publishStream() throws Exception {
    assertPublishStream(new ByteArrayInputStream(JSON_PAYLOAD.getBytes()));
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
  public void publishWithProperties() throws Exception {
    CoreEvent event = assertPublish("publishWithProperties", TRANSIENT_QUEUE_NAME);
    VMMessageAttributes attributes = (VMMessageAttributes) event.getMessage().getAttributes().getValue();
    assertNotNull(attributes);
    Map<String, TypedValue<Serializable>> properties = attributes.getProperties();
    assertNotNull(properties);
    assertTrue(properties.containsKey("prop1"));
    assertTrue(properties.containsKey("prop2"));
    Map prop1 = (Map) properties.get("prop1").getValue();
    assertEquals("Hello", prop1.get("salute"));
    assertEquals("World", properties.get("prop2").getValue());
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
    flowRunner("publishToPersistent")
        .withPayload(value.getValue())
        .withMediaType(value.getDataType().getMediaType())
        .run();

    CoreEvent event = getCapturedEvent();
    Message message = event.getMessage();
    TypedValue<byte[]> payload = message.getPayload();

    assertThat(new String(payload.getValue()), is(equalTo(JSON_PAYLOAD)));
    assertThat(payload.getDataType().getMediaType().matches(value.getDataType().getMediaType()), is(true));
    assertAttributes(message.getAttributes(), PERSISTENT_QUEUE_NAME, now);
  }

  private CoreEvent assertPublish(String flowName, String queueName) throws Exception {
    return assertPublish(flowName, queueName, null);
  }

  private CoreEvent assertPublish(String flowName, String queueName, String correlationId) throws Exception {
    TypedValue<String> value = new TypedValue<>("Hello", JSON_DATA_TYPE);
    LocalDateTime now = now();
    flowRunner(flowName)
        .withPayload(value.getValue())
        .withMediaType(value.getDataType().getMediaType())
        .withSourceCorrelationId(correlationId)
        .run();

    CoreEvent event = getCapturedEvent();
    Message message = event.getMessage();
    TypedValue payload = message.getPayload();

    assertThat(payload.getValue(), is(equalTo(value.getValue())));
    assertThat(payload.getDataType(), equalTo(value.getDataType()));
    assertAttributes(message.getAttributes(), queueName, now);

    return event;
  }
}
