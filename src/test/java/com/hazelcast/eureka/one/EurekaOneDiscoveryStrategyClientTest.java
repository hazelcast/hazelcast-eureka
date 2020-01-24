/*
 * Copyright 2020 Hazelcast Inc.
 *
 * Licensed under the Hazelcast Community License (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://hazelcast.com/hazelcast-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.hazelcast.eureka.one;


import com.hazelcast.eureka.one.EurekaOneDiscoveryStrategy.EurekaOneDiscoveryStrategyBuilder;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EurekaOneDiscoveryStrategyClientTest extends AbstractEurekaOneDiscoveryStrategyTest {

    @Override
    protected void initializeStrategy() {
        strategy = new EurekaOneDiscoveryStrategyBuilder()
                .setEurekaClient(eurekaClient)
                .setApplicationInfoManager(applicationInfoManager)
                .build();
    }

    @Test
    public void shouldNotRegisterWhenStarted(){
        when(eurekaClient.getApplication(APPLICATION_NAME))
                .thenReturn(new Application());

        strategy.start();

        verify(applicationInfoManager, never()).setInstanceStatus(any(InstanceInfo.InstanceStatus.class));
        verify(eurekaClient).getApplication(APPLICATION_NAME);
    }

    @Test
    public void shouldNotDeregisterWhenDestroyed(){
        when(eurekaClient.getApplication(APPLICATION_NAME))
                .thenReturn(new Application());

        strategy.destroy();

        verify(applicationInfoManager, never()).setInstanceStatus(any(InstanceInfo.InstanceStatus.class));
        verify(eurekaClient).shutdown();
    }
}
