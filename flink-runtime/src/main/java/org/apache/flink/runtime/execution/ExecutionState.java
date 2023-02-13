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

package org.apache.flink.runtime.execution;

/**
 * An enumeration of all states that a task can be in during its execution. Tasks usually start in
 * the state {@code CREATED} and switch states according to this diagram:
 *
 * <pre>{@code
 *  CREATED  -> SCHEDULED -> DEPLOYING -> INITIALIZING -> RUNNING -> FINISHED
 *     |            |            |          |              |
 *     |            |            |    +-----+--------------+
 *     |            |            V    V
 *     |            |         CANCELLING -----+----> CANCELED
 *     |            |                         |
 *     |            +-------------------------+
 *     |
 *     |                                   ... -> FAILED
 *     V
 * RECONCILING  -> INITIALIZING | RUNNING | FINISHED | CANCELED | FAILED
 *
 * }</pre>
 *
 * <p>It is possible to enter the {@code RECONCILING} state from {@code CREATED} state if job
 * manager fail over, and the {@code RECONCILING} state can switch into any existing task state.
 *
 * <p>It is possible to enter the {@code FAILED} state from any other state.
 *
 * <p>The states {@code FINISHED}, {@code CANCELED}, and {@code FAILED} are considered terminal
 * states.
 */
public enum ExecutionState {
    /**
     *  ExecutionGraph 创 建 出 来 之 后 ， Execution 默 认 的 状 态 就 是
     * Created
     */
    CREATED,

    /**
     * 在ExecutionGraph中有Scheduled状态，在TaskManager上的Task
     * 不会有该状态，Scheduled状态表示被调度执行的Task进入Scheduled
     * 状态，但是并不是所有的Task都会变成Scheduled状态，然后开始申请
     * 所需的计算资源
     */
    SCHEDULED,
    /**
     * Deploying状态表示资源申请完毕，向TaskManager部署Task
     */
    DEPLOYING,

    /**
     * TaskManager启动Task，并通知JobManager该Task进入Running状
     * 态，JobManager将该Task所在的ExecutionGraph中对应的Execution设
     * 置为Running状态
     */
    RUNNING,

    /**
     * This state marks "successfully completed". It can only be reached when a program reaches the
     * "end of its input". The "end of input" can be reached when consuming a bounded input (fix set
     * of files, bounded query, etc) or when stopping a program (not cancelling!) which make the
     * input look like it reached its end at a specific point.
     */
    FINISHED,

    /**
     * Cancelling 状 态 与 Scheduled 、 Deploying 状 态 类 似 ， 是
     * ExecutionGraph中维护的一种状态，表示正在取消Task执行，等待
     * TaskManager取消Task的执行，并返回结果
     */
    CANCELING,

    /**
     * TaskManager取消Task执行成功，并通知JobManager，JobManager
     * 将该Task所在的ExecutionGraph中对应的Execution设置为Canceled状
     * 态。
     */
    CANCELED,
    /**
     * 若TaskManger执行Task时出现异常导致Task无法继续执行，Task
     * 会进入Failed状态，并通知JobManager，JobManager将该Task所在的
     * ExecutionGraph中对应的Execution设置为Failed状态。整个作业也将
     * 会进入Failed状态。
     */
    FAILED,

    RECONCILING,

    /** Restoring last possible valid state of the task if it has it. */
    INITIALIZING;

    public boolean isTerminal() {
        return this == FINISHED || this == CANCELED || this == FAILED;
    }
}
