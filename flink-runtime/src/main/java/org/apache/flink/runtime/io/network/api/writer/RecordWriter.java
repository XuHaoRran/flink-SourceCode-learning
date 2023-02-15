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

package org.apache.flink.runtime.io.network.api.writer;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.configuration.ExecutionOptions;
import org.apache.flink.core.io.IOReadableWritable;
import org.apache.flink.core.memory.DataOutputSerializer;
import org.apache.flink.runtime.checkpoint.CheckpointException;
import org.apache.flink.runtime.event.AbstractEvent;
import org.apache.flink.runtime.io.AvailabilityProvider;
import org.apache.flink.runtime.metrics.groups.TaskIOMetricGroup;
import org.apache.flink.util.XORShiftRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static org.apache.flink.util.Preconditions.checkArgument;

/**
 * An abstract record-oriented runtime result writer.
 *
 * <p>The RecordWriter wraps the runtime's {@link ResultPartitionWriter} and takes care of channel
 * selection and serializing records into bytes.
 *
 * <p>一个任务中都可能有一个或多个链接在一起的算子，其中最后一个算子的输出会依赖RecordWriter类型的字段，RecordWriter类中依赖了
 * ReusltPartitionWriter类型的tragetPartition字段，由此可以将数据写入ResultPartitionWriter
 *
 * <p>RecordWriter负责将Task处理的数据输出，然后下游Task就可以
 * 继续处理了。RecordWriter面向的是StreamRecord，直接处理算子的
 * 输出结果。ResultPatitionWriter面向的是Buffer，起到承上启下的
 * 作用。RecordWriter比ResultPartitionWriter的层级要高，底层依赖
 * 于ResultPar titionWriter。
 * <p>在 DataStream API 中 介 绍 过 数 据 分 区 ， 其 中 最 核 心 的 抽 象 是
 * ChannelSelector，在RecordWriter中实现了数据分区语义，将开发时
 * 对 数 据 分 区 API 的 调 用 转 换 成 了 实 际 的 物 理 操 作 ， 如
 * DataStream#shuffle（）等。
 * <p>最底层内存抽象是MemorySegment，用于数据传输的是Buffer，那
 * 么，承上启下对接、从Java对象转为Buffer的中间对象是什么呢?是
 * StreamRecord。
 * <p>RecordWriter 类 负 责 将 StreamRecord 进 行 序 列 化 ， 调 用
 * SpaningRecordSerializer，再调用BufferBuilder写入MemorySegment
 * 中（每个Task都有自己的LocalBufferPool，LocalBufferPool中包含
 * 了多个MemorySegment）。
 * <p>哪些数据会被会序列化?
 * <ul>
 *  <li>数据元素StreamElement：StreamElement共有4种。
 *  <li>事件Event：Flink内部的系统事件，如CheckpointBarrier事件等
 * </ul>
 * <p>Flink RecordWriter提供两种写入方式：单播和广播
 *
 * <p>单播:根据ChannelSelector，对数据流中的每一条数据记录进行选路，
 * 有 选 择 地 写 入 一 个 输 出 通 道 的 ResultSubPartition 中 ， 适 用 于 非
 * BroadcastPartition。如果在开发的时候没有使用Partition，默认会
 * 使用RoundRobinChannelSelector，使用RoundRobin算法选择输出通道
 * 循环写入本地输出通道对应的ResultPartition，发送到下游Task
 *
 * <p>概念上来说，广播就是向下游所有的Task发送相同的数据，在所
 * 有的ResultSubPartition中写入N份相同数据。但是在实际实现时，同
 * 时写入N份重复的数据是资源浪费，所以对于广播类型的输出，只会写
 * 入编号为0的ResultSubPartition中，下游Task对于广播类型的数据，
 * 都会从编号为0的ResultSubPartition中获取数据
 * @param <T> the type of the record that can be emitted with this record writer
 */
