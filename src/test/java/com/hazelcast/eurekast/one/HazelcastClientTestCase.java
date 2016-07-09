/*
 * Copyright (c) 2008-2015, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.eurekast.one;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.shared.Application;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.Rule;
import org.junit.Test;

import java.util.UUID;

public class HazelcastClientTestCase
        extends HazelcastTestSupport {

    @Rule
    public MockRemoteEurekaServer serverResource = new MockRemoteEurekaServer();

    @Test
    public void testSimpleDiscovery()
            throws Exception {

        AbstractConfiguration configInstance = ConfigurationManager.getConfigInstance();

        String serviceUrl = "http://localhost:" + serverResource.getPort() + MockRemoteEurekaServer.EUREKA_API_BASE_PATH;
        configInstance.setProperty("eureka.serviceUrl.defaultZone", serviceUrl);
        configInstance.setProperty("eureka.name", "hazelcast-test");

        Application application = new Application();
        application.setName("hazelcast-test");
        application.addInstance(buildInstanceInfo(5701));
        application.addInstance(buildInstanceInfo(5702));
        serverResource.addLocalRegionApps("hazelcast-test", application);

        TestHazelcastFactory factory = new TestHazelcastFactory();
        try {
            HazelcastInstance hz1 = factory.newHazelcastInstance();
            HazelcastInstance hz2 = factory.newHazelcastInstance();

            assertClusterSizeEventually(2, hz1);
            assertClusterSizeEventually(2, hz2);

            HazelcastInstance client = factory.newHazelcastClient();
            assertClusterSizeEventually(2, client);

        } finally {
            factory.shutdownAll();
        }
    }

    private InstanceInfo buildInstanceInfo(int port) {
        InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();
        return builder.setAppName("hazelcast-test").setIPAddr("localhost").setPort(port).setVIPAddress("hazelcast-test")
                      .setStatus(InstanceInfo.InstanceStatus.UP).setInstanceId(UUID.randomUUID().toString())
                      .setHostName("localhost").build();
    }

}
