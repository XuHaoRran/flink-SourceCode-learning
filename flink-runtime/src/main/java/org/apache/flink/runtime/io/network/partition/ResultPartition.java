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

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.SimpleCounter;
import org.apache.flink.runtime.executiongraph.IntermediateResultPartition;
import org.apache.flink.runtime.io.network.api.EndOfData;
import org.apache.flink.runtime.io.network.api.StopMode;
import org.apache.flink.runtime.io.network.api.writer.ResultPartitionWriter;
import org.apache.flink.runtime.io.network.buffer.Buffer;
import org.apache.flink.runtime.io.network.buffer.BufferCompressor;
import org.apache.flink.runtime.io.network.buffer.BufferPool;
import org.apache.flink.runtime.io.network.partition.consumer.LocalInputChannel;
import org.apache.flink.runtime.io.network.partition.consumer.RemoteInputChannel;
import org.apache.flink.runtime.jobgraph.DistributionPattern;
import org.apache.flink.runtime.metrics.groups.TaskIOMetricGroup;
import org.apache.flink.runtime.taskexecutor.TaskExecutor;
import org.apache.flink.util.Preconditions;
import org.apache.flink.util.function.SupplierWithException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.flink.util.Preconditions.checkNotNull;
import static org.apache.flink.util.Preconditions.checkState;

/**
 * A result partition for data produced by a single task.
 * <p>结果分区在Flink中叫作ResultPartition，用来表示作业的单个
 * Task 产 生 的 数 据 。 ResultPartition 是 运 行 时 的 实 体 ， 与
 * ExecutionGraph 中 的 中 间 结 果 分 区 对 应
 * （ IntermediateResultPartition ） ， 一 个 ResultPartition 是 一 组
 * Buffer 实 例 ， ResultPartition 由 ResultSubPartition 组 成 ，
 * ResultSubPartition用来进一步将ResultPartition进行切分，切分成
 * 多少个ResultSubPartition取决于直接下游子任务的并行度和数据分
 * 发模式。
 * <p>下游子任务消费上游子任务产生的ResultPartition，在实际请求
 * 的 时 候 ， 是 向 上 游 请 求 ResultSubPartition ， 并 不 是 请 求 整 个
 * ResultPartition，请求的方式有远程请求和本地请求两种。
 * <p>ResultPartition 有 4 种 类 型 ： Blocking 、
 * Blocking_persisted、Pipeline_bounded、Pipelined。对于流上的计
 * 算而言，ResultPartition在作业的执行过程中会一直存在，但是对于
 * 批处理而言，上游Task输出ResultPartition，下游Task消费上游的
 * ResultPartition，消费完毕之后，上游的ResultPartition就没有什
 * 么 用 了 ， 需 要 进 行 资 源 回 收 ， 所 以 Flink 增 加 了 新 的
 * ReleaseOnConsumptionResultPartition
 *
 *
 *
 * <p>This class is the runtime part of a logical {@link IntermediateResultPartition}. Essentially,
 * a result partition is a collection of {@link Buffer} instances. The buffers are organized in one
 * or more {@link ResultSubpartition} instances or in a joint structure which further partition the
 * data depending on the number of consuming tasks and the data {@link DistributionPattern}.
 *
 * <p>Tasks, which consume a result partition have to request one of its subpartitions. The request
 * happens either remotely (see {@link RemoteInputChannel}) or locally (see {@link
 * LocalInputChannel})
 *
 * <h2>Life-cycle</h2>
 *
 * <p>The life-cycle of each result partition has three (possibly overlapping) phases:
 *
 * <ol>
 *   <li><strong>Produce</strong>:
 *   <li><strong>Consume</strong>:
 *   <li><strong>Release</strong>:
 * </ol>
 *
 * <h2>Buffer management</h2>
 *
 * <h2>State management</h2>
 *
 *
 */
public abstract class ResultPartition implements ResultPartitionWriter {

