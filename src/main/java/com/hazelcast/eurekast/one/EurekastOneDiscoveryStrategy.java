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

import com.google.common.annotations.VisibleForTesting;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.NoLogFactory;
import com.hazelcast.nio.Address;
import com.hazelcast.spi.discovery.AbstractDiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.SimpleDiscoveryNode;
import com.hazelcast.util.UuidUtil;
import com.netflix.appinfo.*;
import com.netflix.appinfo.providers.EurekaConfigBasedInstanceInfoProvider;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.eurekast.one.EurekastOneProperties.*;

class EurekastOneDiscoveryStrategy
        extends AbstractDiscoveryStrategy {

    @VisibleForTesting static final String DEFAULT_NAMESPACE = "hazelcast";
    @VisibleForTesting static final int NUM_RETRIES = 5;
    private static final int VERIFICATION_WAIT_TIMEOUT = 5;
    private static final int DISCOVERY_RETRY_TIMEOUT = 1;

    private final EurekaClient eurekaClient;
    private final ApplicationInfoManager applicationInfoManager;

    private final boolean selfRegistration;
    private final boolean clientMode;

    private final String namespace;

    @VisibleForTesting
    EurekastOneDiscoveryStrategy(EurekaClient eurekaClient,
                                 ApplicationInfoManager manager,
                                 boolean clientMode) {
        super(new NoLogFactory().getLogger(EurekastOneDiscoveryStrategy.class.getName()),
                Collections.<String, Comparable>emptyMap());

        this.selfRegistration = getOrDefault(EUREKAST_ONE_SYSTEM_PREFIX, SELF_REGISTRATION, true);
        this.namespace = getOrDefault(EUREKAST_ONE_SYSTEM_PREFIX, NAMESPACE, DEFAULT_NAMESPACE);

        this.clientMode = clientMode;

        this.eurekaClient = eurekaClient;
        this.applicationInfoManager = manager;
    }

    EurekastOneDiscoveryStrategy(DiscoveryNode localNode, ILogger logger, Map<String, Comparable> properties) {
        super(logger, properties);

        this.selfRegistration = getOrDefault(EUREKAST_ONE_SYSTEM_PREFIX, SELF_REGISTRATION, true);
        this.namespace = getOrDefault(EUREKAST_ONE_SYSTEM_PREFIX, NAMESPACE, "hazelcast");

        this.clientMode = localNode == null;

        this.applicationInfoManager = initializeApplicationInfoManager(localNode);
        this.eurekaClient = new DiscoveryClient(applicationInfoManager, new EurekastOneAwareConfig(this.namespace));
    }

    private ApplicationInfoManager initializeApplicationInfoManager(DiscoveryNode localNode) {
        EurekaInstanceConfig instanceConfig = buildInstanceConfig(localNode);

        InstanceInfo instanceInfo = new EurekaConfigBasedInstanceInfoProvider(instanceConfig).get();
        ApplicationInfoManager manager = new ApplicationInfoManager(instanceConfig, instanceInfo);
        eurekaStatusChange(manager, InstanceInfo.InstanceStatus.STARTING);

        return manager;
    }

    private EurekaInstanceConfig buildInstanceConfig(DiscoveryNode localNode) {
        try {

            String configProperty = DynamicPropertyFactory
                    .getInstance()
                    .getStringProperty("eureka.client.props", "eureka-client")
                    .get();

            String eurekaPropertyFile = String.format("%s.properties", configProperty);
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            URL url = loader.getResource(eurekaPropertyFile);
            if (url == null) {
                throw new IllegalStateException("Cannot locate " + eurekaPropertyFile + " as a classpath resource.");
            }
            Properties props = new Properties();
            props.load(url.openStream());

            String key = String.format("%s.datacenter", this.namespace);
            String value = props.getProperty(key, "");
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
            List<InstanceInfo> instances = application.getInstances();
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
                Map<String, Object> metadata = (Map) instance.getMetadata();
                nodes.add(new SimpleDiscoveryNode(new Address(address, port), metadata));
            }
        }
        return nodes;
    }

    @Override
    public void start() {
        eurekaStatusChange(InstanceInfo.InstanceStatus.UP);
        verifyEurekaRegistration();
    }

    @Override
    public void destroy() {
        eurekaStatusChange(InstanceInfo.InstanceStatus.DOWN);
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

    private void eurekaStatusChange(InstanceInfo.InstanceStatus status) {
        eurekaStatusChange(applicationInfoManager, status);
    }

    private void eurekaStatusChange(ApplicationInfoManager applicationInfoManager, InstanceInfo.InstanceStatus status) {
        if (clientMode || !selfRegistration) {
            return;
        }
        applicationInfoManager.setInstanceStatus(status);
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

    private class EurekastOneAwareConfig extends DefaultEurekaClientConfig {
        EurekastOneAwareConfig(String namespace) {
            super(namespace);
        }

        @Override
        public boolean shouldRegisterWithEureka() {
            return !clientMode && selfRegistration;
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
            this.uuid =  UuidUtil.newSecureUuidString();
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
            if(null == localNode){
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
            if(null == localNode){
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
