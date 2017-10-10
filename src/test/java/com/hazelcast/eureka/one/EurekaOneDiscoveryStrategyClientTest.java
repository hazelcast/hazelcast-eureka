/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        EurekaOneDiscoveryStrategyBuilder builder = new EurekaOneDiscoveryStrategyBuilder();
        builder.setEurekaClient(eurekaClient).setApplicationInfoManager(applicationInfoManager).setClientMode(true);
        strategy = builder.build();
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