public abstract class RecordWriter<T extends IOReadableWritable> implements AvailabilityProvider {

    /** Default name for the output flush thread, if no name with a task reference is given. */
    @VisibleForTesting
    public static final String DEFAULT_OUTPUT_FLUSH_THREAD_NAME = "OutputFlusher";

    private static final Logger LOG = LoggerFactory.getLogger(RecordWriter.class);
    // 用于将数据真正写入分区
    protected final ResultPartitionWriter targetPartition;
    // 子分区的个数
    protected final int numberOfChannels;
    // 用于将数据序列化
    protected final DataOutputSerializer serializer;

    protected final Random rng = new XORShiftRandom();

    protected final boolean flushAlways;

    /** The thread that periodically flushes the output, to give an upper latency bound. */
    @Nullable private final OutputFlusher outputFlusher;

    /**
     * To avoid synchronization overhead on the critical path, best-effort error tracking is enough
     * here.
     */
    private Throwable flusherException;

    private volatile Throwable volatileFlusherException;
    private int volatileFlusherExceptionCheckSkipCount;
    private static final int VOLATILE_FLUSHER_EXCEPTION_MAX_CHECK_SKIP_COUNT = 100;

    RecordWriter(ResultPartitionWriter writer, long timeout, String taskName) {
        this.targetPartition = writer;
        this.numberOfChannels = writer.getNumberOfSubpartitions();

        this.serializer = new DataOutputSerializer(128);

        checkArgument(timeout >= ExecutionOptions.DISABLED_NETWORK_BUFFER_TIMEOUT);
        this.flushAlways = (timeout == ExecutionOptions.FLUSH_AFTER_EVERY_RECORD);
        if (timeout == ExecutionOptions.DISABLED_NETWORK_BUFFER_TIMEOUT
                || timeout == ExecutionOptions.FLUSH_AFTER_EVERY_RECORD) {
            outputFlusher = null;
        } else {
            String threadName =
                    taskName == null
                            ? DEFAULT_OUTPUT_FLUSH_THREAD_NAME
                            : DEFAULT_OUTPUT_FLUSH_THREAD_NAME + " for " + taskName;

            outputFlusher = new OutputFlusher(threadName, timeout);
            outputFlusher.start();
        }
    }
    // 将数据发送到分区
    protected void emit(T record, int targetSubpartition) throws IOException {
        checkErroneous();

        targetPartition.emitRecord(serializeRecord(serializer, record), targetSubpartition);

        /**
         * 如果是立即刷新，则相当于一条记录向下游推送一次，延迟最
         * 低，但是吞吐量会下降，Flink默认的做法是单独启动一个线程，默认
         * 100ms刷新一次，本质上是一种mini-batch，这种mini-batch只是为了
         * 增大吞吐量，与Spark的mini-batch处理不是一个概念
         *
         * ResultPartition遍历自身的PipelinedSubPartition，逐一进行
         * Flush。Flush之后，通知ResultSubPartitionView有可用数据，可以
         * 进行数据的读取了
         */
        if (flushAlways) {
            targetPartition.flush(targetSubpartition);
        }
    }

    public void broadcastEvent(AbstractEvent event) throws IOException {
        broadcastEvent(event, false);
    }

    public void broadcastEvent(AbstractEvent event, boolean isPriorityEvent) throws IOException {
        targetPartition.broadcastEvent(event, isPriorityEvent);

        if (flushAlways) {
            flushAll();
        }
    }

    public void alignedBarrierTimeout(long checkpointId) throws IOException {
        targetPartition.alignedBarrierTimeout(checkpointId);
    }

    public void abortCheckpoint(long checkpointId, CheckpointException cause) {
        targetPartition.abortCheckpoint(checkpointId, cause);
    }

