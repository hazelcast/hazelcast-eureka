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

import com.google.common.annotations.VisibleForTesting;
import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.NoLogFactory;
import com.hazelcast.nio.Address;
import com.hazelcast.spi.discovery.AbstractDiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.SimpleDiscoveryNode;
import com.hazelcast.util.UuidUtil;
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

import static com.hazelcast.eureka.one.EurekaOneProperties.EUREKA_ONE_SYSTEM_PREFIX;
import static com.hazelcast.eureka.one.EurekaOneProperties.HZ_PROPERTY_DEFINITIONS;
import static com.hazelcast.eureka.one.EurekaOneProperties.NAMESPACE;
import static com.hazelcast.eureka.one.EurekaOneProperties.SELF_REGISTRATION;
import static com.hazelcast.eureka.one.EurekaOneProperties.USE_CLASSPATH_EUREKA_CLIENT_PROPS;

final class EurekaOneDiscoveryStrategy
        extends AbstractDiscoveryStrategy {

    static final class EurekaOneDiscoveryStrategyBuilder {
        private EurekaClient eurekaClient;
        private ApplicationInfoManager applicationInfoManager;
        private DiscoveryNode discoveryNode;
        private ILogger logger = new NoLogFactory().getLogger(EurekaOneDiscoveryStrategy.class.getName());
        private Map<String, Comparable> properties = Collections.emptyMap();
        private StatusChangeStrategy changeStrategy;

        EurekaOneDiscoveryStrategyBuilder setEurekaClient(final EurekaClient eurekaClient) {
            this.eurekaClient = eurekaClient;
            if (eurekaClient != null) {
                this.applicationInfoManager = eurekaClient.getApplicationInfoManager();
                this.changeStrategy = new NoopUpdater();
            }
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
    private final ApplicationInfoManager applicationInfoManager;

    private final Boolean useClasspathEurekaClientProps;
    private final String namespace;
    private StatusChangeStrategy statusChangeStrategy;

    private EurekaOneDiscoveryStrategy(final EurekaOneDiscoveryStrategyBuilder builder) {
        super(builder.logger, builder.properties);

        this.namespace = getOrDefault(EUREKA_ONE_SYSTEM_PREFIX, NAMESPACE, "hazelcast");
        boolean selfRegistration = getOrDefault(EUREKA_ONE_SYSTEM_PREFIX, SELF_REGISTRATION, true);
        this.useClasspathEurekaClientProps = getOrDefault(EUREKA_ONE_SYSTEM_PREFIX, USE_CLASSPATH_EUREKA_CLIENT_PROPS, true);
        // override registration if requested
        if (!selfRegistration) {
            statusChangeStrategy = new NoopUpdater();
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
            this.eurekaClient = new DiscoveryClient(applicationInfoManager, eurekaClientConfig);
        } else {
            this.eurekaClient = builder.eurekaClient;
        }
    }

    private Map<String, Object> getEurekaClientProperties(String namespace, Map<String, Comparable> properties) {
        Map<String, Object> result = new HashMap<String, Object>();
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
                value = String.valueOf(getProperties().get("datacenter"));
            }
            if ("cloud".equals(value.trim().toLowerCase())) {
                return new DelegatingInstanceConfig(new CloudInstanceConfig(this.namespace), localNode);
            }
            return new DelegatingInstanceConfig(new MyDataCenterInstanceConfig(this.namespace), localNode);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build EurekaInstanceInfo", e);
        }
    }

    public Iterable<DiscoveryNode> discoverNodes() {
        List<DiscoveryNode> nodes = new ArrayList<DiscoveryNode>();
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

                InetAddress address = mapAddress(instance);
                if (null == address) {
                    continue;
                }

                int port = instance.getPort();
                @SuppressWarnings({"unchecked", "rawtypes"})
                Map<String, Object> metadata = (Map) instance.getMetadata();
                if (metadata.containsKey(EurekaHazelcastMetadata.HAZELCAST_PORT)) {
                    port = Integer.parseInt(metadata.get(EurekaHazelcastMetadata.HAZELCAST_PORT).toString());
                }
                if (metadata.containsKey(EurekaHazelcastMetadata.HAZELCAST_HOST)) {
                    try {
                        address = InetAddress.getByName(metadata.get(EurekaHazelcastMetadata.HAZELCAST_HOST).toString());
                    } catch (UnknownHostException e) {
                        getLogger().warning("Instance address '" + instance + "' could not be resolved", e);
                    }
                }
                nodes.add(new SimpleDiscoveryNode(new Address(address, port), metadata));
            }
        }
        return nodes;
    }

    @Override
    public void start() {
        statusChangeStrategy.update(applicationInfoManager, InstanceInfo.InstanceStatus.UP);
        verifyEurekaRegistration();
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
            return InetAddress.getByName(instance.getIPAddr());

        } catch (UnknownHostException e) {
            getLogger().warning("InstanceInfo '" + instance + "' could not be resolved");
        }
        return null;
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

        private DelegatingInstanceConfig(EurekaInstanceConfig instanceConfig, DiscoveryNode localNode) {
            this.instanceConfig = instanceConfig;
            this.localNode = localNode;
            this.uuid = UuidUtil.newSecureUuidString();
        }

        public String getInstanceId() {
            return uuid;
        }

        public String getAppname() {
            return instanceConfig.getAppname();
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
