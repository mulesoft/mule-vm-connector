/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;
import static org.mule.runtime.api.metadata.DataType.JSON_STRING;
import static org.mule.runtime.api.metadata.MediaType.APPLICATION_JSON;
import static org.mule.tck.junit4.matcher.ErrorTypeMatcher.errorType;

import io.portx.ihub.vm.api.VMError;
import io.portx.ihub.vm.api.VMMessageAttributes;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.streaming.bytes.CursorStreamProviderFactory;
import org.mule.runtime.core.api.streaming.bytes.InMemoryCursorStreamConfig;
import org.mule.runtime.core.api.streaming.bytes.factory.InMemoryCursorStreamProviderFactory;
import org.mule.tck.core.streaming.SimpleByteBufferManager;
import org.mule.tck.junit4.matcher.ErrorTypeMatcher;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.Map;

import org.junit.Test;

public class VMPublishConsumeTestCase extends VMTestCase {

  @Override
  protected String getConfigFile() {
    return "vm-publish-consume-config.xml";
  }

  @Test
  public void publishConsume() throws Exception {
    assertPublishConsume("publishConsume");
  }

  @Test
  public void transactionalPublishConsume() throws Exception {
    assertPublishConsume("transactionalPublishConsume");
  }

  @Test
  public void publishConsumerWithDefaultCorrelationId() throws Exception {
    CoreEvent event = assertPublishConsume("publishConsume", MY_CORRELATION_ID);
    assertAttributesCorrelationId(event, MY_CORRELATION_ID);
  }

  private void assertAttributesCorrelationId(CoreEvent event, String correlationId) {
    VMMessageAttributes attributes = (VMMessageAttributes) event.getMessage().getAttributes().getValue();
    assertThat(attributes.getCorrelationId(), is(correlationId));
  }

  @Test
  public void publishConsumeWithCustomCorrelationId() throws Exception {
    CoreEvent event = assertPublishConsume("publishConsumeWithCustomCorrelationId");
    assertAttributesCorrelationId(event, MY_CORRELATION_ID);
    assertThat(event.getCorrelationId(), is(not(MY_CORRELATION_ID)));
  }

  @Test
  public void publishConsumeWithProperties() throws Exception {
    CoreEvent event = assertPublishConsume("publishConsumeWithProperties");
    VMMessageAttributes attributes = (VMMessageAttributes) event.getMessage().getAttributes().getValue();
    Map<String, TypedValue<Serializable>> properties = attributes.getProperties();
    assertNotNull(properties);
    assertTrue(properties.containsKey("prop1"));
    assertTrue(properties.containsKey("prop2"));
    Map prop1 = (Map) properties.get("prop1").getValue();
    assertEquals("Hello", prop1.get("salute"));
    assertEquals("World", properties.get("prop2").getValue());
  }

  @Test
  public void neverSendCorrelationId() throws Exception {
    CoreEvent event = assertPublishConsume("neverSendCorrelationId", MY_CORRELATION_ID);
    VMMessageAttributes attributes = (VMMessageAttributes) event.getMessage().getAttributes().getValue();
    assertThat(attributes.getCorrelationId(), is(not(MY_CORRELATION_ID)));
  }

  @Test
  public void publishConsumeStream() throws Exception {
    assertPublishConsume("publishConsume");
  }

  @Test
  public void publishConsumeRepeatableStream() throws Exception {
    CursorStreamProviderFactory providerFactory = new InMemoryCursorStreamProviderFactory(new SimpleByteBufferManager(),
                                                                                          InMemoryCursorStreamConfig.getDefault(),
                                                                                          streamingManager);

    TypedValue<byte[]> payload = flowRunner("publishConsume")
        .withPayload(providerFactory.of(testEvent(), new ByteArrayInputStream(JSON_PAYLOAD.getBytes())))
        .withMediaType(JSON_DATA_TYPE.getMediaType())
        .run().getMessage().getPayload();

    assertThat(new String(payload.getValue()), is(asJson(STRING_PAYLOAD.toLowerCase())));
    assertThat(payload.getDataType().getMediaType().matches(JSON_DATA_TYPE.getMediaType()), is(true));
  }

  @Test
  public void onErrorContinue() throws Exception {
    assertPublishConsume("onErrorContinue");
  }

  @Test
  public void onErrorPropagate() throws Exception {
    runAndExpect("onErrorPropagate", publishConsumerErrorTypeMatcher());
  }

  @Test
  public void publishToFailingQueue() throws Exception {
    runAndExpect("failingPublishConsume", publishConsumerErrorTypeMatcher());
  }

  private ErrorTypeMatcher publishConsumerErrorTypeMatcher() {
    return ErrorTypeMatcher.errorType("VM", VMError.PUBLISH_CONSUMER_FLOW_ERROR.name());
  }

  private CoreEvent assertPublishConsume(String flowName) throws Exception {
    return assertPublishConsume(flowName, null);
  }

  private CoreEvent assertPublishConsume(String flowName, String correlationId) throws Exception {
    CoreEvent event = flowRunner(flowName)
        .withPayload(new ByteArrayInputStream(JSON_PAYLOAD.getBytes()))
        .withMediaType(APPLICATION_JSON)
        .withSourceCorrelationId(correlationId)
        .run();

    TypedValue<byte[]> payload = event.getMessage().getPayload();

    assertThat(new String(payload.getValue()), is(asJson(STRING_PAYLOAD.toLowerCase())));
    assertThat(payload.getDataType().getMediaType().matches(JSON_STRING.getMediaType()), is(true));

    return event;
  }

  private String asJson(String value) {
    return "\"" + value + "\"";
  }
}
