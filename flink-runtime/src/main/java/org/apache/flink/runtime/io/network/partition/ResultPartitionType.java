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

/** Type of a result partition.
 * <p>执行模式的不同决定了数据交换行为的不同，为了能够实现不同
 * 的数据交换行为，Flink在ResultPartitionType中定义了4种类型的数
 * 据分区模式，与执行模式一起完成批流在数据交换层面的统一</p>
 * */
public enum ResultPartitionType {

    /**
     * Blocking partitions represent blocking data exchanges, where the data stream is first fully
     * produced and then consumed. This is an option that is only applicable to bounded streams and
     * can be used in bounded stream runtime and recovery.
     *
     * <p>Blocking partitions can be consumed multiple times and concurrently.
     *
     * <p>The partition is not automatically released after being consumed (like for example the
     * {@link #PIPELINED} partitions), but only released through the scheduler, when it determines
     * that the partition is no longer needed.
     *
     * <p>
     * BLOCKING类型的数据分区会等待数据完全处理完毕，然后才会交
     * 给下游进行处理，在上游处理完毕之前，不会与下游进行数据交换。
     * 该类型的数据分区可以被多次消费，也可以并发消费。被消费完毕之
     * 后不会自动释放，而是等待调度器来判断该数据分区无人再消费之
     * 后，由调度器发出销毁指令。
     * 该模式适用于批处理，不提供反压流控能力。
     * </p>
     */
    BLOCKING(true, false, false, ConsumingConstraint.BLOCKING, ReleaseBy.SCHEDULER),

    /**
     * BLOCKING_PERSISTENT partitions are similar to {@link #BLOCKING} partitions, but have a
     * user-specified life cycle.
     *
     * <p>BLOCKING_PERSISTENT partitions are dropped upon explicit API calls to the JobManager or
     * ResourceManager, rather than by the scheduler.
     *
     * <p>Otherwise, the partition may only be dropped by safety-nets during failure handling
     * scenarios, like when the TaskManager exits or when the TaskManager loses connection to
     * JobManager / ResourceManager for too long.
     *
     * <p>BLOCKING_PERSISTENT类型的数据分区类似于BLOCKING，但是其生
     * 命周期由用户指定。调用JobManager或者ResourceManager API进行销
     * 毁，而不是由调度器控制。</p>
     */
    BLOCKING_PERSISTENT(true, false, true, ConsumingConstraint.BLOCKING, ReleaseBy.SCHEDULER),

    /**
     * A pipelined streaming data exchange. This is applicable to both bounded and unbounded
     * streams.
     *
     * <p>Pipelined results can be consumed only once by a single consumer and are automatically
     * disposed when the stream has been consumed.
     *
     * <p>This result partition type may keep an arbitrary amount of data in-flight, in contrast to
     * the {@link #PIPELINED_BOUNDED} variant.
     *
     * <p>PIPELINED（流水线）式数据交换适用于流计算和批处理。
     * 数据处理结果只能被1个消费者（下游的算子）消费1次，当数据
     * 被消费之后即自动销毁。PIPELINED分区可能会保存一定数据的数据，
     * 与PIPELINED_BOUNDED相反。此结果分区类型可以在运行中保留任意数
     * 量的数据。当数据量太大内存无法容纳时，可以写入磁盘中。</p>
     */
    PIPELINED(false, false, false, ConsumingConstraint.MUST_BE_PIPELINED, ReleaseBy.UPSTREAM),

    /**
     * Pipelined partitions with a bounded (local) buffer pool.
     *
     * <p>For streaming jobs, a fixed limit on the buffer pool size should help avoid that too much
     * data is being buffered and checkpoint barriers are delayed. In contrast to limiting the
     * overall network buffer pool size, this, however, still allows to be flexible with regards to
     * the total number of partitions by selecting an appropriately big network buffer pool size.
     *
     * <p>For batch jobs, it will be best to keep this unlimited ({@link #PIPELINED}) since there
     * are no checkpoint barriers.
     *
     * <p>PIPELINED_BOUNDED 是 PIPELINED 带 有 一 个 有 限 大 小 的 本 地 缓 冲
     * 池。
     * 对于流计算作业来说，固定大小的缓冲池可以避免缓冲太多的数
     * 据和检查点延迟太久。不同于限制整体网络缓冲池的大小，该模式下
     * 允许根据分区的总数弹性地选择网络缓冲池的大小。
     * 对于批处理作业来说，最好使用无限制的PIPELINED数据交换模
     * 式，因为在批处理模式下没有CheckpointBarrier，其实现Exactly-
     * Once与流计算不同</p>
     */
    PIPELINED_BOUNDED(
            false, true, false, ConsumingConstraint.MUST_BE_PIPELINED, ReleaseBy.UPSTREAM),

    /**
     * Pipelined partitions with a bounded (local) buffer pool to support downstream task to
     * continue consuming data after reconnection in Approximate Local-Recovery.
     *
     * <p>Pipelined results can be consumed only once by a single consumer at one time. {@link
     * #PIPELINED_APPROXIMATE} is different from {@link #PIPELINED} and {@link #PIPELINED_BOUNDED}
     * in that {@link #PIPELINED_APPROXIMATE} partition can be reconnected after down stream task
     * fails.
     */
    PIPELINED_APPROXIMATE(
            false, true, false, ConsumingConstraint.CAN_BE_PIPELINED, ReleaseBy.UPSTREAM),

