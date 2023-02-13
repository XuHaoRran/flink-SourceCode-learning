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

package org.apache.flink.runtime.throwable;

/** Enum for the classification of {@link Throwable} objects into failure/recovery classes. */
public enum ThrowableType {

    /**
     * This indicates error that would not succeed even with retry, such as DivideZeroException. No
     * recovery attempt should happen for such an error. Instead, the job should fail immediately.
     *
     * 不可恢复的错误。此类错误意味着即便是重启也无法恢复作业到正常状态，一旦发生此类错误，则作业执
     * 行失败，直接退出作业执行。
     *
     */
    NonRecoverableError,

    /** Data consumption error, which indicates that we should revoke the producer.
     * 分区数据不可访问错误。下游Task无法读取上游Task的产生的数据，需要重启上游的Task。
     *
     * */
    PartitionDataMissingError,

    /**
     * This indicates an error related to the running environment, such as hardware error, service
     * issue, in which case we should consider blacklisting the machine.
     * 环境的错误，问题本身来自机器硬件、外部服务等。这种错误需要在调度策略上进行改进，如使用黑名单机
     * 制，排除有问题的机器、服务，避免将失败的Task重新调度到这些机器上。
     *
     */
    EnvironmentError,

    /** This indicates a problem that is recoverable.
     * 可恢复的错误
     *
     * */
    RecoverableError
}
