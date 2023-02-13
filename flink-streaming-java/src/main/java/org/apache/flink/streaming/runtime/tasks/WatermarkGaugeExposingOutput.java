/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.runtime.tasks;

import org.apache.flink.metrics.Gauge;
import org.apache.flink.streaming.api.operators.Output;
import org.apache.flink.streaming.runtime.metrics.WatermarkGauge;

import java.io.Closeable;

/**
 * An {@link Output} that measures the last emitted watermark with a {@link WatermarkGauge}.
 * 此接口定义了统计Watermark监控指标计算行为，将最后一次发送
 * 给下游的Watermark作为其指标值。其实现类负责计算指标值，在
 * Flink Web UI中，通过可视化StreamGraph看到的Watermark监控信息
 * 即来自于此。
 *
 * @param <T> The type of the elements that can be emitted.
 */
public interface WatermarkGaugeExposingOutput<T> extends Output<T>, Closeable {
    Gauge<Long> getWatermarkGauge();
}
