/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.runtime.streamrecord;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.runtime.jobgraph.OperatorID;

/**
 * Special record type carrying a timestamp of its creation time at a source operator and the
 * vertexId and subtask index of the operator.
 * LatencyMarker用来近似评估延迟，LatencyMarker在Source中创
 * 建，并向下游发送，绕过业务处理逻辑，在Sink节点中使用
 * LatencyMarker估计数据在整个DAG图中流转花费的时间，用来近似地
 * 评估总体上的处理延迟。
 *
 * <p>Flink这样的流计算引擎面对的主要是低延迟的场景。在运行时
 * 刻，各个计算子任务分布在大规模的物理机上，跟踪全链路的计算延
 * 迟是一件复杂且重要的事情。Flink通过LatencyMarker（延迟标记）
 * 的特性实现了全链路延迟的计算，延迟最终作为Histogram类型的指标
 * 进行呈现。该功能默认是关闭的。
 *
 * <p>At sinks, the marker can be used to approximate the time a record needs to travel through the
 * dataflow.
 *
 * <h1>使用原理</h1>
 *
 * <p>在Source算子中周期性发送LatencyMarker，其中记录了当前时间
 * 戳t1。延迟标记在处理过程中被当作一般的事件处理，如果存在背
 * 压，LatencyMaker跟普通的事件一样会被阻塞，所以当LatencyMarker
 * 流 转 到 StreamSink （ Sink 算 子 ） 的 时 候 ， 本 地 处 理 时 间 t2 和
 * LatencyMarker的时间戳t1相比较，t2-t1就是数据处理的延迟。
 * <p>此 处 需 要 注 意 ， 延 迟 标 记 无 法 体 现 出 数 据 处 理 的 延 迟 ， 因 为
 * LatencyMarker在算子中并不会处理，直接就发送给下游。例如，对于
 * 窗口运算，延迟标记会直接绕过窗口，而一般的元素处理则需要等待
 * 窗口触发。只有算子无法接收新的数据，如检查点Barrier对齐、反压
 * 等时，才会导致数据被缓冲等待的延迟体现在延迟标记中。
 * <p>延迟标记用来跟踪DAG图中从Source端到下游算子之间延迟分布，
 * 延迟分布最终作为Histogram类型的指标。指标的粒度使用metrics-
 * latency-interval参数配置。最细粒度是Task，启用Task级别的延迟
 * 跟踪，可能会导致产生大量的Histogram计算。
 * <p>LatencyMarker 计 算 过 程 中 使 用 的 是 TaskManager 的 本 地 系 统 时
 * 间，需要确保Flink集群中的所有机器的时钟是同步的，否则计算出来
 * 的延迟会与实际的延迟有偏差。
 * <p>LatencyMarker最终计算出来的是数据延迟，体现为Histogram类
 * 型的指标，在Flink中定义了3种计算粒度：
 * <p>（1）单值粒度:
 * 指标按照算子ID + Task编号粒度进行统计，无论上游使用了多少
 * 个数据源。例如，对于JOIN，不区分数据源。
 * <p>（2）算子:
 * 指标按照Source算子ID + 算子ID + Task编号粒度进行统计，区
 * 分数据源，不区分Source算子的Task。
 * <p>（3）Task:
 * 指标按照数据源算子ID + 数据源Task编号 + 算子ID + Task编
 * 号，区分数据源且区分数据源算子的Task。
 *
 */
@PublicEvolving
public final class LatencyMarker extends StreamElement {

    // ------------------------------------------------------------------------

    /** The time the latency mark is denoting. */
    private final long markedTime;

    private final OperatorID operatorId;

    private final int subtaskIndex;

    /** Creates a latency mark with the given timestamp. */
    public LatencyMarker(long markedTime, OperatorID operatorId, int subtaskIndex) {
        this.markedTime = markedTime;
        this.operatorId = operatorId;
        this.subtaskIndex = subtaskIndex;
    }

    /** Returns the timestamp marked by the LatencyMarker. */
    public long getMarkedTime() {
        return markedTime;
    }

    public OperatorID getOperatorId() {
        return operatorId;
    }

    public int getSubtaskIndex() {
        return subtaskIndex;
    }

    // ------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        LatencyMarker that = (LatencyMarker) o;

        if (markedTime != that.markedTime) {
            return false;
        }
        if (!operatorId.equals(that.operatorId)) {
            return false;
        }
        return subtaskIndex == that.subtaskIndex;
    }

    @Override
    public int hashCode() {
        int result = (int) (markedTime ^ (markedTime >>> 32));
        result = 31 * result + operatorId.hashCode();
        result = 31 * result + subtaskIndex;
        return result;
    }

    @Override
    public String toString() {
        return "LatencyMarker{"
                + "markedTime="
                + markedTime
                + ", operatorId="
                + operatorId
                + ", subtaskIndex="
                + subtaskIndex
                + '}';
    }
}
