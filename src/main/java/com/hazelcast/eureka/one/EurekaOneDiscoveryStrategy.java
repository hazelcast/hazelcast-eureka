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

import com.google.common.annotations.VisibleForTesting;
import com.hazelcast.config.Config;
import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.NoLogFactory;
import com.hazelcast.cluster.Address;
import com.hazelcast.spi.discovery.AbstractDiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.SimpleDiscoveryNode;
import com.hazelcast.internal.util.UuidUtil;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.CloudInstanceConfig;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.appinfo.providers.EurekaConfigBasedInstanceInfoProvider;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.transport.jersey3.Jersey3TransportClientFactories;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.eureka.one.EurekaOneProperties.DATACENTER;
import static com.hazelcast.eureka.one.EurekaOneProperties.EUREKA_ONE_SYSTEM_PREFIX;
import static com.hazelcast.eureka.one.EurekaOneProperties.HZ_PROPERTY_DEFINITIONS;
import static com.hazelcast.eureka.one.EurekaOneProperties.NAME;
import static com.hazelcast.eureka.one.EurekaOneProperties.NAMESPACE;
import static com.hazelcast.eureka.one.EurekaOneProperties.SELF_REGISTRATION;
import static com.hazelcast.eureka.one.EurekaOneProperties.SKIP_EUREKA_REGISTRATION_VERIFICATION;
import static com.hazelcast.eureka.one.EurekaOneProperties.USE_CLASSPATH_EUREKA_CLIENT_PROPS;
import static com.hazelcast.eureka.one.EurekaOneProperties.USE_METADATA_FOR_HOST_AND_PORT;

