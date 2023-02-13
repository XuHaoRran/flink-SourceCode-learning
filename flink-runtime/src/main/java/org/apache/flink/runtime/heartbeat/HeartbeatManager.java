/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.heartbeat;

import org.apache.flink.runtime.clusterframework.types.ResourceID;

/**
 * A heartbeat manager has to be able to start/stop monitoring a {@link HeartbeatTarget}, and report
 * heartbeat timeouts for this target.
 *
 * <p>通用的心跳管理器用来启动或停止监视HeartbeatTarget，并报告
 * 该 目 标 心 跳 超 时 事 件 。 通 过 monitorTarget 来 传 递 并 监 控
 * HeartbeatTarget，这个方法可以看成整个服务的输入，告诉心跳服务
 * 去管理哪些目标。
 *
 * @param <I> Type of the incoming payload
 * @param <O> Type of the outgoing payload
 */
public interface HeartbeatManager<I, O> extends HeartbeatTarget<I> {

    /**
     * Start monitoring a {@link HeartbeatTarget}. Heartbeat timeouts for this target are reported
     * to the {@link HeartbeatListener} associated with this heartbeat manager.
     * 取消监控心跳目标，ResourceID被监控目标组织标识
     * @param resourceID Resource ID identifying the heartbeat target
     * @param heartbeatTarget Interface to send heartbeat requests and responses to the heartbeat
     *     target
     */
    void monitorTarget(ResourceID resourceID, HeartbeatTarget<O> heartbeatTarget);

    /**
     * Stops monitoring the heartbeat target with the associated resource ID.
     *
     * @param resourceID Resource ID of the heartbeat target which shall no longer be monitored
     */
    void unmonitorTarget(ResourceID resourceID);

    /** Stops the heartbeat manager.
     * 停止当前心跳管理器
     * */
    void stop();

    /**
     * Returns the last received heartbeat from the given target.
     * 返回最近一次心跳时间，如果心跳目标被移除了则返回-1
     * @param resourceId for which to return the last heartbeat
     * @return Last heartbeat received from the given target or -1 if the target is not being
     *     monitored.
     */
    long getLastHeartbeatFrom(ResourceID resourceId);
}
