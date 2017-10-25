/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.test;

import static java.time.LocalDateTime.now;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.api.metadata.DataType.JSON_STRING;
import org.mule.runtime.api.exception.MuleException;
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
import java.time.LocalDateTime;

import org.junit.Test;

public class VMPublishTestCase extends VMTestCase {

  public static class DelayProcessor implements Processor {

    @Override
    public CoreEvent process(CoreEvent event) throws MuleException {
      try {
        Thread.sleep(300);
      } catch (InterruptedException e) {

      }
      return event;
    }
  }

  @Override
  protected String[] getConfigFiles() {
    return new String[] {"vm-listener-config.xml", "vm-publish-config.xml"};
  }

  @Test
  public void publishToTransient() throws Exception {
    assertPublish("publishToTransient", TRANSIENT_QUEUE_NAME);
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

  private void assertPublishStream(Object content) throws Exception {
    TypedValue<?> value = new TypedValue<>(content, JSON_STRING);
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

  private void assertPublish(String flowName, String queueName) throws Exception {
    TypedValue<String> value = new TypedValue<>("Hello", JSON_STRING);
    LocalDateTime now = now();
    flowRunner(flowName)
        .withPayload(value.getValue())
        .withMediaType(value.getDataType().getMediaType())
        .run();

    CoreEvent event = getCapturedEvent();
    Message message = event.getMessage();
    TypedValue payload = message.getPayload();

    assertThat(payload.getValue(), is(equalTo(value.getValue())));
    assertThat(payload.getDataType(), equalTo(value.getDataType()));
    assertAttributes(message.getAttributes(), queueName, now);
  }
}
