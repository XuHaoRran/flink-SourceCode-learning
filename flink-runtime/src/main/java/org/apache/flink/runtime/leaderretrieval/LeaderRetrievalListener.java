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

package org.apache.flink.runtime.leaderretrieval;

import javax.annotation.Nullable;

import java.util.UUID;

/**
 * Classes which want to be notified about a changing leader by the {@link LeaderRetrievalService}
 * have to implement this interface.
 *
 * <p>LeaderRetrievalListener用来通知选举了新的Leader，在选举过
 * 程中可能顺利选举出新的Leader，也可能因为内部或者外部异常，导
 * 致无法选举出新的Leader，此时也需要通知各相关组件。对于无法处
 * 理 的 故 障 ， 无 法 进 行 恢 复 ， 作 业 进 入 停 止 状 态 。
 */
public interface LeaderRetrievalListener {

    /**
     * This method is called by the {@link LeaderRetrievalService} when a new leader is elected.
     *
     * <p>If both arguments are null then it signals that leadership was revoked without a new
     * leader having been elected.
     *
     * <p>leader选举服务选举出新的leader，通知其他相关组件
     *
     * @param leaderAddress The address of the new leader
     * @param leaderSessionID The new leader session ID
     */
    void notifyLeaderAddress(@Nullable String leaderAddress, @Nullable UUID leaderSessionID);

    /**
     * This method is called by the {@link LeaderRetrievalService} in case of an exception. This
     * assures that the {@link LeaderRetrievalListener} is aware of any problems occurring in the
     * {@link LeaderRetrievalService} thread.
     *
     * // 当leader选举服务发生异常的时候通知各相关组件
     * @param exception
     */
    void handleError(Exception exception);
}
