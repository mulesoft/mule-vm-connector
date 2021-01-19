/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.api;

/**
 * Defines the types of queues
 *
 * @since 1.0
 */
public enum QueueType {

  /**
   * A queue which stores contents in local memory only. Fast but not reliable
   */
  TRANSIENT {

    @Override
    public boolean isPersistent() {
      return false;
    }
  },

  /**
   * A queue which stores contents into disk or distributed memory grid. Slower but reliable.
   */
  PERSISTENT {

    @Override
    public boolean isPersistent() {
      return true;
    }
  };

  public abstract boolean isPersistent();
}
