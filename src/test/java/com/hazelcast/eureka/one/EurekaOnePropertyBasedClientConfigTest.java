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

import com.google.common.collect.Maps;
import com.hazelcast.eureka.one.EurekaOneDiscoveryStrategy.EurekaOneDiscoveryStrategyBuilder;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EurekaOnePropertyBasedClientConfigTest extends AbstractEurekaOneDiscoveryStrategyTest {

    @Mock
    private DiscoveryNode node;
    private Map<String, Comparable> properties;

    @Override
    protected void initializeStrategy() {
        EurekaOneDiscoveryStrategyBuilder builder = new EurekaOneDiscoveryStrategyBuilder();

        properties = Maps.newHashMap();
        properties.put("self-registration", Boolean.TRUE);
        properties.put("namespace", "hazelcast");
        properties.put("use-classpath-eureka-client-props", Boolean.FALSE);
        properties.put("name", "hazelcast-test");
        properties.put("vipAddress", "hazelcast-test-vip");
        properties.put("serviceUrl.default", "http://localhost:8080/eureka/v2/");

        builder.setEurekaClient(null)
                .setProperties(properties)
                .setApplicationInfoManager(applicationInfoManager)
                .setDiscoveryNode(node)
                .setStatusChangeStrategy(new DefaultUpdater());
        strategy = builder.build();
    }

    @Test
    public void checkPropertyBasedConfigurationWorking() {
        EurekaClient client = strategy.getEurekaClient();

        assertThat(client, notNullValue());
        assertThat(client.getEurekaClientConfig(), notNullValue());
        assertThat(client.getEurekaClientConfig(), instanceOf(PropertyBasedEurekaClientConfig.class));
        assertThat(client.getEurekaClientConfig().getEurekaServerServiceUrls("default").get(0),
                is(properties.get("serviceUrl.default")));
        assertThat(client.getApplicationInfoManager().getEurekaInstanceConfig().getAppname(),
                is(properties.get("name")));
    }
}