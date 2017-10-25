/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.extensions.vm.api.VMError.QUEUE_TIMEOUT;
import static org.mule.runtime.api.metadata.DataType.JSON_STRING;
import static org.mule.runtime.api.metadata.MediaType.APPLICATION_JSON;
import static org.mule.tck.junit4.matcher.ErrorTypeMatcher.errorType;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.streaming.bytes.CursorStreamProviderFactory;
import org.mule.runtime.core.api.streaming.bytes.InMemoryCursorStreamConfig;
import org.mule.runtime.core.api.streaming.bytes.factory.InMemoryCursorStreamProviderFactory;
import org.mule.tck.core.streaming.SimpleByteBufferManager;

import java.io.ByteArrayInputStream;

import org.junit.Test;

public class VMPublishConsumeTestCase extends VMTestCase {

  public static class FailureProcessor implements Processor {

    @Override
    public CoreEvent process(CoreEvent event) throws MuleException {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  protected String getConfigFile() {
    return "vm-publish-consume-config.xml";
  }

  @Test
  public void publishConsume() throws Exception {
    TypedValue<byte[]> payload = flowRunner("publishConsume")
        .withPayload(JSON_PAYLOAD)
        .withMediaType(APPLICATION_JSON)
        .run().getMessage().getPayload();

    assertThat(new String(payload.getValue()), is(asJson(STRING_PAYLOAD.toLowerCase())));
    assertThat(payload.getDataType().getMediaType().matches(JSON_STRING.getMediaType()), is(true));
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
        .withMediaType(APPLICATION_JSON)
        .run().getMessage().getPayload();

    assertThat(new String(payload.getValue()), is(asJson(STRING_PAYLOAD.toLowerCase())));
    assertThat(payload.getDataType().getMediaType().matches(JSON_STRING.getMediaType()), is(true));
  }

  @Test
  public void onErrorContinue() throws Exception {
    assertPublishConsume("onErrorContinue");
  }

  @Test
  public void onErrorPropagate() throws Exception {
    runAndExpect("onErrorPropagate", errorType(VM_ERROR_NAMESPACE, QUEUE_TIMEOUT.name()));
  }

  @Test
  public void publishToFailingQueue() throws Exception {
    runAndExpect("failingPublishConsume", errorType(VM_ERROR_NAMESPACE, QUEUE_TIMEOUT.name()));
  }

  private void assertPublishConsume(String flowName) throws Exception {
    TypedValue<byte[]> payload = flowRunner(flowName)
        .withPayload(new ByteArrayInputStream(JSON_PAYLOAD.getBytes()))
        .withMediaType(APPLICATION_JSON)
        .run().getMessage().getPayload();

    assertThat(new String(payload.getValue()), is(asJson(STRING_PAYLOAD.toLowerCase())));
    assertThat(payload.getDataType().getMediaType().matches(JSON_STRING.getMediaType()), is(true));
  }

  private String asJson(String value) {
    return "\"" + value + "\"";
  }
}
