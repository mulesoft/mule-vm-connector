/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.mule.runtime.api.value.ValueProviderService.VALUE_PROVIDER_SERVICE_KEY;
import static org.mule.tck.junit4.matcher.ValueMatcher.valueWithId;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.value.ValueProviderService;
import org.mule.runtime.api.value.ValueResult;

import javax.inject.Inject;
import javax.inject.Named;

import org.junit.Test;

public class QueueNamesValueProviderTestCase extends VMTestCase {

  @Inject
  @Named(VALUE_PROVIDER_SERVICE_KEY)
  private ValueProviderService service;

  @Override
  protected String[] getConfigFiles() {
    return new String[] {"vm-listener-value-providers-config.xml"};
  }

  @Override
  public boolean enableLazyInit() {
    return true;
  }

  @Override
  public boolean disableXmlValidations() {
    return true;
  }

  @Test
  public void getQueueNames() {
    ValueResult values =
        service.getValues(Location.builder().globalName("listener").addSourcePart().build(),
                          "queueName");
    assertThat(values.isSuccess(), is(true));
    assertThat(values.getValues(), hasItems(valueWithId("transientQueue"), valueWithId("persistentQueue")));
  }
}