    protected static final Logger LOG = LoggerFactory.getLogger(ResultPartition.class);
    // 任务名称
    private final String owningTaskName;
    // 分区的唯一标识
    private final int partitionIndex;

    protected final ResultPartitionID partitionId;

    /** Type of this partition. Defines the concrete subpartition implementation to use. */
    protected final ResultPartitionType partitionType;
    // 分区管理器
    protected final ResultPartitionManager partitionManager;

    protected final int numSubpartitions;

    private final int numTargetKeyGroups;

    // - Runtime state --------------------------------------------------------

    private final AtomicBoolean isReleased = new AtomicBoolean();
    // 内存池
    protected BufferPool bufferPool;

    private boolean isFinished;

    private volatile Throwable cause;

    private final SupplierWithException<BufferPool, IOException> bufferPoolFactory;

    /** Used to compress buffer to reduce IO. */
    @Nullable protected final BufferCompressor bufferCompressor;

    protected Counter numBytesOut = new SimpleCounter();

    protected Counter numBuffersOut = new SimpleCounter();

    /**
     * The difference with {@link #numBytesOut} : numBytesProduced represents the number of bytes
     * actually produced, and numBytesOut represents the number of bytes sent to downstream tasks.
     * In unicast scenarios, these two values should be equal. In broadcast scenarios, numBytesOut
     * should be (N * numBytesProduced), where N refers to the number of subpartitions.
     */
    protected Counter numBytesProduced = new SimpleCounter();

    public ResultPartition(
            String owningTaskName,
            int partitionIndex,
            ResultPartitionID partitionId,
            ResultPartitionType partitionType,
            int numSubpartitions,
            int numTargetKeyGroups,
            ResultPartitionManager partitionManager,
            @Nullable BufferCompressor bufferCompressor,
            SupplierWithException<BufferPool, IOException> bufferPoolFactory) {

        this.owningTaskName = checkNotNull(owningTaskName);
        Preconditions.checkArgument(0 <= partitionIndex, "The partition index must be positive.");
        this.partitionIndex = partitionIndex;
        this.partitionId = checkNotNull(partitionId);
        this.partitionType = checkNotNull(partitionType);
        this.numSubpartitions = numSubpartitions;
        this.numTargetKeyGroups = numTargetKeyGroups;
        this.partitionManager = checkNotNull(partitionManager);
        this.bufferCompressor = bufferCompressor;
        this.bufferPoolFactory = bufferPoolFactory;
    }

    /**
     * Registers a buffer pool with this result partition.
     *
     * <p>There is one pool for each result partition, which is shared by all its sub partitions.
     *
     * <p>The pool is registered with the partition *after* it as been constructed in order to
     * conform to the life-cycle of task registrations in the {@link TaskExecutor}.
     */
    @Override
    public void setup() throws IOException {
        checkState(
                this.bufferPool == null,
                "Bug in result partition setup logic: Already registered buffer pool.");

        this.bufferPool = checkNotNull(bufferPoolFactory.get());

        setupInternal();
        // 在分区管理器中注册该分区
        partitionManager.registerResultPartition(this);
    }

    /** Do the subclass's own setup operation. */
    protected abstract void setupInternal() throws IOException;

    public String getOwningTaskName() {
        return owningTaskName;
    }

    @Override
    public ResultPartitionID getPartitionId() {
        return partitionId;
    }

    public int getPartitionIndex() {
        return partitionIndex;
    }

    @Override
    public int getNumberOfSubpartitions() {
        return numSubpartitions;
    }

    public BufferPool getBufferPool() {
        return bufferPool;
    }

    /** Returns the total number of queued buffers of all subpartitions. */
    public abstract int getNumberOfQueuedBuffers();

    /** Returns the total size in bytes of queued buffers of all subpartitions. */
    public abstract long getSizeOfQueuedBuffersUnsafe();

    /** Returns the number of queued buffers of the given target subpartition. */
    public abstract int getNumberOfQueuedBuffers(int targetSubpartition);

