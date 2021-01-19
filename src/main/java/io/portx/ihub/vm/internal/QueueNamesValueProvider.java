/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.internal;

import static java.util.stream.Collectors.toList;
import static org.mule.runtime.extension.api.values.ValueBuilder.getValuesFor;

import io.portx.ihub.vm.api.QueueDefinition;
import org.mule.runtime.api.value.Value;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.values.ValueProvider;
import org.mule.runtime.extension.api.values.ValueResolvingException;

import java.util.Set;

/**
 * A {@link ValueProvider} which returns the names of the queues defined in a {@link VMConnector}
 *
 * @since 1.0
 */
public class QueueNamesValueProvider implements ValueProvider {

  @Config
  private VMConnector connector;

  @Override
  public Set<Value> resolve() throws ValueResolvingException {
    return getValuesFor(connector.getQueueDefinitions().stream()
        .map(QueueDefinition::getQueueName)
        .collect(toList()));
  }
}
