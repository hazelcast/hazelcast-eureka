/*
 * Copyright (c) 2008-2020, Hazelcast, Inc. All Rights Reserved.
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


import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.discovery.EurekaClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Answers;
import org.mockito.Mock;

import static org.mockito.Mockito.when;

public abstract class AbstractEurekaOneDiscoveryStrategyTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    EurekaClient eurekaClient;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ApplicationInfoManager applicationInfoManager;

    EurekaOneDiscoveryStrategy strategy;

    final String APPLICATION_NAME = "hazelcast-test";

    @Before
    public void setup() {
        when(applicationInfoManager.getInfo().getAppName()).thenReturn(APPLICATION_NAME);
        when(applicationInfoManager.getEurekaInstanceConfig().getAppname()).thenReturn(APPLICATION_NAME);

        initializeStrategy();
    }

    protected abstract void initializeStrategy();
}
