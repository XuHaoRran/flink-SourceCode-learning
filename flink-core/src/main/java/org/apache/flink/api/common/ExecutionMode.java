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

import org.apache.flink.annotation.Public;

/**
 * The execution mode specifies how a batch program is executed in terms of data exchange:
 * pipelining or batched.
 */
@Public
public enum ExecutionMode {

    /**
     * <p>此模式以流水线方式（包括Shuffle和广播数据）
     * 执行作业，但流水线可能会出现死锁的数据交换除外。如果可能会出
     * 现数据交换死锁，则数据交换以Batch方式执行。
     * <p>当数据流被多个下游分支消费处理，处理后的结果再进行Join
     * 时，如果以Pipelined模式运行，则可能出现数据交换死锁。
     *
     * Executes the program in a pipelined fashion (including shuffles and broadcasts), except for
     * data exchanges that are susceptible to deadlocks when pipelining. These data exchanges are
     * performed in a batch manner.
     *
     * <p>An example of situations that are susceptible to deadlocks (when executed in a pipelined
     * manner) are data flows that branch (one data set consumed by multiple operations) and re-join
     * later:
     *
     * <pre>{@code
     * DataSet data = ...;
     * DataSet mapped1 = data.map(new MyMapper());
     * DataSet mapped2 = data.map(new AnotherMapper());
     * mapped1.join(mapped2).where(...).equalTo(...);
     * }</pre>
     */
    PIPELINED,

    /**
     * Executes the program in a pipelined fashion (including shuffles and broadcasts),
     * <strong>including</strong> data exchanges that are susceptible to deadlocks when executed via
     * pipelining.
     *
     * <p>Usually, {@link #PIPELINED} is the preferable option, which pipelines most data exchanges
     * and only uses batch data exchanges in situations that are susceptible to deadlocks.
     *
     * <p>This option should only be used with care and only in situations where the programmer is
     * sure that the program is safe for full pipelining and that Flink was too conservative when
     * choosing the batch exchange at a certain point.
     *
     * <p>一般情况下，Pipelined模式是优先选择，确保不会出现数据死锁的情况下才会使用Pipelined_Forced模式
     */
    PIPELINED_FORCED,

    //	This is for later, we are missing a bit of infrastructure for this.
    //	/**
    //	 * The execution mode starts executing the program in a pipelined fashion
    //	 * (except for deadlock prone situations), similar to the {@link #PIPELINED}
    //	 * option. In the case of a task failure, re-execution happens in a batched
    //	 * mode, as defined for the {@link #BATCH} option.
    //	 */
    //	PIPELINED_WITH_BATCH_FALLBACK,

    /**
     * This mode executes all shuffles and broadcasts in a batch fashion, while pipelining data
     * between operations that exchange data only locally between one producer and one consumer.
     */
    BATCH,

    /**
     * This mode executes the program in a strict batch way, including all points where data is
     * forwarded locally from one producer to one consumer. This mode is typically more expensive to
     * execute than the {@link #BATCH} mode. It does guarantee that no successive operations are
     * ever executed concurrently.
     */
    BATCH_FORCED
}