    /**
     * Hybrid partitions with a bounded (local) buffer pool to support downstream task to
     * simultaneous reading and writing shuffle data.
     *
     * <p>Hybrid partitions can be consumed any time, whether fully produced or not.
     *
     * <p>HYBRID_FULL partitions can be consumed repeatedly, but it does not support concurrent
     * consumption. So re-consumable is false, but double calculation can be avoided during
     * failover.
     */
    // TODO support re-consumable for HYBRID_FULL resultPartitionType.
    HYBRID_FULL(false, false, false, ConsumingConstraint.CAN_BE_PIPELINED, ReleaseBy.SCHEDULER),

    /**
     * HYBRID_SELECTIVE partitions are similar to {@link #HYBRID_FULL} partitions, but it cannot be
     * consumed repeatedly.
     */
    HYBRID_SELECTIVE(
            false, false, false, ConsumingConstraint.CAN_BE_PIPELINED, ReleaseBy.SCHEDULER);

    /**
     * Can this result partition be consumed by multiple downstream consumers for multiple times.
     */
    private final boolean isReconsumable;

    /** Does this partition use a limited number of (network) buffers?
     * 用于设置分区是否使用有限个network buffer */
    private final boolean isBounded;

    /** This partition will not be released after consuming if 'isPersistent' is true. 当其值为true时，
     * 分区被消费后不会立即被释放 */
    private final boolean isPersistent;

    private final ConsumingConstraint consumingConstraint;

    private final ReleaseBy releaseBy;

    /** ConsumingConstraint indicates when can the downstream consume the upstream. */
    private enum ConsumingConstraint {
        /** Upstream must be finished before downstream consume. */
        BLOCKING,
        /** Downstream can consume while upstream is running. */
        CAN_BE_PIPELINED,
        /** Downstream must consume while upstream is running. */
        MUST_BE_PIPELINED
    }

    /**
     * ReleaseBy indicates who is responsible for releasing the result partition.
     *
     * <p>Attention: This may only be a short-term solution to deal with the partition release
     * logic. We can discuss the issue of unifying the partition release logic in FLINK-27948. Once
     * the ticket is resolved, we can remove the enumeration here.
     */
    private enum ReleaseBy {
        UPSTREAM,
        SCHEDULER
    }

    /** Specifies the behaviour of an intermediate result partition at runtime. */
    ResultPartitionType(
            boolean isReconsumable,
            boolean isBounded,
            boolean isPersistent,
            ConsumingConstraint consumingConstraint,
            ReleaseBy releaseBy) {
        this.isReconsumable = isReconsumable;
        this.isBounded = isBounded;
        this.isPersistent = isPersistent;
        this.consumingConstraint = consumingConstraint;
        this.releaseBy = releaseBy;
    }

    /** return if this partition's upstream and downstream must be scheduled in the same time. */
    public boolean mustBePipelinedConsumed() {
        return consumingConstraint == ConsumingConstraint.MUST_BE_PIPELINED;
    }

    /** return if this partition's upstream and downstream support scheduling in the same time. */
    public boolean canBePipelinedConsumed() {
        return consumingConstraint == ConsumingConstraint.CAN_BE_PIPELINED
                || consumingConstraint == ConsumingConstraint.MUST_BE_PIPELINED;
    }

    public boolean isReleaseByScheduler() {
        return releaseBy == ReleaseBy.SCHEDULER;
    }

    public boolean isReleaseByUpstream() {
        return releaseBy == ReleaseBy.UPSTREAM;
    }

    /**
     * {@link #isBlockingOrBlockingPersistentResultPartition()} is used to judge whether it is the
     * specified {@link #BLOCKING} or {@link #BLOCKING_PERSISTENT} resultPartitionType.
     *
     * <p>this method suitable for judgment conditions related to the specific implementation of
     * {@link ResultPartitionType}.
     *
     * <p>this method not related to data consumption and partition release. As for the logic
     * related to partition release, use {@link #isReleaseByScheduler()} instead, and as consume
     * type, use {@link #mustBePipelinedConsumed()} or {@link #canBePipelinedConsumed()} instead.
     */
    public boolean isBlockingOrBlockingPersistentResultPartition() {
        return this == BLOCKING || this == BLOCKING_PERSISTENT;
    }

    /**
     * {@link #isPipelinedOrPipelinedBoundedResultPartition()} is used to judge whether it is the
     * specified {@link #PIPELINED} or {@link #PIPELINED_BOUNDED} resultPartitionType.
     *
     * <p>This method suitable for judgment conditions related to the specific implementation of
     * {@link ResultPartitionType}.
     *
     * <p>This method not related to data consumption and partition release. As for the logic
     * related to partition release, use {@link #isReleaseByScheduler()} instead, and as consume
     * type, use {@link #mustBePipelinedConsumed()} or {@link #canBePipelinedConsumed()} instead.
     */
    public boolean isPipelinedOrPipelinedBoundedResultPartition() {
        return this == PIPELINED || this == PIPELINED_BOUNDED;
    }

    /**
     * Whether this partition uses a limited number of (network) buffers or not.
     *
     * @return <tt>true</tt> if the number of buffers should be bound to some limit
     */
    public boolean isBounded() {
        return isBounded;
    }

    public boolean isPersistent() {
        return isPersistent;
    }

    public boolean supportCompression() {
        return isBlockingOrBlockingPersistentResultPartition()
                || this == HYBRID_FULL
                || this == HYBRID_SELECTIVE;
    }

    public boolean isReconsumable() {
        return isReconsumable;
    }
}
