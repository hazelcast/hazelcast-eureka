package com.hazelcast.eureka.one;


import com.google.common.base.Preconditions;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;

interface StatusChangeStrategy {
    void update(ApplicationInfoManager manager, InstanceInfo.InstanceStatus status);
    boolean shouldRegister();
}


class NoopUpdater implements StatusChangeStrategy{

    @Override
    public void update(ApplicationInfoManager manager, InstanceInfo.InstanceStatus status) {
    }

    @Override
    public boolean shouldRegister() {
        return false;
    }
}

class DefaultUpdater implements StatusChangeStrategy{
    @Override
    public void update(ApplicationInfoManager manager, InstanceInfo.InstanceStatus status) {
        Preconditions.checkNotNull(manager);
        Preconditions.checkNotNull(status);

        manager.setInstanceStatus(status);
    }

    @Override
    public boolean shouldRegister() {
        return true;
    }
}