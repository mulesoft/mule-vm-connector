/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_QUEUE_MANAGER;

import org.mule.extensions.vm.api.VMMessageAttributes;
import org.mule.extensions.vm.internal.VMConnectorQueueManager;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.event.BaseEvent;
import org.mule.runtime.core.api.exception.MessagingException;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.streaming.StreamingManager;
import org.mule.runtime.core.api.util.queue.Queue;
import org.mule.runtime.core.api.util.queue.QueueManager;
import org.mule.runtime.core.api.util.queue.QueueSession;
import org.mule.tck.junit4.matcher.ErrorTypeMatcher;
import org.mule.tck.probe.JUnitLambdaProbe;
import org.mule.tck.probe.PollingProber;
import org.mule.test.runner.ArtifactClassLoaderRunnerConfig;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Named;

@ArtifactClassLoaderRunnerConfig(exportPluginClasses = VMConnectorQueueManager.class)
public abstract class VMTestCase extends MuleArtifactFunctionalTestCase {

  protected static final String STRING_PAYLOAD = "Hello";
  protected static final String JSON_PAYLOAD = "{\"salute\": \"" + STRING_PAYLOAD + "\"}";
  protected static final String TRANSIENT_QUEUE_NAME = "transientQueue";
  protected static final String PERSISTENT_QUEUE_NAME = "persistentQueue";
  protected static final java.util.Queue<BaseEvent> CAPTURED = new ConcurrentLinkedDeque<>();
  protected static final String VM_ERROR_NAMESPACE = "VM";
  protected static final long TIMEOUT = 5000;

  @Override
  protected boolean doTestClassInjection() {
    return true;
  }

  public static class EventCaptor implements Processor {

    @Override
    public BaseEvent process(BaseEvent event) throws MuleException {
      CAPTURED.add(event);
      synchronized (CAPTURED) {
        CAPTURED.notifyAll();
      }

      return event;
    }
  }

  @Inject
  protected VMConnectorQueueManager vmQueueManager;

  @Inject
  @Named(OBJECT_QUEUE_MANAGER)
  protected QueueManager queueManager;

  @Inject
  protected StreamingManager streamingManager;

  @Override
  protected void doTearDown() throws Exception {
    CAPTURED.clear();
  }

  protected Queue getTransientQueue() {
    return getQueue(TRANSIENT_QUEUE_NAME);
  }

  protected Queue getPersistentQueue() {
    return getQueue(PERSISTENT_QUEUE_NAME);
  }

  protected BaseEvent getCapturedEvent() {
    AtomicReference<BaseEvent> value = new AtomicReference<>();
    new PollingProber(TIMEOUT, 100).check(new JUnitLambdaProbe(() -> {
      BaseEvent capturedEvent = CAPTURED.poll();
      if (capturedEvent != null) {
        value.set(capturedEvent);
        return true;
      }

      return false;
    }));

    return value.get();
  }

  protected Queue getQueue(String name) {
    final QueueSession queueSession = queueManager.getQueueSession();
    Queue queue = queueSession.getQueue(name);

    assertThat(queue, is(notNullValue()));
    return queue;
  }

  protected void assertAttributes(TypedValue typedAttributes, String queueName, LocalDateTime now) {
    assertThat(typedAttributes.getValue(), is(instanceOf(VMMessageAttributes.class)));
    VMMessageAttributes attributes = (VMMessageAttributes) typedAttributes.getValue();

    assertThat(attributes.getQueueName(), is(queueName));
    assertThat(attributes.getTimestamp().compareTo(now), is(greaterThanOrEqualTo(0)));
  }

  protected void runAndExpect(String flowName, ErrorTypeMatcher matcher) throws Exception {
    try {
      flowRunner(flowName).withPayload("Hello").run();
      fail("Was expecting a failure");
    } catch (MessagingException e) {
      Error error = e.getEvent().getError().get();
      assertThat(error.getErrorType(), is(matcher));
    }
  }
}