    /**
     * 序列化
     * @param serializer
     * @param record
     * @return
     * @throws IOException
     */
    @VisibleForTesting
    public static ByteBuffer serializeRecord(
            DataOutputSerializer serializer, IOReadableWritable record) throws IOException {
        // the initial capacity should be no less than 4 bytes
        serializer.setPositionUnsafe(4);

        // write data
        record.write(serializer);

        // write length
        serializer.writeIntUnsafe(serializer.length() - 4, 0);

        return serializer.wrapAsByteBuffer();
    }

    public void flushAll() {
        targetPartition.flushAll();
    }

    /** Sets the metric group for this RecordWriter. */
    public void setMetricGroup(TaskIOMetricGroup metrics) {
        targetPartition.setMetricGroup(metrics);
    }

    @Override
    public CompletableFuture<?> getAvailableFuture() {
        return targetPartition.getAvailableFuture();
    }

    /** This is used to send regular records. */
    public abstract void emit(T record) throws IOException;

    /** This is used to send LatencyMarks to a random target channel. */
    public void randomEmit(T record) throws IOException {
        checkErroneous();

        int targetSubpartition = rng.nextInt(numberOfChannels);
        emit(record, targetSubpartition);
    }

    /** This is used to broadcast streaming Watermarks in-band with records. */
    public abstract void broadcastEmit(T record) throws IOException;

    /** Closes the writer. This stops the flushing thread (if there is one). */
    public void close() {
        // make sure we terminate the thread in any case
        if (outputFlusher != null) {
            outputFlusher.terminate();
            try {
                outputFlusher.join();
            } catch (InterruptedException e) {
                // ignore on close
                // restore interrupt flag to fast exit further blocking calls
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Notifies the writer that the output flusher thread encountered an exception.
     *
     * @param t The exception to report.
     */
    private void notifyFlusherException(Throwable t) {
        if (flusherException == null) {
            LOG.error("An exception happened while flushing the outputs", t);
            flusherException = t;
            volatileFlusherException = t;
        }
    }

    protected void checkErroneous() throws IOException {
        // For performance reasons, we are not checking volatile field every single time.
        if (flusherException != null
                || (volatileFlusherExceptionCheckSkipCount
                                >= VOLATILE_FLUSHER_EXCEPTION_MAX_CHECK_SKIP_COUNT
                        && volatileFlusherException != null)) {
            throw new IOException(
                    "An exception happened while flushing the outputs", volatileFlusherException);
        }
        if (++volatileFlusherExceptionCheckSkipCount
                >= VOLATILE_FLUSHER_EXCEPTION_MAX_CHECK_SKIP_COUNT) {
            volatileFlusherExceptionCheckSkipCount = 0;
        }
    }

    /** Sets the max overdraft buffer size of per gate. */
    public void setMaxOverdraftBuffersPerGate(int maxOverdraftBuffersPerGate) {
        targetPartition.setMaxOverdraftBuffersPerGate(maxOverdraftBuffersPerGate);
    }

    // ------------------------------------------------------------------------

    /**
     * A dedicated thread that periodically flushes the output buffers, to set upper latency bounds.
     *
     * <p>The thread is daemonic, because it is only a utility thread.
     */
    private class OutputFlusher extends Thread {

        private final long timeout;

        private volatile boolean running = true;

        OutputFlusher(String name, long timeout) {
            super(name);
            setDaemon(true);
            this.timeout = timeout;
        }

        public void terminate() {
            running = false;
            interrupt();
        }

        @Override
        public void run() {
            try {
                while (running) {
                    try {
                        Thread.sleep(timeout);
                    } catch (InterruptedException e) {
                        // propagate this if we are still running, because it should not happen
                        // in that case
                        if (running) {
                            throw new Exception(e);
                        }
                    }

                    // any errors here should let the thread come to a halt and be
                    // recognized by the writer
                    flushAll();
                }
            } catch (Throwable t) {
                notifyFlusherException(t);
            }
        }
    }

    @VisibleForTesting
    ResultPartitionWriter getTargetPartition() {
        return targetPartition;
    }
}
