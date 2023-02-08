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

package org.apache.flink.runtime.jobgraph;

import org.apache.flink.runtime.io.network.partition.ResultPartitionType;

import java.util.ArrayList;
import java.util.List;

import static org.apache.flink.util.Preconditions.checkNotNull;
import static org.apache.flink.util.Preconditions.checkState;

/**
 * An intermediate data set is the data set produced by an operator - either a source or any
 * intermediate operation.
 *
 * <p>Intermediate data sets may be read by other operators, materialized, or discarded.
 *
 * <p>中 间 数 据 集 IntermediateDataSet 是 一 种 逻 辑 结 构 ， 用 来 表 示
 * JobVertex的输出，即该JobVertex中包含的算子会产生的数据集。不
 * 同的执行模式下，其对应的结果分区类型不同，决定了在执行时刻数
 * 据交换的模式</p>
 *
 * <p>IntermediateDataSet的个数与该JobVertext对应的StreamNode的
 * 出边数量相同，可以是一个或者多个。</p>
 */
public class IntermediateDataSet implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private final IntermediateDataSetID id; // the identifier

    private final JobVertex producer; // the operation that produced this data set

    // All consumers must have the same partitioner and parallelism
    private final List<JobEdge> consumers = new ArrayList<>();

    // The type of partition to use at runtime
    private final ResultPartitionType resultType;

    private DistributionPattern distributionPattern;

    private boolean isBroadcast;

    // --------------------------------------------------------------------------------------------

    public IntermediateDataSet(
            IntermediateDataSetID id, ResultPartitionType resultType, JobVertex producer) {
        this.id = checkNotNull(id);
        this.producer = checkNotNull(producer);
        this.resultType = checkNotNull(resultType);
    }

    // --------------------------------------------------------------------------------------------

    public IntermediateDataSetID getId() {
        return id;
    }

    public JobVertex getProducer() {
        return producer;
    }

    public List<JobEdge> getConsumers() {
        return this.consumers;
    }

    public boolean isBroadcast() {
        return isBroadcast;
    }

    public DistributionPattern getDistributionPattern() {
        return distributionPattern;
    }

    public ResultPartitionType getResultType() {
        return resultType;
    }

    // --------------------------------------------------------------------------------------------

    public void addConsumer(JobEdge edge) {
        // sanity check
        checkState(id.equals(edge.getSourceId()), "Incompatible dataset id.");

        if (consumers.isEmpty()) {
            distributionPattern = edge.getDistributionPattern();
            isBroadcast = edge.isBroadcast();
        } else {
            checkState(
                    distributionPattern == edge.getDistributionPattern(),
                    "Incompatible distribution pattern.");
            checkState(isBroadcast == edge.isBroadcast(), "Incompatible broadcast type.");
        }
        consumers.add(edge);
    }

    // --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return "Intermediate Data Set (" + id + ")";
    }
}
