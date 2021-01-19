/*
/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.test;

import static java.time.LocalDateTime.now;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.Mockito.mock;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.util.queue.Queue;
import org.mule.tck.probe.JUnitProbe;
import org.mule.tck.probe.PollingProber;
import org.mule.tck.testmodels.fruit.Apple;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class VMListenerTestCase extends VMTestCase {

  @Rule
  public ExpectedException expectedException = none();

  @Override
  protected String[] getConfigFiles() {
    return new String[] {"vm-listener-config.xml", "vm-configs.xml"};
  }

  @Test
  public void listenOnTransientQueue() throws Exception {
    Queue queue = getTransientQueue();

    Serializable sentValue = new Apple();
    LocalDateTime now = now();
    queue.offer(sentValue, TIMEOUT);

    CoreEvent event = getCapturedEvent();
    Message message = event.getMessage();
    TypedValue payload = message.getPayload();

    assertThat(payload.getValue(), is(sameInstance(sentValue)));
    assertThat(payload.getDataType().getType(), equalTo(Apple.class));
    assertAttributes(message.getAttributes(), TRANSIENT_QUEUE_NAME, now);
  }

  @Test
  public void listenOneAtATime() throws Exception {
    final int messageCount = 3;

    Serializable sentValue = new Apple();
    for (int i = 0; i < messageCount; i++) {
      flowRunner("publishToSyncQueue").withPayload(sentValue).run();
    }

    Long priorMessageTime = null;

    for (int i = 0; i < messageCount; i++) {
      ZonedDateTime messageTimestamp = (ZonedDateTime) getCapturedEvent(15000).getMessage().getPayload().getValue();
      final long timestamp = messageTimestamp.toInstant().toEpochMilli();
      if (priorMessageTime != null) {
        long diff = timestamp - priorMessageTime;
        assertThat(diff, is(greaterThanOrEqualTo(1000L)));
      }

      priorMessageTime = timestamp;
    }
  }

  @Test
  public void listenOnPersistentQueue() throws Exception {
    Queue queue = getPersistentQueue();

    Apple apple = new Apple();
    apple.bite();
    LocalDateTime now = now();
    queue.offer(apple, TIMEOUT);

    CoreEvent event = getCapturedEvent();
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
    TypedValue<String> value = new TypedValue<>(payloadValue, JSON_DATA_TYPE);
    LocalDateTime now = now();
    queue.offer(value, TIMEOUT);

    CoreEvent event = getCapturedEvent();
    Message message = event.getMessage();
    TypedValue payload = message.getPayload();

    assertThat(payload.getValue(), is(sameInstance(payloadValue)));
    assertThat(payload.getDataType(), equalTo(JSON_DATA_TYPE));
    assertAttributes(message.getAttributes(), TRANSIENT_QUEUE_NAME, now);
  }

  @Test
  public void consumersStopped() throws Exception {
    registry.<Stoppable>lookupByName("transientListener").get().stop();

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

  @Test
  public void listenerRegistered() throws Exception {
    expectedException.expect(IllegalArgumentException.class);
    vmQueueManager.validateNoListenerOnQueue(TRANSIENT_QUEUE_NAME, "test", mock(ComponentLocation.class));
  }

  @Test
  public void listenerUnregistersWhenStopped() throws Exception {
    ((Stoppable) getFlowConstruct("transientListener")).stop();
    vmQueueManager.validateNoListenerOnQueue(TRANSIENT_QUEUE_NAME, "test", mock(ComponentLocation.class));
  }
}
