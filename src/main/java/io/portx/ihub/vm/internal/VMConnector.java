package io.portx.ihub.vm.internal;

/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */


import static org.mule.runtime.api.meta.ExpressionSupport.NOT_SUPPORTED;
import static org.mule.runtime.extension.api.runtime.parameter.OutboundCorrelationStrategy.AUTO;

import io.portx.ihub.vm.api.QueueDefinition;
import io.portx.ihub.vm.api.VMError;
import io.portx.ihub.vm.internal.connection.VMConnectionProvider;
import io.portx.ihub.vm.internal.listener.VMListener;
import io.portx.ihub.vm.internal.operations.VMOperations;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.Sources;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;
import org.mule.runtime.extension.api.annotation.error.ErrorTypes;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.RefName;
import org.mule.runtime.extension.api.runtime.parameter.OutboundCorrelationStrategy;

import javax.inject.Inject;

import java.util.List;

/**
 * The VM Connector is used for intra/inter app communication. The communication is done through asynchronous queues, which can
 * be either transient or persistent.
 *
 * Transient queues are faster, but not reliable in the case of a system crash. Persistent queues,
 * on the other hand are slower but reliable.
 *
 * When running on a single instance, persistent queues work by serializing and storing the contents into disk. When running
 * in cluster mode, persistent queues are backed by the memory grid instead. This means that when a flow uses the VM connector
 * to publish content to a queue, the Runtime will decide whether to process that message in the same origin node or to send it
 * out to the cluster for another node to pick it up. This is an easy way to distribute load across the cluster.
 *
 * In either way, transactions are always supported.
 *
 * Each config defines its own set of queues. Those queues are only visible to components referencing that config.
 *
 * @since 1.0
 */
//TODO: EE-5614 - migrate clustering tests
@Extension(name = "VM")
@Xml(prefix = "vm")
@Sources(VMListener.class)
@Operations(VMOperations.class)
@ConnectionProviders(VMConnectionProvider.class)
@ErrorTypes(VMError.class)
public class VMConnector implements Startable, Stoppable {

  @Inject
  private VMConnectorQueueManager queueManager;

  @RefName
  private String name;

  /**
   * The queues that this config owns
   */
  @Parameter
  @Expression(NOT_SUPPORTED)
  @Alias("queues")
  private List<QueueDefinition> queueDefinitions;

  /**
   * Whether to specify a correlationId when publishing messages. This applies both for custom correlation ids specifies at the
   * operation level and for default correlation Ids taken from the current event
   */
  @Parameter
  @Optional(defaultValue = "AUTO")
  private OutboundCorrelationStrategy sendCorrelationId = AUTO;

  @Override
  public void start() throws MuleException {
    if (queueDefinitions == null || queueDefinitions.isEmpty()) {
      throw new IllegalArgumentException("No queues were defined for <vm:config> " + name);
    }

    queueManager.createQueues(this, queueDefinitions);
  }

  @Override
  public void stop() throws MuleException {
    queueManager.unregisterQueues(this);
  }

  public String getName() {
    return name;
  }

  public List<QueueDefinition> getQueueDefinitions() {
    return queueDefinitions;
  }
}
