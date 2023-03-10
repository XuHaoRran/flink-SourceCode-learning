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

package org.apache.flink.streaming.api.operators;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.streamrecord.LatencyMarker;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.streaming.runtime.watermarkstatus.WatermarkStatus;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

/**
 * A {@link org.apache.flink.streaming.api.operators.StreamOperator} is supplied with an object of
 * this interface that can be used to emit elements and other messages, such as barriers and
 * watermarks, from an operator.
 *
 * <p>数据输出（Output）是算子向下游传递的数据抽象，定义了向下
 * 游 发 送 StreamRecord 、 Watermark 、 LatencyMark 的 行 为 ， 对 于
 * StreamRecord，多了一个SideOutput的行为定义
 *
 * @param <T> The type of the elements that can be emitted.
 */
@PublicEvolving
public interface Output<T> extends Collector<T> {

    /**
     * Emits a {@link Watermark} from an operator. This watermark is broadcast to all downstream
     * operators.
     *
     * <p>A watermark specifies that no element with a timestamp lower or equal to the watermark
     * timestamp will be emitted in the future.
     *
     * 普通的输出，即把信息发送给下一个算子
     */
    void emitWatermark(Watermark mark);

    void emitWatermarkStatus(WatermarkStatus watermarkStatus);

    /**
     * Emits a record to the side output identified by the given {@link OutputTag}.
     *
     * @param record The record to collect.
     */
    <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record);

    void emitLatencyMarker(LatencyMarker latencyMarker);
}
