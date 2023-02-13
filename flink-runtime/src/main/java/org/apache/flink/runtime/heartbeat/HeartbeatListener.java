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
 * Interface for the interaction with the {@link HeartbeatManager}. The heartbeat listener is used
 * for the following things:
 *
 * <p>心跳监听器是和HeartbeatManager密切相关的接口，可以看成服
 * 务的输出。
 *
 * <p>JobMaster、ResourceManager、TaskManager都实现了该接口，心
 * 跳超时时触发异常处理。
 *
 * <ul>
 *   <li>Notifications about heartbeat timeouts 心跳超时通知
 *   <li>Payload reports of incoming heartbeats 接收心跳信息中的Payload
 *   <li>Retrieval of payloads for outgoing heartbeats 检索作为心跳响应输出的Payload
 * </ul>
 *
 * @param <I> Type of the incoming payload
 * @param <O> Type of the outgoing payload
 */
public interface HeartbeatListener<I, O> {

    /**
     * Callback which is called if a heartbeat for the machine identified by the given resource ID
     * times out. 心跳超时会调用该方法
     *
     * @param resourceID Resource ID of the machine whose heartbeat has timed out
     */
    void notifyHeartbeatTimeout(ResourceID resourceID);

    /**
     * Callback which is called if a target specified by the given resource ID is no longer
     * reachable.
     *
     * @param resourceID resourceID identifying the target that is no longer reachable
     */
    void notifyTargetUnreachable(ResourceID resourceID);

    /**
     * Callback which is called whenever a heartbeat with an associated payload is received. The
     * carried payload is given to this method.
     *
     * 接受到有关心跳的Payload就会执行该方法
     *
     * @param resourceID Resource ID identifying the sender of the payload
     * @param payload Payload of the received heartbeat
     */
    void reportPayload(ResourceID resourceID, I payload);

    /**
     * Retrieves the payload value for the next heartbeat message.
     *
     * 检索下一个心跳信息的Payload
     *
     * @param resourceID Resource ID identifying the receiver of the payload
     * @return The payload for the next heartbeat
     */
    O retrievePayload(ResourceID resourceID);
}
