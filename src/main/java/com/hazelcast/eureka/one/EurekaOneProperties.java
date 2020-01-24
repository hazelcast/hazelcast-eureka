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

import java.util.Collection;

import com.google.common.collect.Lists;
import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.config.properties.PropertyTypeConverter;
import com.hazelcast.config.properties.SimplePropertyDefinition;
import com.hazelcast.core.TypeConverter;

import static com.hazelcast.config.properties.PropertyTypeConverter.BOOLEAN;
import static com.hazelcast.config.properties.PropertyTypeConverter.STRING;
import static com.hazelcast.eureka.one.PropertyBasedEurekaClientConfigConstants.*;

/**
 * <p>Configuration class of the Hazelcast Discovery Plugin for <a href="https://github.com/Netflix/eureka">Eureka 1</a>.</p>
 * <p>For possible configuration properties please refer to the public constants of this class.</p>
 */
public final class EurekaOneProperties {

    /**
     * <p>Configuration System Environment Prefix: <tt>hazelcast.eurekaone.</tt></p>
     * Defines the prefix for system environment variables and JVM command line parameters.<br/>
     * Defining or overriding properties as JVM parameters or using the system environment, those
     * properties need to be prefixed to prevent collision on property names.<br/>
     * Example: {@link #SELF_REGISTRATION} will be:
     * <pre>
     *     -Dhazelcast.eurekaone.self-registration=value
     * </pre>
     * Example: {@link #NAMESPACE} will be:
     * <pre>
     *     -Dhazelcast.eurekaone.namespace=value
     * </pre>
     */
    public static final String EUREKA_ONE_SYSTEM_PREFIX = "hazelcast.eurekaone";

    /**
     * <p>Configuration key: <tt>use-classpath-eureka-client-props</tt></p>
     * <p>Defines if the Discovery SPI plugin will use the standard Eureka <tt>eureka.client.props</tt></p>
     * <p>If <tt>true</tt>, the classpath-based Eureka properties file will be used,
     * otherwise the plugin will use properties defined in the <tt>discovery-strategy</tt> config itself.</p>
     * <p>The default value is: <tt>true</tt></p>
     */
    public static final PropertyDefinition USE_CLASSPATH_EUREKA_CLIENT_PROPS =
            property("use-classpath-eureka-client-props", BOOLEAN);

    /**
     * <p>Configuration key: <tt>name</tt></p>
     * <p>Defines the App Name.
     * This is only used when <tt>use-classpath-eureka-client-props</tt> is <tt>false</tt>.</p>
     * <p>The default value is: <tt>unknown</tt></p>
     */
    public static final PropertyDefinition NAME =
            property("name", STRING);

    /**
     * <p>Configuration key: <tt>datacenter</tt></p>
     * <p>Defines the Eureka server datacenter.
     * This is only used when <tt>use-classpath-eureka-client-props</tt> is <tt>false</tt>.</p>
     */
    public static final PropertyDefinition DATACENTER =
            property("datacenter", STRING);

    /**
     * <p>Configuration key: <tt>self-registration</tt></p>
     * <p>Defines if the Discovery SPI plugin will register itself with the Eureka 1 service discovery.</p>
     * <p>The default value is: <tt>true</tt></p>
     */
    public static final PropertyDefinition SELF_REGISTRATION = property("self-registration", BOOLEAN);

    /**
     * <p>
     * Configuration key: <tt>use-metadata-for-host-and-port</tt>
     * </p>
     * <p>
     * Defines if the Discovery SPI plugin will use Eureka metadata map to store host and port of Hazelcast
     * instance, and when it looks for other nodes it will use the metadata as well.
     * </p>
     * <p>
     * The default value is: <tt>false</tt>
     * </p>
     */
    public static final PropertyDefinition USE_METADATA_FOR_HOST_AND_PORT = property("use-metadata-for-host-and-port", BOOLEAN);

    /**
     * <p>
     * Configuration key: <tt>skip-eureka-registration-verification</tt>
     * </p>
     * <p>
     * When first node starts, it takes some time to do self-registration with
     * Eureka Server. Until Eureka data is updated it make no sense to verify
     * registration. See
     * https://github.com/Netflix/eureka/wiki/Understanding-eureka-client-server-communication#time-lag
     * This option will speed up startup when starting first cluster node.
     * </p>
     * <p>
     * The default value is: <tt>false</tt>
     * </p>
     */
    public static final PropertyDefinition SKIP_EUREKA_REGISTRATION_VERIFICATION =
            property("skip-eureka-registration-verification", BOOLEAN);

    /**
     * <p>Configuration key: <tt>namespace</tt></p>
     * <p>Definition for providing different namespaces in order to not collide with other service registry clients in
     * eureka-client.properties file.</p>
     * <p>The default value is: <tt>hazelcast</tt></p>
     */
    public static final PropertyDefinition NAMESPACE = property("namespace", STRING);

    static final Collection<PropertyDefinition> HZ_PROPERTY_DEFINITIONS = Lists.newArrayList(
            USE_CLASSPATH_EUREKA_CLIENT_PROPS,
            NAME,
            DATACENTER,
            SELF_REGISTRATION,
            NAMESPACE,
            USE_METADATA_FOR_HOST_AND_PORT,
            SKIP_EUREKA_REGISTRATION_VERIFICATION
    );

