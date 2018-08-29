package com.hazelcast.eureka.one;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.Maps;
import com.hazelcast.eureka.one.EurekaOneDiscoveryStrategy.EurekaOneDiscoveryStrategyBuilder;
import com.hazelcast.nio.Address;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;

@RunWith(MockitoJUnitRunner.class)
public class EurekaOneDiscoveryStrategyMetadataEnableRegistrationTest extends AbstractEurekaOneDiscoveryStrategyTest {

    @Mock
    private DiscoveryNode node;

    @Override
    protected void initializeStrategy() {
        HashMap<String, Comparable> properties = Maps.newHashMap();
        properties.put("self-registration", Boolean.TRUE);
        properties.put("use-metadata-for-host-and-port", Boolean.TRUE);
        EurekaOneDiscoveryStrategyBuilder builder = new EurekaOneDiscoveryStrategyBuilder();
        builder.setEurekaClient(eurekaClient)
                .setProperties(properties)
                .setApplicationInfoManager(applicationInfoManager)
                .setDiscoveryNode(node)
                .setGroupName("my-custom-group")
                .setStatusChangeStrategy(new MetadataUpdater(node, true, "my-custom-group"));
        strategy = builder.build();
    }

    @Test
    public void shouldRegisterAndUpdateMetadata() throws Exception{
        InstanceInfo instanceInfo = mock(InstanceInfo.class);
        when(instanceInfo.getId()).thenReturn(RandomStringUtils.random(42));
        when(instanceInfo.getStatus()).thenReturn(InstanceInfo.InstanceStatus.UP);
        when(instanceInfo.getIPAddr()).thenReturn("local");
        
        @SuppressWarnings("unchecked")
        Map<String, String> metadata = mock(HashMap.class);
        when(instanceInfo.getMetadata()).thenReturn(metadata);

        when(eurekaClient.getApplication(APPLICATION_NAME)).thenReturn(new Application());
        when(applicationInfoManager.getInfo()).thenReturn(instanceInfo);
        when(node.getPrivateAddress()).thenReturn(new Address("localhost", 5708));

        strategy.start();

        verify(applicationInfoManager, atLeastOnce()).setInstanceStatus(any(InstanceInfo.InstanceStatus.class));
        verify(applicationInfoManager, atLeastOnce()).getInfo();
        verify(instanceInfo, atLeastOnce()).getMetadata();
        verify(metadata).put(EurekaHazelcastMetadata.HAZELCAST_PORT, "5708");
        verify(metadata).put(EurekaHazelcastMetadata.HAZELCAST_HOST, "localhost");
        verify(metadata).put(EurekaHazelcastMetadata.HAZELCAST_GROUP_NAME, "my-custom-group");
        
    }
}