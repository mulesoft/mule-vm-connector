/*
/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.test;

import static java.time.LocalDateTime.now;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.api.metadata.DataType.JSON_STRING;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.construct.Flow;
import org.mule.runtime.core.api.event.BaseEvent;
import org.mule.runtime.core.api.util.queue.Queue;
import org.mule.tck.probe.JUnitProbe;
import org.mule.tck.probe.PollingProber;
import org.mule.tck.testmodels.fruit.Apple;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class VMListenerTestCase extends VMTestCase {

  @Override
  protected String getConfigFile() {
    return "vm-listener-config.xml";
  }

  @Test
  public void listenOnTransientQueue() throws Exception {
    Queue queue = getTransientQueue();

    Serializable sentValue = new Apple();
    LocalDateTime now = now();
    queue.offer(sentValue, TIMEOUT);

    BaseEvent event = getCapturedEvent();
    Message message = event.getMessage();
    TypedValue payload = message.getPayload();

    assertThat(payload.getValue(), is(sameInstance(sentValue)));
    assertThat(payload.getDataType().getType(), equalTo(Apple.class));
    assertAttributes(message.getAttributes(), TRANSIENT_QUEUE_NAME, now);
  }

  @Test
  public void listenOnPersistentQueue() throws Exception {
    Queue queue = getPersistentQueue();

    Apple apple = new Apple();
    apple.bite();
    LocalDateTime now = now();
    queue.offer(apple, TIMEOUT);

    BaseEvent event = getCapturedEvent();
    Message message = event.getMessage();
    TypedValue payload = message.getPayload();

    assertThat(payload.getDataType().getType(), equalTo(Apple.class));
    Apple queueApple = (Apple) payload.getValue();
    assertThat(queueApple, is(not(sameInstance(apple))));
    assertThat(queueApple.isBitten(), is(true));
    assertAttributes(message.getAttributes(), PERSISTENT_QUEUE_NAME, now);
  }

  @Test
  public void listenTypedValue() throws Exception {
    Queue queue = getTransientQueue();

    final String payloadValue = "Hello";
    TypedValue<String> value = new TypedValue<>(payloadValue, JSON_STRING);
    LocalDateTime now = now();
    queue.offer(value, TIMEOUT);

    BaseEvent event = getCapturedEvent();
    Message message = event.getMessage();
    TypedValue payload = message.getPayload();

    assertThat(payload.getValue(), is(sameInstance(payloadValue)));
    assertThat(payload.getDataType(), equalTo(JSON_STRING));
    assertAttributes(message.getAttributes(), TRANSIENT_QUEUE_NAME, now);
  }

  @Test
  public void consumersStopped() throws Exception {
    Flow flow = (Flow) muleContext.getRegistry().lookupFlowConstruct("transientListener");
    flow.stop();

    Queue queue = getTransientQueue();
    AtomicInteger capturedPayloadsCount = new AtomicInteger(CAPTURED.size());

    new PollingProber(SECONDS.toMillis(10), 100).check(new JUnitProbe() {

      @Override
      protected boolean test() throws Exception {
        Serializable sentValue = new Apple();
        queue.offer(sentValue, TIMEOUT);

        synchronized (CAPTURED) {
          CAPTURED.wait(TIMEOUT);
        }

        int updatedSize = CAPTURED.size();
        if (updatedSize == capturedPayloadsCount.get()) {
          return true;
        } else {
          capturedPayloadsCount.set(updatedSize);
          return false;
        }
      }
    });
  }
}