    public void setMaxOverdraftBuffersPerGate(int maxOverdraftBuffersPerGate) {
        this.bufferPool.setMaxOverdraftBuffersPerGate(maxOverdraftBuffersPerGate);
    }

    /**
     * Returns the type of this result partition.
     *
     * @return result partition type
     */
    public ResultPartitionType getPartitionType() {
        return partitionType;
    }

    // ------------------------------------------------------------------------

    @Override
    public void notifyEndOfData(StopMode mode) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> getAllDataProcessedFuture() {
        throw new UnsupportedOperationException();
    }

    /**
     * The subpartition notifies that the corresponding downstream task have processed all the user
     * records.
     *
     * @see EndOfData
     * @param subpartition The index of the subpartition sending the notification.
     */
    public void onSubpartitionAllDataProcessed(int subpartition) {}

    /**
     * Finishes the result partition.
     *
     * <p>After this operation, it is not possible to add further data to the result partition.
     *
     * <p>For BLOCKING results, this will trigger the deployment of consuming tasks.
     */
    @Override
    public void finish() throws IOException {
        checkInProduceState();

        isFinished = true;
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

    public void release() {
        release(null);
    }

    @Override
    public void release(Throwable cause) {
        if (isReleased.compareAndSet(false, true)) {
            LOG.debug("{}: Releasing {}.", owningTaskName, this);

            // Set the error cause
            if (cause != null) {
                this.cause = cause;
            }

            releaseInternal();
        }
    }

    /** Releases all produced data including both those stored in memory and persisted on disk. */
    protected abstract void releaseInternal();

    private void closeBufferPool() {
        if (bufferPool != null) {
            bufferPool.lazyDestroy();
        }
    }

    @Override
    public void close() {
        closeBufferPool();
    }

    @Override
    public void fail(@Nullable Throwable throwable) {
        // the task canceler thread will call this method to early release the output buffer pool
        closeBufferPool();
        partitionManager.releasePartition(partitionId, throwable);
    }

    public Throwable getFailureCause() {
        return cause;
    }

    @Override
    public int getNumTargetKeyGroups() {
        return numTargetKeyGroups;
    }

    @Override
    public void setMetricGroup(TaskIOMetricGroup metrics) {
        numBytesOut = metrics.getNumBytesOutCounter();
        numBuffersOut = metrics.getNumBuffersOutCounter();
        metrics.registerNumBytesProducedCounterForPartition(
                partitionId.getPartitionId(), numBytesProduced);
    }

    /**
     * Whether this partition is released.
     *
     * <p>A partition is released when each subpartition is either consumed and communication is
     * closed by consumer or failed. A partition is also released if task is cancelled.
     */
    @Override
    public boolean isReleased() {
        return isReleased.get();
    }

    @Override
    public CompletableFuture<?> getAvailableFuture() {
        return bufferPool.getAvailableFuture();
    }

    @Override
    public String toString() {
        return "ResultPartition "
                + partitionId.toString()
                + " ["
                + partitionType
                + ", "
                + numSubpartitions
                + " subpartitions]";
    }

    // ------------------------------------------------------------------------

    /** Notification when a subpartition is released. */
    void onConsumedSubpartition(int subpartitionIndex) {

        if (isReleased.get()) {
            return;
        }

        LOG.debug(
                "{}: Received release notification for subpartition {}.", this, subpartitionIndex);
    }

    // ------------------------------------------------------------------------

    protected void checkInProduceState() throws IllegalStateException {
        checkState(!isFinished, "Partition already finished.");
    }

    @VisibleForTesting
    public ResultPartitionManager getPartitionManager() {
        return partitionManager;
    }

    /**
     * Whether the buffer can be compressed or not. Note that event is not compressed because it is
     * usually small and the size can become even larger after compression.
     */
    protected boolean canBeCompressed(Buffer buffer) {
        return bufferCompressor != null && buffer.isBuffer() && buffer.readableBytes() > 0;
    }
}
