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

/** Metric for measuring throughput.
 * 平均值。用来记录一个指标在某个时间段内的平均值。Flink中类似的指标
 * 有Task/算子中的numRecordsInPerSecond，记录此Task或者算子每秒
 * 接收的记录数。
 *
 * */
@Public
public interface Meter extends Metric {

    /** Mark occurrence of an event. */
    void markEvent();

    /**
     * Mark occurrence of multiple events.
     *
     * @param n number of events occurred
     */
    void markEvent(long n);

    /**
     * Returns the current rate of events per second.
     *
     * @return current rate of events per second
     */
    double getRate();

    /**
     * Get number of events marked on the meter.
     *
     * @return number of events marked on the meter
     */
    long getCount();

    @Override
    default MetricType getMetricType() {
        return MetricType.METER;
    }
}
