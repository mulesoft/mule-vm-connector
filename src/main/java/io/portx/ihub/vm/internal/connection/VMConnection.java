/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.internal.connection;

import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.runtime.api.tx.TransactionException;
import org.mule.runtime.core.api.util.queue.Queue;
import org.mule.runtime.core.api.util.queue.QueueSession;
import org.mule.runtime.extension.api.connectivity.XATransactionalConnection;

import javax.transaction.xa.XAResource;

import org.slf4j.Logger;

/**
 * A {@link XATransactionalConnection} which hides the details of Mule's queueing API.
 *
 * @since 1.0
 */
public class VMConnection implements XATransactionalConnection {

  private static final Logger LOGGER = getLogger(VMConnection.class);

  private final QueueSession queueSession;
  private boolean txBegun = false;

  public VMConnection(QueueSession queueSession) {
    this.queueSession = queueSession;
  }

  public Queue getQueue(String queueName) {
    return queueSession.getQueue(queueName);
  }

  @Override
  public void begin() throws TransactionException {
    try {
      queueSession.begin();
      txBegun = true;
    } catch (Exception e) {
      throw new TransactionException(createStaticMessage("Could not start transaction: " + e.getMessage()), e);
    }
  }

  @Override
  public void commit() throws TransactionException {
    if (!txBegun) {
      return;
    }

    try {
      queueSession.commit();
      txBegun = false;
    } catch (Exception e) {
      throw new TransactionException(createStaticMessage("Could not commit transaction: " + e.getMessage()), e);
    }
  }

  @Override
  public void rollback() throws TransactionException {
    if (!txBegun) {
      return;
    }

    try {
      queueSession.rollback();
      txBegun = false;
    } catch (Exception e) {
      throw new TransactionException(createStaticMessage("Could not rollback transaction: " + e.getMessage()), e);
    }
  }

  @Override
  public XAResource getXAResource() {
    return queueSession;
  }

  @Override
  public void close() {
    if (txBegun) {
      try {
        rollback();
      } catch (Exception e) {
        if (LOGGER.isWarnEnabled()) {
          LOGGER.warn("Found exception while rolling back transaction due to connection close: " + e.getMessage(), e);
        }
      }
    }
  }

  public boolean isInTransaction() {
    return txBegun;
  }
}
