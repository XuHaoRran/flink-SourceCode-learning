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

import org.apache.flink.runtime.jobgraph.JobVertex;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.util.AbstractID;

import java.util.List;

/**
 * {@code CoLocationGroup} refers to a list of {@link JobVertex} instances, where the <i>i-th</i>
 * subtask of one vertex has to be executed on the same {@code TaskManager} as the <i>i-th</i>
 * subtask of all other {@code JobVertex} instances in the same group.
 *
 * <p>The co-location group is used to make sure that the i-th subtasks for iteration head and
 * iteration tail are scheduled on the same TaskManager.
 *
 * <p>CoLocationGroup又叫作本地约束共享组，具有强制性的Slot共享
 * 限制，CoLocationGroup用在迭代运算中，即在IterativeStream的API
 * 中 调 用 。 迭 代 运 算 中 的 Task 必 须 共 享 同 一 个 TaskManager 的 Slot 。
 * CoLocationGroup可以看成是SlotSharingGroup的特例。
 *
 * <p>此处需要注意，JobGraph向ExecutionGraph的转换过程中，为每
 * 一个ExecutionVertex赋予了按照并行度编写的编号，相同编号的迭代
 * 计 算 ExecutionVertex 会 被 放 入 本 地 共 享 约 束 组 中 ， 共 享 相 同 的
 * CoLocationConstraint对象，在调度的时候，根据编号就能找到本组
 * 其他Task的Slot信息。
 *
 * <p>CoLocation 共 享 根 据 组 内 每 个 ExecutionVertex 关 联 的
 * CoLocationConstraint查找是否有相同CoLocationConstraint约束已
 * 分配Slot可用，在调度作业执行的时候，首先要找到本约束中其他
 * Task部署的TaskManager，如果没有则申请一个新的Slot，如果有则共
 * 享该TaskManager上的Slot。
 */
public interface CoLocationGroup {

    /**
     * Returns the unique identifier describing this co-location constraint as a group.
     *
     * @return The group's identifier.
     */
    AbstractID getId();

    /**
     * Returns the IDs of the {@link JobVertex} instances participating in this group.
     *
     * @return The group's members represented by their {@link JobVertexID}s.
     */
    List<JobVertexID> getVertexIds();

    /**
     * Returns the {@link CoLocationConstraint} for a specific {@code subTaskIndex}.
     *
     * @param subTaskIndex The index of the subtasks for which a {@code CoLocationConstraint} shall
     *     be returned.
     * @return The corresponding {@code CoLocationConstraint} instance.
     */
    CoLocationConstraint getLocationConstraint(final int subTaskIndex);
}
