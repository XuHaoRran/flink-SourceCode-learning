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

package org.apache.flink.runtime.jobmanager.scheduler;

import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.instance.SlotSharingGroupId;
import org.apache.flink.runtime.jobgraph.JobVertexID;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * A slot sharing units defines which different task (from different job vertices) can be deployed
 * together within a slot. This is a soft permission, in contrast to the hard constraint defined by
 * a co-location hint.
 *
 * <p>默认情况下，Flink作业共享同一个SlotSharingGroup，同一个作
 * 业中来自不同JobVertex的Task可以共享作业。使用Slot共享，可以在
 * 一个Slot中运行Task组成的流水线。共享Slot带来了如下优点。
 *
 * <ul>
 *     <li>（1）资源分配简单:
 * Flink集群需要的Slot的数量和作业中的最高并行度一致，不需要
 * 计算一个程序总共包含多少个Task。
 *     <li>（2）资源利用率高:
 * 如果没有Slot共享，资源密集型的Task（如长周期的窗口计算）
 * 跟 非 密 集 型 的 作 业 （ 如 Source/Map ） 占 用 相 同 的 资 源 ， 在 整 个
 * TaskManager层面上，资源没有充分利用。如果共享Slot，将并行度从2提高到6，可以充分利用Slot资源，同时确保资源保
 * 密集型的Task在Taskmanager中公平分配。
 * </ul>
 *
 * <p>非强制性共享约束，Slot共享根据组内的JobVertices ID查找是
 * 否已有可以共享的Slot，只要确保相同JobVertext ID不能出现在一个
 * 共享的Slot内即可。
 * <p>在符合资源要求的Slot中，找到没有相同JobVertext ID的Slot，
 * 根据Slot选择策略选择一个Slot即可，如果没有符合条件的，则申请
 * 新的Slot。
 */
public class SlotSharingGroup implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private final Set<JobVertexID> ids = new TreeSet<>();

    private final SlotSharingGroupId slotSharingGroupId = new SlotSharingGroupId();

    // Represents resources of all tasks in the group. Default to be UNKNOWN.
    private ResourceProfile resourceProfile = ResourceProfile.UNKNOWN;

    // --------------------------------------------------------------------------------------------

    public void addVertexToGroup(final JobVertexID id) {
        ids.add(checkNotNull(id));
    }

    public void removeVertexFromGroup(final JobVertexID id) {
        ids.remove(checkNotNull(id));
    }

    public Set<JobVertexID> getJobVertexIds() {
        return Collections.unmodifiableSet(ids);
    }

    public SlotSharingGroupId getSlotSharingGroupId() {
        return slotSharingGroupId;
    }

    public void setResourceProfile(ResourceProfile resourceProfile) {
        this.resourceProfile = checkNotNull(resourceProfile);
    }

    public ResourceProfile getResourceProfile() {
        return resourceProfile;
    }

    // ------------------------------------------------------------------------
    //  Utilities
    // ------------------------------------------------------------------------

    @Override
    public String toString() {
        return "SlotSharingGroup " + this.ids.toString();
    }
}
