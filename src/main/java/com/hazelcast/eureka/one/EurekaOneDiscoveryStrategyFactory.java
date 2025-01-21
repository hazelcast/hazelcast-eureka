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

import com.google.common.collect.Lists;
import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.eureka.one.EurekaOneDiscoveryStrategy.EurekaOneDiscoveryStrategyBuilder;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.DiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryStrategyFactory;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.transport.jersey.TransportClientFactories;

import java.util.Collection;
import java.util.Map;

/**
 * <p>Configuration class of the Hazelcast Discovery Plugin for Eureka.</p>
 * <p>For possible configuration properties please refer to the public constants of this class.</p>
 */
public class EurekaOneDiscoveryStrategyFactory
        implements DiscoveryStrategyFactory {

    static final Collection<PropertyDefinition> PROPERTY_DEFINITIONS = Lists.newArrayList();

    static {
        PROPERTY_DEFINITIONS.addAll(EurekaOneProperties.HZ_PROPERTY_DEFINITIONS);
        PROPERTY_DEFINITIONS.addAll(EurekaOneProperties.EUREKA_CLIENT_PROPERTY_DEFINITIONS);
    }

    private static TransportClientFactories clientFactories;
    private static EurekaClient eurekaClient;
    private static String groupName;

    public Class<? extends DiscoveryStrategy> getDiscoveryStrategyType() {
        return EurekaOneDiscoveryStrategy.class;
    }

    public DiscoveryStrategy newDiscoveryStrategy(DiscoveryNode discoveryNode, ILogger logger,
                                                  Map<String, Comparable> properties) {
        EurekaOneDiscoveryStrategyBuilder builder = new EurekaOneDiscoveryStrategyBuilder();
        builder.setDiscoveryNode(discoveryNode).setILogger(logger).setProperties(properties)
                .setEurekaClient(eurekaClient).setGroupName(groupName).setTransportClientFactories(clientFactories);
        return builder.build();
    }

    public Collection<PropertyDefinition> getConfigurationProperties() {
        return PROPERTY_DEFINITIONS;
    }

    /**
     * Allows to use already configured {@link EurekaClient} instead of creating new one.
     *
     * @param eurekaClient {@link EurekaClient} instance
     */
    public static void setEurekaClient(EurekaClient eurekaClient) {
        EurekaOneDiscoveryStrategyFactory.eurekaClient = eurekaClient;
    }

    /**
     * Allows to use already configured {@link TransportClientFactories} instead of using {@link com.netflix.discovery.shared.transport.jersey3.Jersey3TransportClientFactories}.
     *
     * @param clientFactories {@link TransportClientFactories} instance
     */
    public static void setTransportClientFactories(TransportClientFactories clientFactories) {
        EurekaOneDiscoveryStrategyFactory.clientFactories = clientFactories;
    }

    /**
     * Set hazelcast cluster name.
     *
     * @param groupName
     *            hazelcast cluster name
     */
    public static void setGroupName(String groupName) {
        EurekaOneDiscoveryStrategyFactory.groupName = groupName;
    }
}
