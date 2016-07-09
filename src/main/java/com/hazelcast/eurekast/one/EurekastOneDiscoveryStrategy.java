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

import com.hazelcast.logging.ILogger;
import com.hazelcast.nio.Address;
import com.hazelcast.spi.discovery.AbstractDiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.SimpleDiscoveryNode;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.CloudInstanceConfig;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.eurekast.one.EurekastOneProperties.EUREKAST_ONE_SYSTEM_PREFIX;
import static com.hazelcast.eurekast.one.EurekastOneProperties.SELF_REGISTRATION;

class EurekastOneDiscoveryStrategy
        extends AbstractDiscoveryStrategy {

    private static final DynamicStringProperty EUREKA_PROPS_FILE = //
            DynamicPropertyFactory.getInstance().getStringProperty("eureka.client.props", "eureka-client");

    private final EurekaClient eurekaClient;
    private final DiscoveryManager discoveryManager;
    private final DynamicPropertyFactory dynamicPropertyFactory;
    private final ApplicationInfoManager applicationInfoManager;

    private final boolean selfRegistration;
    private final boolean clientMode;

    private final String applicationName;

    EurekastOneDiscoveryStrategy(DiscoveryNode localNode, ILogger logger, Map<String, Comparable> properties) {
        super(logger, properties);

        this.selfRegistration = getOrDefault(EUREKAST_ONE_SYSTEM_PREFIX, SELF_REGISTRATION, true);
        this.clientMode = localNode == null;

        this.discoveryManager = initEurekaEnvironment(localNode);
        this.eurekaClient = DiscoveryManager.getInstance().getEurekaClient();
        this.applicationInfoManager = initApplicationInfoManager();
        this.dynamicPropertyFactory = DynamicPropertyFactory.getInstance();
        this.applicationName = applicationInfoManager.getInfo().getAppName();
    }

    public Iterable<DiscoveryNode> discoverNodes() {
        List<DiscoveryNode> nodes = new ArrayList<DiscoveryNode>();

        Application application = null;
        for (int i = 0; i < 5; i++) {
            application = eurekaClient.getApplication(applicationName);
            if (application != null) {
                break;
            }

            // retry
            try {
                TimeUnit.SECONDS.sleep(1);
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

                InetAddress addr = mapAddress(instance);
                int port = instance.getPort();
                Map<String, Object> metadata = (Map) instance.getMetadata();
                nodes.add(new SimpleDiscoveryNode(new Address(addr, port), metadata));
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
        discoveryManager.shutdownComponent();
    }

    private InetAddress mapAddress(InstanceInfo instance) {
        try {
            return InetAddress.getByName(instance.getIPAddr());

        } catch (UnknownHostException e) {
            getLogger().warning("InstanceInfo '" + instance + "' could not be resolved");
        }
        return null;
    }

    private DiscoveryManager initEurekaEnvironment(DiscoveryNode localNode) {
        EurekaInstanceConfig instanceConfig = buildInstanceInfo(localNode);
        DefaultEurekaClientConfig eurekaClientConfig = new EurekastOneAwareConfig();
        DiscoveryManager.getInstance().initComponent(instanceConfig, eurekaClientConfig);
        return DiscoveryManager.getInstance();
    }

    private EurekaInstanceConfig buildInstanceInfo(DiscoveryNode localNode) {
        try {
            String eurekaPropertyFile = EUREKA_PROPS_FILE.get() + ".properties";
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            URL url = loader.getResource(eurekaPropertyFile);
            if (url == null) {
                throw new IllegalStateException("Cannot locate " + eurekaPropertyFile + " as a classpath resource.");
            }
            Properties props = new Properties();
            props.load(url.openStream());
            String value = props.getProperty("eureka.datacenter", "");
            if ("cloud".equals(value.trim().toLowerCase())) {
                return new DelegatingInstanceConfig(new CloudInstanceConfig(), localNode);
            }
            return new DelegatingInstanceConfig(new MyDataCenterInstanceConfig(), localNode);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build EurekaInstanceInfo", e);
        }
    }

    private ApplicationInfoManager initApplicationInfoManager() {
        ApplicationInfoManager applicationInfoManager = ApplicationInfoManager.getInstance();
        eurekaStatusChange(applicationInfoManager, InstanceInfo.InstanceStatus.STARTING);
        return applicationInfoManager;
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

    private void verifyEurekaRegistration() {
        String vipAddress = dynamicPropertyFactory.getStringProperty("eureka.vipAddress", applicationName).get();
        Application application = null;
        do {
            try {
                getLogger().info("Waiting for registration with Eureka...");
                application = eurekaClient.getApplication(vipAddress);
            } catch (Throwable t) {
                if (t instanceof Error) {
                    throw (Error) t;
                }
            }

            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException almostIgnore) {
                Thread.currentThread().interrupt();
            }
        } while (application == null);
    }

    private class EurekastOneAwareConfig extends DefaultEurekaClientConfig {
        @Override
        public boolean shouldRegisterWithEureka() {
            return !clientMode && selfRegistration;
        }
    }

    private static class DelegatingInstanceConfig
            implements EurekaInstanceConfig {

        private final EurekaInstanceConfig instanceConfig;
        private final DiscoveryNode localNode;
        private final String uuid;

        private DelegatingInstanceConfig(EurekaInstanceConfig instanceConfig, DiscoveryNode localNode) {
            this.instanceConfig = instanceConfig;
            this.localNode = localNode;
            this.uuid = UUID.randomUUID().toString();
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
