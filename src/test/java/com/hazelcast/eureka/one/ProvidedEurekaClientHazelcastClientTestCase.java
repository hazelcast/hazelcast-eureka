/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved. Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */

package com.hazelcast.eureka.one;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.test.HazelcastTestSupport;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.junit.resource.SimpleEurekaHttpServerResource;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.SimpleEurekaHttpServer;

public class ProvidedEurekaClientHazelcastClientTestCase extends HazelcastTestSupport {

    private static final String APP_NAME = "hazelcast-test";

    @Rule
    public SimpleEurekaHttpServerResource resource = new SimpleEurekaHttpServerResource();

    private TestHazelcastFactory factory;
    private SimpleEurekaHttpServer server;
    private EurekaHttpClient requestHandler;

    @Before
    public void setup() {
        server = resource.getEurekaHttpServer();
        requestHandler = resource.getRequestHandler();
        factory = new TestHazelcastFactory();

        reset(requestHandler);

        configure(EurekaOneDiscoveryStrategy.DEFAULT_NAMESPACE, APP_NAME);
    }

    private void configure(String namespace, String appName) {
        URI serviceUri = server.getServiceURI();
        AbstractConfiguration configInstance = ConfigurationManager.getConfigInstance();
        configInstance.setProperty(namespace + ".serviceUrl.default", serviceUri.toString());
        configInstance.setProperty(namespace + ".port", serviceUri.getPort());
        configInstance.setProperty(namespace + ".name", appName);
    }

    @After
    public void tearDown() {
        if (null != server) {
            server.shutdown();
        }

        if (null != factory) {
            factory.shutdownAll();
        }
    }

    @Test
    public void testInstanceRegistrationUsingProvidedEurekaClient() {
        EurekaClient eurekaClient = eurekaClientMock();

        // use provided EurekaClient
        EurekaOneDiscoveryStrategyFactory.setEurekaClient(eurekaClient);

        HazelcastInstance hz1 = factory.newHazelcastInstance();
        HazelcastInstance hz2 = factory.newHazelcastInstance();

        assertClusterSizeEventually(2, hz1);
        assertClusterSizeEventually(2, hz2);
    }

    private EurekaClient eurekaClientMock() {
        EurekaClient eurekaClient = mock(EurekaClient.class);
        ApplicationInfoManager applicationInfoManager = mock(ApplicationInfoManager.class);
        EurekaInstanceConfig eurekaInstanceConfig = mock(EurekaInstanceConfig.class);

        when(eurekaClient.getApplicationInfoManager()).thenReturn(applicationInfoManager);
        when(eurekaClient.getApplication(anyString())).thenReturn(new Application(APP_NAME));

        when(applicationInfoManager.getEurekaInstanceConfig()).thenReturn(eurekaInstanceConfig);
        when(eurekaInstanceConfig.getAppname()).thenReturn(APP_NAME);

        return eurekaClient;
    }
}
