package org.mule.extensions.vm.internal;

/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */


import org.mule.extensions.vm.api.VMError;
import org.mule.extensions.vm.internal.connection.VMConnectionProvider;
import org.mule.extensions.vm.internal.listener.VMListener;
import org.mule.extensions.vm.internal.operations.VMOperations;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.Sources;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;
import org.mule.runtime.extension.api.annotation.error.ErrorTypes;

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
 * @since 1.0
 */
//TODO: EE-5614 - migrate clustering tests
@Extension(name = "VM")
@Xml(prefix = "vm")
@Sources(VMListener.class)
@Operations(VMOperations.class)
@ConnectionProviders(VMConnectionProvider.class)
@ErrorTypes(VMError.class)
public class VMConnector {

}