final class EurekaOneDiscoveryStrategy
        extends AbstractDiscoveryStrategy {

    static final class EurekaOneDiscoveryStrategyBuilder {
        private EurekaClient eurekaClient;
        private String groupName = Config.DEFAULT_CLUSTER_NAME;
        private ApplicationInfoManager applicationInfoManager;
        private DiscoveryNode discoveryNode;
        private ILogger logger = new NoLogFactory().getLogger(EurekaOneDiscoveryStrategy.class.getName());
        private Map<String, Comparable> properties = Collections.emptyMap();
        private StatusChangeStrategy changeStrategy;

        EurekaOneDiscoveryStrategyBuilder setEurekaClient(final EurekaClient eurekaClient) {
            this.eurekaClient = eurekaClient;
            if (eurekaClient != null) {
                this.applicationInfoManager = eurekaClient.getApplicationInfoManager();
            }
            return this;
        }

        EurekaOneDiscoveryStrategyBuilder setGroupName(final String groupName) {
            this.groupName = groupName;
            return this;
        }

        EurekaOneDiscoveryStrategyBuilder setApplicationInfoManager(
                final ApplicationInfoManager applicationInfoManager) {
            this.applicationInfoManager = applicationInfoManager;
            return this;
        }

        EurekaOneDiscoveryStrategyBuilder setDiscoveryNode(final DiscoveryNode discoveryNode) {
            this.discoveryNode = discoveryNode;
            return this;
        }

        EurekaOneDiscoveryStrategyBuilder setILogger(final ILogger logger) {
            this.logger = logger;
            return this;
        }

        EurekaOneDiscoveryStrategyBuilder setProperties(final Map<String, Comparable> properties) {
            this.properties = properties;
            return this;
        }

        @VisibleForTesting
        EurekaOneDiscoveryStrategyBuilder setStatusChangeStrategy(StatusChangeStrategy statusChangeStrategy) {
            this.changeStrategy = statusChangeStrategy;
            return this;
        }

        EurekaOneDiscoveryStrategy build() {

            if (null == changeStrategy) {
                changeStrategy = new DefaultUpdater();
            }

            if (null == discoveryNode) {
                changeStrategy = new NoopUpdater();
            }

            return new EurekaOneDiscoveryStrategy(this);
        }
    }

    @VisibleForTesting
    static final String DEFAULT_NAMESPACE = "hazelcast";
    @VisibleForTesting
    static final int NUM_RETRIES = 5;
    private static final int VERIFICATION_WAIT_TIMEOUT = 5;
    private static final int DISCOVERY_RETRY_TIMEOUT = 1;

    private final EurekaClient eurekaClient;
    private final String groupName;
    private final ApplicationInfoManager applicationInfoManager;

    private final Boolean useClasspathEurekaClientProps;
    private final String namespace;
    private StatusChangeStrategy statusChangeStrategy;
    private final Boolean skipEurekaRegistrationVerification;
    private final Boolean useMetadataForHostAndPort;

    private EurekaOneDiscoveryStrategy(final EurekaOneDiscoveryStrategyBuilder builder) {
        super(builder.logger, builder.properties);

        this.namespace = getOrDefault(EUREKA_ONE_SYSTEM_PREFIX, NAMESPACE, "hazelcast");
        boolean selfRegistration = getOrDefault(EUREKA_ONE_SYSTEM_PREFIX, SELF_REGISTRATION, true);
        this.useMetadataForHostAndPort = getOrDefault(EUREKA_ONE_SYSTEM_PREFIX, USE_METADATA_FOR_HOST_AND_PORT, false);
        this.skipEurekaRegistrationVerification =
                getOrDefault(EUREKA_ONE_SYSTEM_PREFIX, SKIP_EUREKA_REGISTRATION_VERIFICATION, false);
        this.useClasspathEurekaClientProps = getOrDefault(EUREKA_ONE_SYSTEM_PREFIX, USE_CLASSPATH_EUREKA_CLIENT_PROPS, true);
        this.groupName = builder.groupName != null ? builder.groupName : Config.DEFAULT_CLUSTER_NAME;

        // override registration if requested
        if (!selfRegistration && !useMetadataForHostAndPort) {
            statusChangeStrategy = new NoopUpdater();
        } else if (useMetadataForHostAndPort && builder.discoveryNode != null) {
            statusChangeStrategy = new MetadataUpdater(builder.discoveryNode, selfRegistration, this.groupName);
        } else {
            this.statusChangeStrategy = builder.changeStrategy;
        }

        if (builder.applicationInfoManager == null) {
            this.applicationInfoManager = initializeApplicationInfoManager(builder.discoveryNode);
        } else {
            this.applicationInfoManager = builder.applicationInfoManager;
        }

        if (builder.eurekaClient == null) {
            EurekaClientConfig eurekaClientConfig;
            if (useClasspathEurekaClientProps) {
                eurekaClientConfig = new EurekaOneAwareConfig(this.namespace);
            } else {
                eurekaClientConfig = new PropertyBasedEurekaClientConfig(
                        this.namespace,
                        getEurekaClientProperties(this.namespace, this.getProperties()));
            }
            this.eurekaClient = new DiscoveryClient(applicationInfoManager, eurekaClientConfig, Jersey3TransportClientFactories.getInstance());
        } else {
            this.eurekaClient = builder.eurekaClient;
        }
    }

    private String getAppname() {
        Comparable name = this.getProperties().get(NAME.key());
        return name == null ? "unknown" : name.toString();
    }

    private Map<String, Object> getEurekaClientProperties(String namespace, Map<String, Comparable> properties) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Comparable> e : properties.entrySet()) {
            result.put(namespace + "." + e.getKey(), e.getValue());
        }
        for (PropertyDefinition p : HZ_PROPERTY_DEFINITIONS) {
            result.remove(namespace + "." + p.key());
        }
        return result;
    }

    private ApplicationInfoManager initializeApplicationInfoManager(DiscoveryNode localNode) {
        EurekaInstanceConfig instanceConfig = buildInstanceConfig(localNode);

        InstanceInfo instanceInfo = new EurekaConfigBasedInstanceInfoProvider(instanceConfig).get();
        ApplicationInfoManager manager = new ApplicationInfoManager(instanceConfig, instanceInfo);
        statusChangeStrategy.update(manager, InstanceInfo.InstanceStatus.STARTING);

        return manager;
    }

    private EurekaInstanceConfig buildInstanceConfig(DiscoveryNode localNode) {
        try {
            String value;
            if (this.useClasspathEurekaClientProps) {
                String configProperty = DynamicPropertyFactory
                        .getInstance()
                        .getStringProperty("eureka.client.props", "eureka-client").get();

                String eurekaPropertyFile = String.format("%s.properties", configProperty);
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                URL url = loader.getResource(eurekaPropertyFile);
                if (url == null) {
                    throw new IllegalStateException("Cannot locate " + eurekaPropertyFile + " as a classpath resource.");
                }
                Properties props = new Properties();
                props.load(url.openStream());

                String key = String.format("%s.datacenter", this.namespace);
                value = props.getProperty(key, "");
            } else {
                value = String.valueOf(getProperties().get(DATACENTER.key()));
            }
            if ("cloud".equals(value.trim().toLowerCase())) {
                return new DelegatingInstanceConfig(new CloudInstanceConfig(this.namespace), localNode);
            }
            if (this.useClasspathEurekaClientProps) {
                return new DelegatingInstanceConfig(new MyDataCenterInstanceConfig(this.namespace), localNode);
            }
            return new DelegatingInstanceConfig(new MyDataCenterInstanceConfig(this.namespace), localNode, getAppname());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build EurekaInstanceInfo", e);
        }
    }

    private String getGroupNameFromMetadata(Map<String, String> metadata) {
        String groupName = Config.DEFAULT_CLUSTER_NAME;
        if (metadata.containsKey(EurekaHazelcastMetadata.HAZELCAST_GROUP_NAME)) {
            groupName = metadata.get(EurekaHazelcastMetadata.HAZELCAST_GROUP_NAME);
        }
        return groupName;
    }

    public Iterable<DiscoveryNode> discoverNodes() {
        List<DiscoveryNode> nodes = new ArrayList<>();
        String applicationName = applicationInfoManager.getEurekaInstanceConfig().getAppname();

        Application application = null;
        for (int i = 0; i < NUM_RETRIES; i++) {
            application = eurekaClient.getApplication(applicationName);
            if (application != null) {
                break;
            }
            try {
                TimeUnit.SECONDS.sleep(DISCOVERY_RETRY_TIMEOUT);
            } catch (InterruptedException almostIgnore) {
                Thread.currentThread().interrupt();
            }
        }
        if (application != null) {
            List<InstanceInfo> instances = application.getInstancesAsIsFromEureka();

            for (InstanceInfo instance : instances) {
                // Only recognize up and running instances
                if (instance.getStatus() != InstanceInfo.InstanceStatus.UP) {
                    continue;
                }

                Map<String, String> metadata = instance.getMetadata();
                @SuppressWarnings({"unchecked", "rawtypes"}) Map<String, String> properties = metadata;

                if (useMetadataForHostAndPort) {
                    addNodeUsingMetadata(nodes, instance, metadata, properties);
                } else {
                    addNode(nodes, instance, properties);
                }
            }
        }
        return nodes;
    }

    private void addNodeUsingMetadata(List<DiscoveryNode> nodes, InstanceInfo instance, Map<String, String> metadata,
            Map<String, String> properties) {
        if (getGroupNameFromMetadata(metadata).equals(groupName)) {
            InetAddress address = mapAddress(instance);
            int port = mapPort(instance);
            if (address != null) {
                nodes.add(new SimpleDiscoveryNode(new Address(address, port), properties));
            }
        }
    }

    private void addNode(List<DiscoveryNode> nodes, InstanceInfo instance, Map<String, String> properties) {
        InetAddress address = mapAddress(instance);
        if (null == address) {
            return;
        }

        int port = instance.getPort();
        nodes.add(new SimpleDiscoveryNode(new Address(address, port), properties));
    }

    @Override
    public void start() {
        statusChangeStrategy.update(applicationInfoManager, InstanceInfo.InstanceStatus.UP);
        if (!skipEurekaRegistrationVerification) {
            verifyEurekaRegistration();
        }
    }

    @Override
    public void destroy() {
        statusChangeStrategy.update(applicationInfoManager, InstanceInfo.InstanceStatus.DOWN);
        if (null != eurekaClient) {
            eurekaClient.shutdown();
        }
    }

    private InetAddress mapAddress(InstanceInfo instance) {
        try {
            if (useMetadataForHostAndPort) {
                Map<String, String> metadata = instance.getMetadata();
                return InetAddress.getByName(metadata.get(EurekaHazelcastMetadata.HAZELCAST_HOST));
            } else {
                return InetAddress.getByName(instance.getIPAddr());
            }
        } catch (UnknownHostException e) {
            getLogger().warning("InstanceInfo '" + instance + "' could not be resolved");
        }
        return null;
    }

    private int mapPort(InstanceInfo instance) {
        Map<String, String> metadata = instance.getMetadata();
        if (metadata.containsKey(EurekaHazelcastMetadata.HAZELCAST_PORT)) {
            return Integer.parseInt(metadata.get(EurekaHazelcastMetadata.HAZELCAST_PORT));
        }
        return -1;
    }

    @VisibleForTesting
    void verifyEurekaRegistration() {
        String applicationName = applicationInfoManager.getEurekaInstanceConfig().getAppname();
        Application application;
        do {
            try {
                getLogger().info("Waiting for registration with Eureka...");
                application = eurekaClient.getApplication(applicationName);
                if (application != null) {
                    getLogger().info("Registered in Eureka");
                    break;
                }
            } catch (Throwable t) {
                if (t instanceof Error) {
                    throw (Error) t;
                }
            }

            try {
                TimeUnit.SECONDS.sleep(VERIFICATION_WAIT_TIMEOUT);
            } catch (InterruptedException almostIgnore) {
                Thread.currentThread().interrupt();
            }
        } while (true);
    }

    @VisibleForTesting
    EurekaClient getEurekaClient() {
        return eurekaClient;
    }

    private class EurekaOneAwareConfig extends DefaultEurekaClientConfig {
        EurekaOneAwareConfig(String namespace) {
            super(namespace);
        }

        @Override
        public boolean shouldRegisterWithEureka() {
            return statusChangeStrategy.shouldRegister();
        }
    }

    private static final class DelegatingInstanceConfig
            implements EurekaInstanceConfig {

        private final EurekaInstanceConfig instanceConfig;
        private final DiscoveryNode localNode;
        private final String uuid;
        private final String appname;

        private DelegatingInstanceConfig(EurekaInstanceConfig instanceConfig, DiscoveryNode localNode) {
            this(instanceConfig, localNode, instanceConfig.getAppname());
        }

        private DelegatingInstanceConfig(EurekaInstanceConfig instanceConfig, DiscoveryNode localNode, String appname) {
            this.instanceConfig = instanceConfig;
            this.localNode = localNode;
            this.uuid = UuidUtil.newSecureUuidString();
            this.appname = appname;
        }

        public String getInstanceId() {
            return uuid;
        }

        public String getAppname() {
            return appname;
        }

        public String getAppGroupName() {
            return instanceConfig.getAppGroupName();
        }

        public boolean isInstanceEnabledOnit() {
            return instanceConfig.isInstanceEnabledOnit();
        }

        public int getNonSecurePort() {
            if (null == localNode) {
                return instanceConfig.getNonSecurePort();
            }
            return localNode.getPrivateAddress().getPort();
        }

        public int getSecurePort() {
            return instanceConfig.getSecurePort();
        }

        public boolean isNonSecurePortEnabled() {
            return instanceConfig.isNonSecurePortEnabled();
        }

        public boolean getSecurePortEnabled() {
            return instanceConfig.getSecurePortEnabled();
        }

        public int getLeaseRenewalIntervalInSeconds() {
            return instanceConfig.getLeaseRenewalIntervalInSeconds();
        }

        public int getLeaseExpirationDurationInSeconds() {
            return instanceConfig.getLeaseExpirationDurationInSeconds();
        }

        public String getVirtualHostName() {
            return instanceConfig.getVirtualHostName();
        }

        public String getSecureVirtualHostName() {
            return instanceConfig.getSecureVirtualHostName();
        }

        public String getASGName() {
            return instanceConfig.getASGName();
        }

        public String getHostName(boolean refresh) {
            return instanceConfig.getHostName(refresh);
        }

        public Map<String, String> getMetadataMap() {
            return instanceConfig.getMetadataMap();
        }

        public DataCenterInfo getDataCenterInfo() {
            return instanceConfig.getDataCenterInfo();
        }

        public String getIpAddress() {
            if (null == localNode) {
                return instanceConfig.getIpAddress();
            }
            return localNode.getPrivateAddress().getHost();
        }

        public String getStatusPageUrlPath() {
            return instanceConfig.getStatusPageUrlPath();
        }

        public String getStatusPageUrl() {
            return instanceConfig.getStatusPageUrl();
        }

        public String getHomePageUrlPath() {
            return instanceConfig.getHomePageUrlPath();
        }

        public String getHomePageUrl() {
            return instanceConfig.getHomePageUrl();
        }

        public String getHealthCheckUrlPath() {
            return instanceConfig.getHealthCheckUrlPath();
        }

        public String getHealthCheckUrl() {
            return instanceConfig.getHealthCheckUrl();
        }

        public String getSecureHealthCheckUrl() {
            return instanceConfig.getSecureHealthCheckUrl();
        }

        public String[] getDefaultAddressResolutionOrder() {
            return instanceConfig.getDefaultAddressResolutionOrder();
        }

        public String getNamespace() {
            return instanceConfig.getNamespace();
        }
    }
}
