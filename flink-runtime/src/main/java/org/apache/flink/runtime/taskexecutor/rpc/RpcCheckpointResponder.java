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

package org.apache.flink.runtime.taskexecutor.rpc;

import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.checkpoint.CheckpointCoordinatorGateway;
import org.apache.flink.runtime.checkpoint.CheckpointException;
import org.apache.flink.runtime.checkpoint.CheckpointMetrics;
import org.apache.flink.runtime.checkpoint.TaskStateSnapshot;
import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.messages.checkpoint.DeclineCheckpoint;
import org.apache.flink.runtime.taskmanager.CheckpointResponder;
import org.apache.flink.util.Preconditions;

import static org.apache.flink.runtime.checkpoint.TaskStateSnapshot.serializeTaskStateSnapshot;

public class RpcCheckpointResponder implements CheckpointResponder {

    private final CheckpointCoordinatorGateway checkpointCoordinatorGateway;

    public RpcCheckpointResponder(CheckpointCoordinatorGateway checkpointCoordinatorGateway) {
        this.checkpointCoordinatorGateway =
                Preconditions.checkNotNull(checkpointCoordinatorGateway);
    }

    @Override
    public void acknowledgeCheckpoint(
            JobID jobID,
            ExecutionAttemptID executionAttemptID,
            long checkpointId,
            CheckpointMetrics checkpointMetrics,
            TaskStateSnapshot subtaskState) {
        // 向JobMaster中checkpointcoordinator发送消息报告checkpoint完成

        // 在向JobMaster汇报的消息中，TaskStateSnapshot中保存了本次
        // 检查点的State数据，如果是内存型的StateBackend，那么其中保存的
        // 是真实的State数据，如果是文件型的StateBackend，其中保存的则是
        // 状态的句柄（StateHandle）。在分布式文件系统中的保存路径也是通
        // 过TaskStateSnapshot中保存的信息恢复回来的。
        // 状态的句柄分为OperatorStateHandle和KeyedStateHandle，分别
        // 对应于OperatorState和KyedState，同时也区分了原始状态和托管状
        // 态。
        // RpcCheckpointResponder底层依赖Flink的RPC框架的方式远程调
        // 用JobMaster的相关方法来完成报告事件。
        checkpointCoordinatorGateway.acknowledgeCheckpoint(
                jobID,
                executionAttemptID,
                checkpointId,
                checkpointMetrics,
                serializeTaskStateSnapshot(subtaskState));
    }

    @Override
    public void reportCheckpointMetrics(
            JobID jobID,
            ExecutionAttemptID executionAttemptID,
            long checkpointId,
            CheckpointMetrics checkpointMetrics) {
        checkpointCoordinatorGateway.reportCheckpointMetrics(
                jobID, executionAttemptID, checkpointId, checkpointMetrics);
    }

    @Override
    public void declineCheckpoint(
            JobID jobID,
            ExecutionAttemptID executionAttemptID,
            long checkpointId,
            CheckpointException checkpointException) {

        checkpointCoordinatorGateway.declineCheckpoint(
                new DeclineCheckpoint(
                        jobID, executionAttemptID, checkpointId, checkpointException));
    }
}
