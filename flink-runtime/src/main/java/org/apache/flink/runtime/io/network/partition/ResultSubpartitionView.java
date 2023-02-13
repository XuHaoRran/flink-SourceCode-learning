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

package org.apache.flink.runtime.io.network.partition;

import org.apache.flink.runtime.io.network.buffer.Buffer;
import org.apache.flink.runtime.io.network.partition.ResultSubpartition.BufferAndBacklog;

import javax.annotation.Nullable;

import java.io.IOException;

import static org.apache.flink.util.Preconditions.checkArgument;

/** A view to consume a {@link ResultSubpartition} instance. 下游要读取上游的数据时，会给上游发送请求，
 * 此时上游会先创建一个试图对象去读取数据，这个对象对应的类就是ResultSubpartitionView，利用这个试图对象读取数据后，
 * 再传递给下游。其中getNextBuffer是最重要的方法，用来获取Buffer
 *
 * <p>Flink中设计了两种不同类型的结果子分区，其存储机制不同，对
 * 应于结果子分区的不同类型，定义了两个结果子分区视图</p>
 * <ul>
 *     <li>PipelinedSubPartitionView ： 用 来 读 取
 * PipelinedSubPartition中的数据。
 *     <li>BoundedBlockingSubPartitionReader ： 用 来 读 取
 * BoundedBlockingSubPartition中的数据
 * </ul>
 * */
public interface ResultSubpartitionView {

    /**
     * Returns the next {@link Buffer} instance of this queue iterator.
     * 获取下一个BufferAndBacklog对象
     * <p>If there is currently no instance available, it will return <code>null</code>. This might
     * happen for example when a pipelined queue producer is slower than the consumer or a spilled
     * queue needs to read in more data.
     *
     * <p><strong>Important</strong>: The consumer has to make sure that each buffer instance will
     * eventually be recycled with {@link Buffer#recycleBuffer()} after it has been consumed.
     */
    @Nullable
    BufferAndBacklog getNextBuffer() throws IOException;

    /**
     * 通知数据可被消费
     */
    void notifyDataAvailable();

    default void notifyPriorityEvent(int priorityBufferNumber) {}

    void releaseAllResources() throws IOException;

    boolean isReleased();

    void resumeConsumption();

    void acknowledgeAllDataProcessed();

    /**
     * {@link ResultSubpartitionView} can decide whether the failure cause should be reported to
     * consumer as failure (primary failure) or {@link ProducerFailedException} (secondary failure).
     * Secondary failure can be reported only if producer (upstream task) is guaranteed to failover.
     *
     * <p><strong>BEWARE:</strong> Incorrectly reporting failure cause as primary failure, can hide
     * the root cause of the failure from the user.
     */
    Throwable getFailureCause();

    AvailabilityWithBacklog getAvailabilityAndBacklog(int numCreditsAvailable);

    int unsynchronizedGetNumberOfQueuedBuffers();

    int getNumberOfQueuedBuffers();

    void notifyNewBufferSize(int newBufferSize);

    /**
     * Availability of the {@link ResultSubpartitionView} and the backlog in the corresponding
     * {@link ResultSubpartition}.
     */
    class AvailabilityWithBacklog {

        private final boolean isAvailable;

        private final int backlog;

        public AvailabilityWithBacklog(boolean isAvailable, int backlog) {
            checkArgument(backlog >= 0, "Backlog must be non-negative.");

            this.isAvailable = isAvailable;
            this.backlog = backlog;
        }

        public boolean isAvailable() {
            return isAvailable;
        }

        public int getBacklog() {
            return backlog;
        }
    }
}
