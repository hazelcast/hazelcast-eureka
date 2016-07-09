/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
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

import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.config.properties.SimplePropertyDefinition;
import com.hazelcast.core.TypeConverter;

import static com.hazelcast.config.properties.PropertyTypeConverter.BOOLEAN;

/**
 * <p>Configuration class of the Hazelcast Discovery Plugin for <a href="https://github.com/Netflix/eureka">Eureka 1</a>.</p>
 * <p>For possible configuration properties please refer to the public constants of this class.</p>
 */
public final class EurekastOneProperties {

    /**
     * <p>Configuration System Environment Prefix: <tt>hazelcast.eurekastone.</tt></p>
     * Defines the prefix for system environment variables and JVM command line parameters.<br/>
     * Defining or overriding properties as JVM parameters or using the system environment, those
     * properties need to be prefixed to prevent collision on property names.<br/>
     * Example: {@link #SELF_REGISTRATION} will be:
     * <pre>
     *     -Dhazelcast.eurekastone.self-registration-=value
     * </pre>
     */
    public static final String EUREKAST_ONE_SYSTEM_PREFIX = "hazelcast.eurekastone";

    /**
     * <p>Configuration key: <tt>self-registration</tt></p>
     * <p>Defines if the Discovery SPI plugin will register itself with the Eureka 1 service discovery.</p>
     * <p>The default value is: <tt>true</tt></p>
     */
    public static final PropertyDefinition SELF_REGISTRATION = property("self-registration", BOOLEAN);

    // Prevent instantiation
    private EurekastOneProperties() {
    }

    private static PropertyDefinition property(String key, TypeConverter typeConverter) {
        return new SimplePropertyDefinition(key, true, typeConverter);
    }

}
