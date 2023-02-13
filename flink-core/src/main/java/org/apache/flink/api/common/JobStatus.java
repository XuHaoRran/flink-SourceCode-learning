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

package org.apache.flink.api.common;

import org.apache.flink.annotation.PublicEvolving;

/** Possible states of a job once it has been accepted by the dispatcher.
 * 在Flink中使用状态机管理作业的状态，作业状态在JobStatus中
 * 定义，总共有9种状态。
 *
 * */
@PublicEvolving
public enum JobStatus {
    /**
     * The job has been received by the Dispatcher, and is waiting for the job manager to receive
     * leadership and to be created.
     */
    INITIALIZING(TerminalState.NON_TERMINAL),

    /** Job is newly created, no task has started to run. 作业刚被创建，还没有Task开始执行 */
    CREATED(TerminalState.NON_TERMINAL),

    /** Some tasks are scheduled or running, some may be pending, some may be finished.
     * 作业创建完之后，就开始申请资源，申请必要的资源成功，并且
     * 向TaskManager调度Task执行成功，就进入Running状态。在Running状
     * 态下，对于流作业而言，作业的所有Task都在执行，对于批作业而
     * 言，可能部分Task被调度执行，其他的Task在等待调度。
     *
     * */
    RUNNING(TerminalState.NON_TERMINAL),

    /** The job has failed and is currently waiting for the cleanup to complete.
     * 作业执行失败，可能是因为作业代码中抛出的异常没有处理，或
     * 者是资源不够，作业进入Failing状态，等待资源清理。
     * */
    FAILING(TerminalState.NON_TERMINAL),

    /** The job has failed with a non-recoverable task failure.
     *
     * 如果作业进入Failing状态的异常达到了作业自动重启次数的限
     * 制，或者其他更严重的异常导致作业无法自动恢复，此时作业就会进
     * 入Failed的状态。
     * */
    FAILED(TerminalState.GLOBALLY),

    /** Job is being cancelled.
     * 调用Flink的接口或者在WebUI上对作业进行取消，首先会进入此
     * 状态，此状态下，首先会清理资源，等待作业的Task停止。
     * */
    CANCELLING(TerminalState.NON_TERMINAL),

    /** Job has been cancelled.
     * 当所有的资源清理完毕，作业完全停止执行后，进入Canceled状
     * 态，此状态一般是用户主动停止作业
     * */
    CANCELED(TerminalState.GLOBALLY),

    /** All of the job's tasks have successfully finished.
     * 作业的所有Task都成功地执行完毕后，作业退出，进入Finished状态
     * */
    FINISHED(TerminalState.GLOBALLY),

    /** The job is currently undergoing a reset and total restart.
     * 当作业执行出错，需要重启作业的时候，首先进入Failing状态，
     * 如果可以重启则进入Restarting状态，作业进行重置，释放所申请的
     * 所有资源，包含内存、Slot等，开始重新调度作业的执行。
     *
     * */
    RESTARTING(TerminalState.NON_TERMINAL),

    /**
     * The job has been suspended which means that it has been stopped but not been removed from a
     * potential HA job store.
     * 挂起作业之前，取消Running状态的Task，Task进入Canceled状
     * 态，销毁掉通信组件等其他组件，但是仍然保留ExecutionGraph，等
     * 待恢复。Suspended状态一般在HA下主JobManager宕机、备JobManager
     * 接管继续执行时，恢复ExecutionGraph
     */
    SUSPENDED(TerminalState.LOCALLY),

    /** The job is currently reconciling and waits for task execution report to recover state. */
    RECONCILING(TerminalState.NON_TERMINAL);

    // --------------------------------------------------------------------------------------------

    private enum TerminalState {
        NON_TERMINAL,
        LOCALLY,
        GLOBALLY
    }

    private final TerminalState terminalState;

    JobStatus(TerminalState terminalState) {
        this.terminalState = terminalState;
    }

    /**
     * Checks whether this state is <i>globally terminal</i>. A globally terminal job is complete
     * and cannot fail any more and will not be restarted or recovered by another standby master
     * node.
     *
     * <p>When a globally terminal state has been reached, all recovery data for the job is dropped
     * from the high-availability services.
     *
     * @return True, if this job status is globally terminal, false otherwise.
     */
    public boolean isGloballyTerminalState() {
        return terminalState == TerminalState.GLOBALLY;
    }

    /**
     * Checks whether this state is <i>locally terminal</i>. Locally terminal refers to the state of
     * a job's execution graph within an executing JobManager. If the execution graph is locally
     * terminal, the JobManager will not continue executing or recovering the job.
     *
     * <p>The only state that is locally terminal, but not globally terminal is {@link #SUSPENDED},
     * which is typically entered when the executing JobManager loses its leader status.
     *
     * @return True, if this job status is terminal, false otherwise.
     */
    public boolean isTerminalState() {
        return terminalState != TerminalState.NON_TERMINAL;
    }
}
