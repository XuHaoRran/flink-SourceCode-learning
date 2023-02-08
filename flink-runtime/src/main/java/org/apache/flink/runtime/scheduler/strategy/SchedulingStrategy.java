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

package org.apache.flink.runtime.scheduler.strategy;

import org.apache.flink.runtime.execution.ExecutionState;
import org.apache.flink.runtime.executiongraph.Execution;
import org.apache.flink.runtime.executiongraph.IntermediateResultPartition;
import org.apache.flink.runtime.jobgraph.IntermediateResultPartitionID;

import java.util.Set;

/**
 * Component which encapsulates the scheduling logic. It can react to execution state changes and
 * partition consumable events. Moreover, it is responsible for resolving task failures.
 * 调度策略，它封装了调度逻辑，当任务状态改变或者分区数据可以被消费时，会触发它的方法
 */
public interface SchedulingStrategy {

    /** Called when the scheduling is started (initial scheduling operation).
     * 开始调度*/
    void startScheduling();

    /**
     * Called whenever vertices need to be restarted (due to task failure).
     * 重启任务
     *
     * @param verticesToRestart The tasks need to be restarted
     */
    void restartTasks(Set<ExecutionVertexID> verticesToRestart);

    /**
     * Called whenever an {@link Execution} changes its state.
     * 当任务状态改变时触发的方法
     * @param executionVertexId The id of the task
     * @param executionState The new state of the execution
     */
    void onExecutionStateChange(ExecutionVertexID executionVertexId, ExecutionState executionState);

    /**
     * Called whenever an {@link IntermediateResultPartition} becomes consumable.
     * IntermediateResultPartition可以被消费时触发的方法
     * @param resultPartitionId The id of the result partition
     */
    void onPartitionConsumable(IntermediateResultPartitionID resultPartitionId);
}
