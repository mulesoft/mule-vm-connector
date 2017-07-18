/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.internal.connection;

import org.mule.runtime.core.api.util.queue.Queue;
import org.mule.runtime.core.api.util.queue.QueueManager;
import org.mule.runtime.core.api.util.queue.QueueSession;
import org.mule.runtime.extension.api.connectivity.XATransactionalConnection;

import javax.transaction.xa.XAResource;

/**
 * A {@link XATransactionalConnection} which hides the details of Mule's queueing API.
 *
 * @since 1.0
 */
public class VMConnection implements XATransactionalConnection {

  private final QueueManager queueManager;
  private ThreadLocal<QueueSession> queueSession;


  public VMConnection(QueueManager queueManager) {
    this.queueManager = queueManager;
    queueSession = new ThreadLocal<>();
  }

  public Queue getQueue(String queueName) {
    return getQueueSession().getQueue(queueName);
  }

  @Override
  public void begin() throws Exception {
    getQueueSession().begin();
  }

  @Override
  public void commit() throws Exception {
    getQueueSession().commit();
  }

  @Override
  public void rollback() throws Exception {
    getQueueSession().rollback();
  }

  @Override
  public XAResource getXAResource() {
    return getQueueSession();
  }

  @Override
  public void close() {
    queueSession = null;
  }

  //TODO: MULE-13102 - this should go away and the session be part of the connection state
  private QueueSession getQueueSession() {
    synchronized (queueSession) {
      QueueSession session = queueSession.get();
      if (session == null) {
        session = queueManager.getQueueSession();
        queueSession.set(session);
      }

      return session;
    }
  }
}
