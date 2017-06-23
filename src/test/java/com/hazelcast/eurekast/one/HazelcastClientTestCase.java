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

package com.hazelcast.eurekast.one;

import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.Config;
import com.hazelcast.config.DiscoveryConfig;
import com.hazelcast.config.DiscoveryStrategyConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.test.HazelcastTestSupport;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.junit.resource.SimpleEurekaHttpServerResource;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.SimpleEurekaHttpServer;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;

import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.util.Collections;
import java.util.List;

import static com.netflix.discovery.shared.transport.EurekaHttpResponse.anEurekaHttpResponse;
import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class HazelcastClientTestCase extends HazelcastTestSupport {

    private static final String APP_NAME = "hazelcast-test";
    private static final String VIP_ADDRESS = "hazelcast-test-vip";

    @Rule
    public SimpleEurekaHttpServerResource resource = new SimpleEurekaHttpServerResource();

    private TestHazelcastFactory factory;
    private SimpleEurekaHttpServer server;
    private EurekaHttpClient requestHandler;

    @Before
    public void setup(){
        server = resource.getEurekaHttpServer();
        requestHandler = resource.getRequestHandler();
        factory = new TestHazelcastFactory();

        reset(requestHandler);

        URI serviceUri = server.getServiceURI();

        AbstractConfiguration configInstance = ConfigurationManager.getConfigInstance();
        configInstance.setProperty("eureka.serviceUrl.default", serviceUri.toString());
        configInstance.setProperty("eureka.port", serviceUri.getPort());
        configInstance.setProperty("eureka.name", APP_NAME);
        configInstance.setProperty("eureka.vipAddress", VIP_ADDRESS);
    }

    @After
    public void tearDown(){
        if(null != server){
            server.shutdown();
        }

        if(null != factory){
            factory.shutdownAll();
        }
    }

    @Test
    public void testInstanceRegistration(){

        EurekaHttpResponse<Applications> response = generateMockResponse(Collections.<InstanceInfo>emptyList());
        when(requestHandler.getApplications()).thenReturn(response);

        factory.newHazelcastInstance();

        ArgumentCaptor<InstanceInfo> captor = ArgumentCaptor.forClass(InstanceInfo.class);
        verify(requestHandler, timeout(5000).times(1)).register(captor.capture());

        assertNotNull(captor.getAllValues());
        assertEquals(captor.getAllValues().size(), 1);

        InstanceInfo actual = captor.getValue();
        assertEquals(actual.getAppName().toLowerCase(), APP_NAME);

    }

    @Test
    public void testSimpleDiscovery(){
        ArgumentCaptor<InstanceInfo> captor = ArgumentCaptor.forClass(InstanceInfo.class);

        EurekaHttpResponse<Applications> response = generateMockResponse(Collections.<InstanceInfo>emptyList());
        when(requestHandler.getApplications()).thenReturn(response);

        HazelcastInstance hz1 = factory.newHazelcastInstance();
        HazelcastInstance hz2 = factory.newHazelcastInstance();

        verify(requestHandler, timeout(5000).times(2)).register(captor.capture());
        response = generateMockResponse(captor.getAllValues());
        when(requestHandler.getApplications()).thenReturn(response);

        assertClusterSizeEventually(2, hz1);
        assertClusterSizeEventually(2, hz2);

        HazelcastInstance client = factory.newHazelcastClient();
        reset(requestHandler);
        when(requestHandler.getApplications()).thenReturn(response);
        verify(requestHandler, timeout(1000).times(0)).register(Matchers.<InstanceInfo>any());
        assertClusterSizeEventually(2, client);
    }

    @Test
    public void testInstanceDown(){
        ArgumentCaptor<InstanceInfo> captor = ArgumentCaptor.forClass(InstanceInfo.class);

        EurekaHttpResponse<Applications> response = generateMockResponse(Collections.<InstanceInfo>emptyList());
        when(requestHandler.getApplications()).thenReturn(response);

        HazelcastInstance hz1 = factory.newHazelcastInstance();
        HazelcastInstance hz2 = factory.newHazelcastInstance();

        verify(requestHandler, timeout(5000).times(2)).register(captor.capture());
        response = generateMockResponse(captor.getAllValues());
        when(requestHandler.getApplications()).thenReturn(response);

        hz1.shutdown();

        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        verify(requestHandler, timeout(5000).times(1)).cancel(anyString(), id.capture());

        reset(requestHandler);
        for(InstanceInfo info : captor.getAllValues()){
            if(info.getId().equals(id.getValue())){
                captor.getAllValues().remove(info);
                break;
            }
        }

        response = generateMockResponse(captor.getAllValues());
        when(requestHandler.getApplications()).thenReturn(response);

        assertClusterSizeEventually(1, hz2);
    }


    @Test
    public void testExternalRegistration(){
        EurekaHttpResponse<Applications> response = generateMockResponse(Collections.<InstanceInfo>emptyList());
        when(requestHandler.getApplications()).thenReturn(response);

        Config config = new XmlConfigBuilder().build();
        DiscoveryConfig discoveryConfig = config.getNetworkConfig().getJoin().getDiscoveryConfig();
        DiscoveryStrategyConfig strategyConfig = discoveryConfig.getDiscoveryStrategyConfigs().iterator().next();
        strategyConfig.addProperty("self-registration", "false");

        HazelcastInstance hz1 = factory.newHazelcastInstance(config);
        HazelcastInstance hz2 = factory.newHazelcastInstance(config);

        assertClusterSizeEventually(2, hz1);
        assertClusterSizeEventually(2, hz2);

        verify(requestHandler, times(0)).register(any(InstanceInfo.class));

    }

    private EurekaHttpResponse<Applications> generateMockResponse(List<InstanceInfo> infoList){

        Application application = new Application();
        application.setName(APP_NAME);
        for(InstanceInfo info : infoList){
            application.addInstance(info);
        }

        Applications applications = new Applications();
        applications.addApplication(application);

        return anEurekaHttpResponse(200, applications)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }
}
