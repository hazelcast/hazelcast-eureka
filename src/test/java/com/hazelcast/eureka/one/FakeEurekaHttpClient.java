/*
 * Copyright (c) 2008-2025, Hazelcast, Inc. All Rights Reserved.
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

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import jakarta.ws.rs.core.MediaType;

import static com.netflix.discovery.shared.transport.EurekaHttpResponse.anEurekaHttpResponse;

public class FakeEurekaHttpClient implements EurekaHttpClient {

    private final Applications applications;

    public FakeEurekaHttpClient(Applications applications) {
        this.applications = applications;
    }

    @Override
    public EurekaHttpResponse<Void> register(InstanceInfo info) {
        applications.getRegisteredApplications(info.getAppName()).addInstance(info);
        return anEurekaHttpResponse(500).build();
    }

    @Override
    public EurekaHttpResponse<Void> cancel(String appName, String id) {
        return anEurekaHttpResponse(200).build();
    }

    @Override
    public EurekaHttpResponse<InstanceInfo> sendHeartBeat(String appName, String id, InstanceInfo info, InstanceInfo.InstanceStatus overriddenStatus) {
        System.out.println("sendHeartBeat");
        info.setStatus(overriddenStatus);
        return anEurekaHttpResponse(200, info)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    @Override
    public EurekaHttpResponse<Void> statusUpdate(String appName, String id, InstanceInfo.InstanceStatus newStatus, InstanceInfo info) {
        InstanceInfo instanceInfo = applications.getRegisteredApplications(appName).getByInstanceId(id);
        applications.getRegisteredApplications(appName).removeInstance(instanceInfo);
        info.setStatus(newStatus);
        applications.getRegisteredApplications(appName).addInstance(info);
        return anEurekaHttpResponse(200).build();
    }

    @Override
    public EurekaHttpResponse<Void> deleteStatusOverride(String appName, String id, InstanceInfo info) {
        return anEurekaHttpResponse(200).build();
    }

    @Override
    public EurekaHttpResponse<Applications> getApplications(String... regions) {
        return anEurekaHttpResponse(200, applications)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    @Override
    public EurekaHttpResponse<Applications> getDelta(String... regions) {
        return anEurekaHttpResponse(200, applications)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    @Override
    public EurekaHttpResponse<Applications> getVip(String vipAddress, String... regions) {
        return anEurekaHttpResponse(200, applications)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    @Override
    public EurekaHttpResponse<Applications> getSecureVip(String secureVipAddress, String... regions) {
        return anEurekaHttpResponse(200, applications)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    @Override
    public EurekaHttpResponse<Application> getApplication(String appName) {
        return anEurekaHttpResponse(200, applications.getRegisteredApplications(appName))
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    @Override
    public EurekaHttpResponse<InstanceInfo> getInstance(String appName, String id) {
        InstanceInfo instance = applications.getRegisteredApplications(appName).getByInstanceId(id);
        return anEurekaHttpResponse(200, instance)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    @Override
    public EurekaHttpResponse<InstanceInfo> getInstance(String id) {
        for (Application app : applications.getRegisteredApplications()) {
            InstanceInfo instance = app.getByInstanceId(id);
            if (instance != null) {
                return anEurekaHttpResponse(200, instance)
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build();
            }
        }
        return EurekaHttpResponse.<InstanceInfo>anEurekaHttpResponse(404, null).build();
    }

    @Override
    public void shutdown() {
    }
}
