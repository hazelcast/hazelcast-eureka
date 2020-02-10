# Hazelcast Discovery Plugin for Eureka

This repository contains a plugin which provides the automatic Hazelcast member discovery using Eureka Service Registry.

***NOTE:*** *hazelcast-eureka-one 2.0+* is compatible with *hazelcast 4.0+*, for older hazelcast versions you need to use *hazelcast-eureka-one 1.1.x*.

## Embedded mode

To use Hazelcast embedded in your application, you need to add the plugin dependency into your Maven/Gradle file. Then, when you provide `hazelcast.xml` (and `hazelcast-client.properties`) as presented below, your Hazelcast instances will use Eureka Server to discover each other automatically.

#### Maven

```xml
<dependency>
  <groupId>com.hazelcast</groupId>
  <artifactId>hazelcast-eureka-one</artifactId>
  <version>${hazelcast-eureka-version}</version>
</dependency>
```

#### Gradle

```groovy
compile group: "com.hazelcast", name: "hazelcast-eureka-one", version: "${hazelcast-eureka-version}"
```

## Configuration

Make sure you have:
* `hazelcast-eureka-one.jar` in your classpath
* Hazelcast configuration (`hazelcast.xml` or Java-based configuration)
* Eureka client configuration (`eureka-client.properties` file or `eureka.client.props` dynamic property in the Hazelcast configuration)

### Hazelcast Configuration

#### XML Configuration

```xml
<hazelcast>
  <network>
    <join>
      <multicast enabled="false"/>
      <eureka enabled="true">
        <self-registration>true</self-registration>
        <namespace>hazelcast</namespace>
      </eureka>
    </join>
  </network>
</hazelcast>
```

#### Java-based Configuration

```java
config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
config.getNetworkConfig().getJoin().getEurekaConfig().setEnabled(true)
      .setProperty("self-registration", "true")
      .setProperty("namespace", "hazelcast");
```

The following properties can be configured:
* `self-registration`: Defines if the Discovery SPI plugin will register itself with the Eureka 1 service discovery. It is optional. Default value is `true`.
* `namespace`: Definition for providing different namespaces in order not to collide with other service registry clients in eureka-client.properties file. It is optional. Default value is `hazelcast`.
* `use-metadata-for-host-and-port`: Defines if the Discovery SPI plugin will use Eureka metadata map to store host and port of Hazelcast instance, and when it looks for other nodes it will use the metadata as well.
Default value is `false`.
* `skip-eureka-registration-verification`: When first node starts, it takes some time to do self-registration with Eureka Server. Until Eureka data is updated it make no sense to verify registration. See <a href="https://github.com/Netflix/eureka/wiki/Understanding-eureka-client-server-communication#time-lag" target="_blank">Time Lag</a>. This option will speed up startup when starting first cluster node. Default value is `false`.

### Eureka Client Configuration

#### Properties File

Below you can also find an example of `eureka-client.properties`. 

```$properties
hazelcast.shouldUseDns=false
hazelcast.name=hazelcast-test
hazelcast.serviceUrl.default=http://<your-eureka-server-url>
```

**Note**: `hazelcast.name` property is crucial for cluster members to discover each other. Please give the identical names in regarding `eureka-client.properties` on each Hazelcast member.

#### Embedded Properties

In some environments adding the `eureka-client.properties` file to the classpath is not desirable or feasible. To support this use case, it is possible to specify the Eureka client properties in the Hazelcast configuration. Set the `use-classpath-eureka-client-props` property to `false`, then add the Eureka client properties _without prepending the namespace_, as they will be applied to the namespace specified with the `namespace` property.

**NOTE:** If `use-classpath-eureka-client-props` is `true` (its default value), all Eureka client properties in the Hazelcast configuration will be ignored.

The following is an example declarative configuration, equivalent to the example given above.

```xml
<hazelcast>
  <network>
    <join>
      <multicast enabled="false"/>
      <eureka enabled="true">
        <self-registration>true</self-registration>
        <namespace>hazelcast</namespace>
        <use-classpath-eureka-client-props>false</use-classpath-eureka-client-props>
        <shouldUseDns>false</shouldUseDns>
        <name>hazelcast-test</name>
        <serviceUrl.default>http://your-eureka-server-url</serviceUrl.default>
      </eureka>
    </join>
  </network>
</hazelcast>
```

## Hazelcast Client Configuration

Configuring Hazelcast Client follows exactly the same steps as configuring Hazelcast member, so you need to have:
* `hazelcast-eureka-one.jar` in your classpath
* Hazelcast client configuration (`hazelcast-client.xml` or Java-based configuration)
* Eureka client configuration (`eureka-client.properties` file or `eureka.client.props` dynamic property in the Hazelcast configuration)

Following are example declarative and programmatic configuration snippets.

#### XML Configuration

```xml
<hazelcast-client>
  <network>
    <eureka enabled="true">
      <namespace>hazelcast</namespace>
    </eureka>
  </network>
</hazelcast-client>
```

#### Java-based Configuration

```java
clientConfig.getEurekaConfig().setEnabled(true)
            .setProperty("namespace", "hazelcast");
```

**Note:** Hazelcast clients do not register themselves to Eureka server, therefore `self-registration` property has no effect.

**Note:** The `eureka-client.properties` file and the `eureka.client.props` dynamic property mechanism work exactly the same as described in the Hazelcast Member Configuration.

## Reusing existing EurekaClient instance

If your application already provides a configured `EurekaClient` instance e.g. if you are using Spring Cloud, you can reuse your existing client:

```
EurekaClient eurekaClient = ...
EurekaOneDiscoveryStrategyFactory.setEurekaClient(eurekaClient);
EurekaOneDiscoveryStrategyFactory.setGroupName("dev"); // optional group name. Default is 'dev'.
```

Note that if you have Hazelcast embedded in your application and you want to use Eureka Service discovery for both the application itself and Hazelcast, by default they collide. You can solve this issue in one of the following manners:
* use a separate `EurekaClient` for the application and a separate `EurekaClient` for Hazelcast
* use the same `EurekaClient`, but set `use-metadata-for-host-and-port` property to `true` (it makes Hazelcast store its host/port in the Metadata for your Eureka application).

Please note that If you use metadata to store cluster member addresses on Eureka application "hazelcast" then the discovery plugin will only find the host and port stored in the metadata for that app, not for any other Eureka app.

Please check more in [Hazelcast Eureka Code Samples](https://github.com/hazelcast/hazelcast-code-samples/tree/master/hazelcast-integration/eureka/springboot-embedded).

## Code Samples

Please find the Hazelcast Eureka Code Samples [here](https://github.com/hazelcast/hazelcast-code-samples/tree/master/hazelcast-integration/eureka/springboot-embedded).

## How to find us?

In case of any question or issue, please raise a GH issue, send an email to [Hazelcast Google Groups](https://groups.google.com/forum/#!forum/hazelcast) or contact as directly via [Hazelcast Gitter](https://gitter.im/hazelcast/hazelcast).
