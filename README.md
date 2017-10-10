# Table of Contents
  
  * Latest Release Version
  * [Discovering Members within EC2 VPC](#discovering-members-within-ec2-vpc)
  * [Debugging](#debugging)
  * [Hazelcast Performance on AWS](#hazelcast-performance-on-aws)
    * [Selecting EC2 Instance Type](#selecting-ec2-instance-type)
    * [Dealing with Network Latency](#dealing-with-network-latency)
    * [Selecting Virtualization](#selecting-virtualization)

## Latest Release Version
```$xml
<dependency>
  <groupId>com.hazelcast</groupId>
  <artifactId>hazelcast-eureka-one</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Discovering Members within EC2 VPC

Hazelcast supports Eureka V1 discovery. It is useful when you do not want to provide or you cannot provide the list of 
possible IP addresses.

Please note that this document does not cover details for Eureka Server installation on AWS regarding DNS, IAM roles or 
any other required service configurations.

Eureka can either have its location hard-coded or it can be found using DNS. Using DNS is much more flexible, therefore 
given example configurations assume available DNS resolution for Eureka server.

### Configuring Eureka Discovery for Hazelcast Cluster Members

- Add the *hazelcast-eureka-one.jar* dependency to your project. 
- Disable join over multicast, TCP/IP and AWS by setting the `enabled` attribute of the related tags to `false`.
- Enable Discovery SPI by adding "hazelcast.discovery.enabled" property to your config.
- Add *eureka-client.properties* file to working directory or use `eureka.client.props` dynamic property to define 
property file path without `properties` extension.

The following is an example declarative configuration.

```xml
 <hazelcast>
   ...
    <properties>
        <property name="hazelcast.discovery.enabled">true</property>
    </properties>
   
    <network>
      ...
        <join>
            <multicast enabled="false"/>
            <tcp-ip enabled="false"/>
            <aws enabled="false"/>
            <discovery-strategies>
                <discovery-strategy class="EurekaOneDiscoveryStrategy" enabled="true">
                    <properties>
                       <property name="self-registration">true</property>
                       <property name="namespace">hazelcast</property>
                    </properties>
                </discovery-strategy>
            </discovery-strategies>
          </join>
      </network>
 </hazelcast>
```
* `self-registration`: Defines if the Discovery SPI plugin will register itself with the Eureka 1 service discovery. 
It is optional. Default value is `true`.
* `namespace`: Definition for providing different namespaces in order to not to collide with other service registry clients 
in eureka-client.properties file. It is optional. Default value is `hazelcast`.

Below you can also find an example of Eureka client properties. 

```$properties
hazelcast.environment=prod
hazelcast.shouldUseDns=false
hazelcast.datacenter=cloud
hazelcast.name=hazelcast-test
hazelcast.serviceUrl.default=http://<your-eureka-server-url>
```

> `IMPORTANT`: `hazelcast.name` property is crucial for cluster members to discover each other. Please give 
identical names in regarding `eureka-client.properties` on EC2 hosts for building cluster of your choice properly.

### Configuring Eureka Discovery for Hazelcast Client

- Add the *hazelcast-eureka-one.jar* dependency to your project. 
- Add *eureka-client.properties* file to working directory or use `eureka.client.props` dynamic property to define 
property file path without `properties` extension.

The following is an example declarative configuration.

```xml
<network>
    ...
    <discovery-strategies>
        <discovery-strategy class="EurekaOneDiscoveryStrategy" enabled="true">
            <properties>
                <property name="namespace">hazelcast</property>
            </properties>
        </discovery-strategy>
    </discovery-strategies>
</network>
```

Below you can also find an example of Eureka client properties.
```$properties
hazelcast.environment=prod
hazelcast.shouldUseDns=false
hazelcast.datacenter=cloud
hazelcast.name=hazelcast-test
hazelcast.serviceUrl.default=http://<your-eureka-server-url>/eureka/v2/
```

> `NOTE:` Hazelcast clients do not register themselves to Eureka server with given `namespace` or default namespace, 
which is `hazelcast`. Therefore, `self-registration` property is overridden and it has no effect.

> `IMPORTANT`: `hazelcast.name` property is crucial for clients to discover cluster members.

#### Reusing existing Eureka Client instance
If your application provides already configured `EurekaClient` instance e.g. if you are using Spring Cloud, you can reuse your existing client:

```
EurekaClient eurekaClient = ...
EurekaOneDiscoveryStrategyFactory.setEurekaClient(eurekaClient);
```
 
## Debugging

When needed, Hazelcast can log the events for the instances that exist in a region. To see what has happened or to 
trace the activities while forming the cluster, change the log level in your logging mechanism to `FINEST` or `DEBUG`. 
After this change, you can also see in the generated log whether the instances are accepted or rejected, and the reason 
the instances were rejected. Note that changing the log level in this way may affect the performance of the cluster. 
Please see the <a href="http://docs.hazelcast.org/docs/latest-dev/manual/html-single/index.html#logging-configuration" target="_blank">Logging Configuration</a> 
for information on logging mechanisms.

## Hazelcast Performance on AWS

Amazon Web Services (AWS) platform can be an unpredictable environment compared to traditional in-house data centers. 
This is because the machines, databases or CPUs are shared with other unknown applications in the cloud, causing fluctuations. 
When you gear up your Hazelcast application from a physical environment to Amazon EC2, you should configure it so that 
any network outage or fluctuation is minimized and its performance is maximized. This section provides notes on improving 
the performance of Hazelcast on AWS.

### Selecting EC2 Instance Type

Hazelcast is an in-memory data grid that distributes the data and computation to the members that are connected with 
a network, making Hazelcast very sensitive to the network. Not all EC2 Instance types are the same in terms of the 
network performance. It is recommended that you choose instances that have **10 Gigabit** or **High** network 
performance for Hazelcast deployments. Please see the below list for the recommended instances.

* m3.2xlarge - High
* m1.xlarge - High
* c3.2xlarge - High
* c3.4xlarge - High
* c3.8xlarge - 10 Gigabit
* c1.xlarge - High
* cc2.8xlarge - 10 Gigabit
* m2.4xlarge - High
* cr1.8xlarge - 10 Gigabit

### Dealing with Network Latency

Since data is sent and received very frequently in Hazelcast applications, latency in the network becomes a crucial issue. 
In terms of the latency, AWS cloud performance is not the same for each region. There are vast differences in the speed 
and optimization from region to region.

When you do not pay attention to AWS regions, Hazelcast applications may run tens or even hundreds of times slower 
than necessary. The following notes are potential workarounds.

- Create a cluster only within a region. It is not recommended that you deploy a single cluster that spans across 
multiple regions.
- If a Hazelcast application is hosted on Amazon EC2 instances in multiple EC2 regions, you can reduce the latency by 
serving the end users` requests from the EC2 region which has the lowest network latency. Changes in network connectivity 
and routing result in changes in the latency between hosts on the Internet. Amazon has a web service (Route 53) that 
lets the cloud architects use DNS to route end-user requests to the EC2 region that gives the fastest response. This 
latency-based routing is based on latency measurements performed over a period of time. Please have a look at 
<a href="http://docs.aws.amazon.com/Route53/latest/DeveloperGuide/HowDoesRoute53Work.html" target="_blank">Route53</a>.
- Move the deployment to another region. The <a href="http://www.cloudping.info/" target="_blank">CloudPing</a> tool 
gives instant estimates on the latency from your location. By using it frequently, CloudPing can be helpful to determine 
the regions which have the lowest latency.
- The <a href="http://cloudharmony.com/speedtest" target="_blank">SpeedTest</a> tool allows you to test the network 
latency and also the downloading/uploading speeds.

### Selecting Virtualization

AWS uses two virtualization types to launch the EC2 instances: Para-Virtualization (PV) and Hardware-assisted Virtual
Machine (HVM). According to the tests we performed, HVM provided up to three times higher throughput than PV. Therefore, 
we recommend you use HVM when you run Hazelcast on EC2.

***RELATED INFORMATION***

*You can download the white paper "Amazon EC2 Deployment Guide for Hazelcast IMDG" <a href="https://hazelcast.com/resources/amazon-ec2-deployment-guide/" target="_blank">here</a>.*
