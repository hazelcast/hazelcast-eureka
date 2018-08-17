package com.hazelcast.eureka.one;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.hazelcast.eureka.one.EurekaOneDiscoveryStrategy.EurekaOneDiscoveryStrategyBuilder;
import com.hazelcast.nio.Address;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;

@RunWith(MockitoJUnitRunner.class)
public class EurekaOneDiscoveryStrategyMetadataTest extends AbstractEurekaOneDiscoveryStrategyTest {

    @Mock
    private DiscoveryNode node;

    @Override
    protected void initializeStrategy() {
        EurekaOneDiscoveryStrategyBuilder builder = new EurekaOneDiscoveryStrategyBuilder();
        builder.setEurekaClient(eurekaClient)
                .setApplicationInfoManager(applicationInfoManager)
                .setDiscoveryNode(node)
                .setGroupName("my-custom-group")
                .setStatusChangeStrategy(new MetadataUpdater(node, "my-custom-group"));
        strategy = builder.build();
    }

   
    @Test
    public void shouldDiscoverInstancesViaMetadata(){
        Application application = new Application();
        InstanceInfo mockInfo = mock(InstanceInfo.class);
        when(mockInfo.getId()).thenReturn(RandomStringUtils.random(42));
        when(mockInfo.getStatus()).thenReturn(InstanceInfo.InstanceStatus.UP);
        when(mockInfo.getIPAddr()).thenReturn("local");
        
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put(EurekaHazelcastMetadata.HAZELCAST_HOST, "127.0.0.1");
        metadata.put(EurekaHazelcastMetadata.HAZELCAST_PORT, "5777");
        metadata.put(EurekaHazelcastMetadata.HAZELCAST_GROUP_NAME, "my-custom-group");
        when(mockInfo.getMetadata()).thenReturn(metadata);

        application.addInstance(mockInfo);

        when(eurekaClient.getApplication(APPLICATION_NAME)).thenReturn(application);

        Iterable<DiscoveryNode> nodes = strategy.discoverNodes();

        verify(eurekaClient).getApplication(APPLICATION_NAME);
        verify(mockInfo).getMetadata();

        assertThat(nodes.iterator().hasNext(), is(true));
        
        DiscoveryNode discoveredNode = nodes.iterator().next();
        assertThat(discoveredNode.getPrivateAddress().getHost(), is("127.0.0.1"));
        assertEquals(discoveredNode.getPrivateAddress().getPort(), 5777);
    }
    
    @Test
    public void shouldSkipDifferentGroup(){
        Application application = new Application();
        InstanceInfo mockInfo = mock(InstanceInfo.class);
        when(mockInfo.getId()).thenReturn(RandomStringUtils.random(42));
        when(mockInfo.getStatus()).thenReturn(InstanceInfo.InstanceStatus.UP);
        when(mockInfo.getIPAddr()).thenReturn("local");
        
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put(EurekaHazelcastMetadata.HAZELCAST_HOST, "127.0.0.1");
        metadata.put(EurekaHazelcastMetadata.HAZELCAST_PORT, "5777");
        metadata.put(EurekaHazelcastMetadata.HAZELCAST_GROUP_NAME, "my-different-group");
        when(mockInfo.getMetadata()).thenReturn(metadata);

        application.addInstance(mockInfo);

        when(eurekaClient.getApplication(APPLICATION_NAME)).thenReturn(application);

        Iterable<DiscoveryNode> nodes = strategy.discoverNodes();

        verify(eurekaClient).getApplication(APPLICATION_NAME);
        verify(mockInfo).getMetadata();

        assertThat(nodes.iterator().hasNext(), is(false));
    }
    
    @Test
    public void shouldRegisterMetadata() throws Exception{
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

        verify(applicationInfoManager, never()).setInstanceStatus(any(InstanceInfo.InstanceStatus.class));
        verify(applicationInfoManager, atLeastOnce()).getInfo();
        verify(instanceInfo, atLeastOnce()).getMetadata();
        verify(metadata).put(EurekaHazelcastMetadata.HAZELCAST_PORT, "5708");
        verify(metadata).put(EurekaHazelcastMetadata.HAZELCAST_HOST, "localhost");
        verify(metadata).put(EurekaHazelcastMetadata.HAZELCAST_GROUP_NAME, "my-custom-group");
    }
}