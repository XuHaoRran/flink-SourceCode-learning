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

package org.apache.flink.metrics;

import org.apache.flink.annotation.Public;

/** A Gauge is a {@link Metric} that calculates a specific value at a point in time.指标瞬时值
 * 用 来 记 录 一 个 指 标 的 瞬 间 值 。 以 Flink 中 的 指 标 为 例 ，
 * TaskManager中的JVM堆内存使用量（JVM.Heap.Used），记录某个时刻
 * TaskManager进程的JVM堆内存使用量。
 * */
@Public
public interface Gauge<T> extends Metric {

    /**
     * Calculates and returns the measured value.
     *
     * @return calculated value
     */
    T getValue();

    @Override
    default MetricType getMetricType() {
        return MetricType.GAUGE;
    }
}
