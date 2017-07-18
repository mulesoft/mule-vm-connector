package org.mule.extensions.vm.api;

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
