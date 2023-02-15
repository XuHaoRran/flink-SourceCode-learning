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

package org.apache.flink.metrics.reporter;

import org.apache.flink.annotation.Public;
import org.apache.flink.metrics.Metric;
import org.apache.flink.metrics.MetricConfig;
import org.apache.flink.metrics.MetricGroup;

/**
 * Reporters are used to export {@link Metric Metrics} to an external backend.
 * 生产环境中，为了保证业务的正常运行，一般都需要对Flink集群
 * 和其中作业的运行状态进行监控，Flink提供了主动和被动两种集成方
 * 式。主动方式是MetricReporter，主动将监控数据写入第三方监控接
 * 口；被动方式是提供Rest接口(WebMonitorEndpoint)，供外部监控系统调用。
 * <p>Reporters are instantiated via a {@link MetricReporterFactory}.
 *
 * <p>MetricReporter是用来向外披露Metric的监测结果的接口，定义
 * 了MetricReporter的生命周期管理、添加和删除指标时的行为。
 *
 * <p>在Flink中使用MetricReporter的时候，有两种实例化的方式：
 * <p>1 ） 使 用 Java 反 射 机 制 实 例 化 MetricReporter ， 要 求
 * MetricReporter的实现类必须是public的访问修饰符，不能是抽象
 * 类，必须有一个无参构造函数。
 * <p>2 ） 使 用 MetricReporterFactory 工 厂 模 式 实 例 化
 * MetricReporter，推荐使用这种实例化的方式，相比发射机制，限制更少。
 * <p>Flink 提 供 了 JMX 、 Graphite 、 InfluxDB 、 Prometheus 、
 * PrometheusPushGateway、StatsD、Datadog和Slf4j共8种Reporter，
 * 在配置文件中进行配置就可以直接使用。具体配置参数的使用参见官方文档。
 */
@Public
public interface MetricReporter {

    // ------------------------------------------------------------------------
    //  life cycle
    // ------------------------------------------------------------------------

    /**
     * Configures this reporter.
     *
     * <p>If the reporter was instantiated generically and hence parameter-less, this method is the
     * place where the reporter sets it's basic fields based on configuration values. Otherwise,
     * this method will typically be a no-op since resources can be acquired in the constructor.
     *
     * <p>This method is always called first on a newly instantiated reporter.
     *
     * @param config A properties object that contains all parameters set for this reporter.
     */
    void open(MetricConfig config);

    /** Closes this reporter. Should be used to close channels, streams and release resources. */
    void close();

    // ------------------------------------------------------------------------
    //  adding / removing metrics
    // ------------------------------------------------------------------------

    /**
     * Called when a new {@link Metric} was added.
     *
     * @param metric the metric that was added
     * @param metricName the name of the metric
     * @param group the group that contains the metric
     */
    void notifyOfAddedMetric(Metric metric, String metricName, MetricGroup group);

    /**
     * Called when a {@link Metric} was removed.
     *
     * @param metric the metric that should be removed
     * @param metricName the name of the metric
     * @param group the group that contains the metric
     */
    void notifyOfRemovedMetric(Metric metric, String metricName, MetricGroup group);
}