    static final Collection<PropertyDefinition> EUREKA_CLIENT_PROPERTY_DEFINITIONS = Lists.newArrayList(
            property(REGISTRY_REFRESH_INTERVAL_KEY, PropertyTypeConverter.INTEGER),
            property(REGISTRATION_REPLICATION_INTERVAL_KEY, PropertyTypeConverter.INTEGER),
            property(INITIAL_REGISTRATION_REPLICATION_DELAY_KEY, PropertyTypeConverter.INTEGER),
            property(EUREKA_SERVER_URL_POLL_INTERVAL_KEY, PropertyTypeConverter.INTEGER),
            property(EUREKA_SERVER_PROXY_HOST_KEY, PropertyTypeConverter.STRING),
            property(EUREKA_SERVER_PROXY_PORT_KEY, PropertyTypeConverter.STRING),
            property(EUREKA_SERVER_PROXY_USERNAME_KEY, PropertyTypeConverter.STRING),
            property(EUREKA_SERVER_PROXY_PASSWORD_KEY, PropertyTypeConverter.STRING),
            property(EUREKA_SERVER_GZIP_CONTENT_KEY, PropertyTypeConverter.BOOLEAN),
            property(EUREKA_SERVER_READ_TIMEOUT_KEY, PropertyTypeConverter.INTEGER),
            property(EUREKA_SERVER_CONNECT_TIMEOUT_KEY, PropertyTypeConverter.INTEGER),
            property(BACKUP_REGISTRY_CLASSNAME_KEY, PropertyTypeConverter.STRING),
            property(EUREKA_SERVER_MAX_CONNECTIONS_KEY, PropertyTypeConverter.INTEGER),
            property(EUREKA_SERVER_MAX_CONNECTIONS_PER_HOST_KEY, PropertyTypeConverter.INTEGER),
            property(EUREKA_SERVER_URL_CONTEXT_KEY, PropertyTypeConverter.STRING),
            property(EUREKA_SERVER_FALLBACK_URL_CONTEXT_KEY, PropertyTypeConverter.STRING),
            property(EUREKA_SERVER_PORT_KEY, PropertyTypeConverter.STRING),
            property(EUREKA_SERVER_FALLBACK_PORT_KEY, PropertyTypeConverter.STRING),
            property(EUREKA_SERVER_DNS_NAME_KEY, PropertyTypeConverter.STRING),
            property(EUREKA_SERVER_FALLBACK_DNS_NAME_KEY, PropertyTypeConverter.STRING),
            property(SHOULD_USE_DNS_KEY, PropertyTypeConverter.BOOLEAN),
            property(REGISTRATION_ENABLED_KEY, PropertyTypeConverter.BOOLEAN),
            property(SHOULD_PREFER_SAME_ZONE_SERVER_KEY, PropertyTypeConverter.BOOLEAN),
            property(SHOULD_ALLOW_REDIRECTS_KEY, PropertyTypeConverter.BOOLEAN),
            property(SHOULD_LOG_DELTA_DIFF_KEY, PropertyTypeConverter.BOOLEAN),
            property(SHOULD_DISABLE_DELTA_KEY, PropertyTypeConverter.BOOLEAN),
            property(SHOULD_FETCH_REMOTE_REGION_KEY, PropertyTypeConverter.STRING),
            property(CLIENT_REGION_KEY, PropertyTypeConverter.STRING),
            property(CLIENT_REGION_FALLBACK_KEY, PropertyTypeConverter.STRING),
            property(CONFIG_EUREKA_SERVER_SERVICE_URL_PREFIX + ".default", PropertyTypeConverter.STRING),
            property(SHOULD_FILTER_ONLY_UP_INSTANCES_KEY, PropertyTypeConverter.BOOLEAN),
            property(EUREKA_SERVER_CONNECTION_IDLE_TIMEOUT_KEY, PropertyTypeConverter.INTEGER),
            property(FETCH_REGISTRY_ENABLED_KEY, PropertyTypeConverter.BOOLEAN),
            property(FETCH_SINGLE_VIP_ONLY_KEY, PropertyTypeConverter.STRING),
            property(HEARTBEAT_THREADPOOL_SIZE_KEY, PropertyTypeConverter.INTEGER),
            property(HEARTBEAT_BACKOFF_BOUND_KEY, PropertyTypeConverter.INTEGER),
            property(CACHEREFRESH_THREADPOOL_SIZE_KEY, PropertyTypeConverter.INTEGER),
            property(CACHEREFRESH_BACKOFF_BOUND_KEY, PropertyTypeConverter.INTEGER),
            property(CONFIG_DOLLAR_REPLACEMENT_KEY, PropertyTypeConverter.STRING),
            property(CONFIG_ESCAPE_CHAR_REPLACEMENT_KEY, PropertyTypeConverter.STRING),
            property(SHOULD_ONDEMAND_UPDATE_STATUS_KEY, PropertyTypeConverter.BOOLEAN),
            property(CLIENT_ENCODER_NAME_KEY, PropertyTypeConverter.STRING),
            property(CLIENT_DECODER_NAME_KEY, PropertyTypeConverter.STRING),
            property(CLIENT_DATA_ACCEPT_KEY, PropertyTypeConverter.STRING)
    );

    // Prevent instantiation
    private EurekaOneProperties() {
    }

    private static PropertyDefinition property(String key, TypeConverter typeConverter) {
        return new SimplePropertyDefinition(key, true, typeConverter);
    }

}
