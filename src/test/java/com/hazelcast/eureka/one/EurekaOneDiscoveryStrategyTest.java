package com.hazelcast.eureka.one;

import com.hazelcast.eureka.one.EurekaOneDiscoveryStrategy.EurekaOneDiscoveryStrategyBuilder;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EurekaOneDiscoveryStrategyTest extends AbstractEurekaOneDiscoveryStrategyTest {

    @Mock
    private DiscoveryNode node;

    @Override
    protected void initializeStrategy() {
        EurekaOneDiscoveryStrategyBuilder builder = new EurekaOneDiscoveryStrategyBuilder();
        builder.setEurekaClient(eurekaClient)
                .setApplicationInfoManager(applicationInfoManager)
                .setDiscoveryNode(node)
                .setStatusChangeStrategy(new DefaultUpdater());
        strategy = builder.build();
    }

    @Test
    public void shouldDiscoverNodesWhenClientsRetrievesFromEurekaServer() {
        Application application = new Application();
        InstanceInfo info = InstanceInfoGenerator.takeOne();
        info.setStatus(InstanceInfo.InstanceStatus.UP);
        application.addInstance(info);

        when(eurekaClient.getApplication(APPLICATION_NAME)).thenReturn(application);

        Iterable<DiscoveryNode> nodes = strategy.discoverNodes();

        assertThat(nodes, notNullValue());
        assertThat(nodes.iterator().hasNext(), is(true));
        assertThat(nodes.iterator().next().getPublicAddress().getHost(), is(info.getIPAddr()));
    }

    @Test
    public void shouldOnlyDiscoverInstancesWithUpStatus(){
        Application application = new Application();
        List<InstanceInfo> infoList = InstanceInfoGenerator
                .newBuilder(5, 1)
                .build()
                .toInstanceList();
        for (InstanceInfo info : infoList) {
            info.setStatus(InstanceInfo.InstanceStatus.DOWN);
            application.addInstance(info);
        }

        when(eurekaClient.getApplication(APPLICATION_NAME)).thenReturn(application);

        Iterable<DiscoveryNode> nodes = strategy.discoverNodes();
        assertThat(nodes.iterator().hasNext(), is(false));
    }

    @Test
    public void shouldOnlyDiscoverInstancesWithValidAddress(){
        Application application = new Application();
        InstanceInfo mockInfo = mock(InstanceInfo.class);
        when(mockInfo.getId()).thenReturn(RandomStringUtils.random(42));
        when(mockInfo.getStatus()).thenReturn(InstanceInfo.InstanceStatus.UP);
        when(mockInfo.getIPAddr()).thenReturn("local");

        application.addInstance(mockInfo);

        when(eurekaClient.getApplication(APPLICATION_NAME)).thenReturn(application);

        Iterable<DiscoveryNode> nodes = strategy.discoverNodes();

        verify(eurekaClient).getApplication(APPLICATION_NAME);
        verify(mockInfo).getStatus();
        verify(mockInfo).getIPAddr();

        assertThat(nodes.iterator().hasNext(), is(false));
    }

    @Test
    public void shouldContinueVerifyRegistrationWhenEurekaClientThrowException() {
        when(eurekaClient.getApplication(APPLICATION_NAME))
                .thenThrow(new RuntimeException())
                .thenReturn(new Application());

        strategy.verifyEurekaRegistration();
        verify(eurekaClient, times(2)).getApplication(eq(APPLICATION_NAME));
    }

    @Test
    public void shouldRetryNodeDiscoveryUntilLimitReached(){
        when(eurekaClient.getApplication(APPLICATION_NAME))
                .thenReturn(null);

        Iterable<DiscoveryNode> actual = strategy.discoverNodes();
        verify(eurekaClient, times(EurekaOneDiscoveryStrategy.NUM_RETRIES)).getApplication(APPLICATION_NAME);

        assertThat(actual.iterator().hasNext(), is(false));
    }


    @Test
    public void shouldEscalateWhenErrorCaughtInVerification(){
        expectedException.expect(Error.class);
        when(eurekaClient.getApplication(APPLICATION_NAME))
                .thenThrow(new Error());

        strategy.start();
        verify(applicationInfoManager).setInstanceStatus(InstanceInfo.InstanceStatus.UP);
    }



    @Test
    public void shouldShutdownClientWhenDestroyCalled(){
        strategy.destroy();
        verify(applicationInfoManager).setInstanceStatus(InstanceInfo.InstanceStatus.DOWN);
        verify(eurekaClient).shutdown();
    }

}