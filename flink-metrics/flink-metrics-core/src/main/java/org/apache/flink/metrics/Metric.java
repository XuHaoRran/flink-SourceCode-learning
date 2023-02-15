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

/** Common super interface for all metrics.
 * <p>Flink Metrics指作业在Flink集群运行过程中的各项指标，包括
 * 机器系统指标，如主机名、CPU、内存、线程、GC垃圾回收、网络、
 * IO、JVM和 任务运行组件（JobManager、TaskManager、作业、Task、
 * 算子）等相关指标。
 * <p>Flink的指标模块有两个作用：
 * <p>1）实时采集监控数据，在Flink Web UI展示监控信息，用户可以
 * 在页面上看到自己提交任务的状态、延迟等信息。
 * <p>2）对外提供监控数据收集接口，用户可以将整个Flink集群的监
 * 控数据主动上报至第三方监控系统。
 * <p>第2个作用对IT规模很大的公司很有用（如互联网公司、大型金融
 * 机构、运营商），它们的集群规模一般比较大，Flink UI的展示和易
 * 用性不够，并且大型机构一般都有集中的运维管理系统，所以就通过
 * 上报的方式与现有的IT运维系统进行集成，利用现有工具对Flink监控。
 * <p>Flink提供了Counter、Gauge、Histogram和Meter 4类监控指标。
 * */
@Public
public interface Metric {
    default MetricType getMetricType() {
        throw new UnsupportedOperationException("Custom metric types are not supported.");
    }
}
