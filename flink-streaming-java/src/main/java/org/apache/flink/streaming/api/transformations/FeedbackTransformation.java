/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.api.transformations;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.dag.Transformation;

import org.apache.flink.shaded.guava30.com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

/**
 * This represents a feedback point in a topology.
 *
 * <p>This is different from how iterations work in batch processing. Once a feedback point is
 * defined you can connect one or several {@code Transformations} as a feedback edges. Operations
 * downstream from the feedback point will receive elements from the input of this feedback point
 * and from the feedback edges.
 *
 *
 * <p>这表示拓扑中的反馈点。这与批处理中迭代的工作方式不同。定义反馈点后，可以将一个或多个变换连接为反馈边。
 * 反馈点下游的操作将接收来自该反馈点的输入和来自反馈边缘的元素。输入和反馈边缘的划分都被保留。
 * 它们也可以有不同的分区策略。然而，这要求反馈变换的并行性必须与输入变换的并行度相匹配。
 *
 * <p>表示Flink DAG中的一个反馈点。简单来说，反馈点就是把符合条件的数据重新发回上游Transformation处理，一个反馈点
 * 可以连接一个或者多个上游的Transformation，这些连接关系叫作反馈边。处于反馈点下游的Transformation将可
 * 以从反馈点和反馈边获得元素输入。符合反馈条件并交给上游的Transformation的数据流
 * 叫作反馈流（Feedback DataStream）
 *
 * <p>Both the partitioning of the input and the feedback edges is preserved. They can also have
 * differing partitioning strategies. This requires, however, that the parallelism of the feedback
 * {@code Transformations} must match the parallelism of the input {@code Transformation}.
 *
 * <p>The type of the input {@code Transformation} and the feedback {@code Transformation} must
 * match.
 *
 * @param <T> The type of the input elements and the feedback elements.
 */
@Internal
public class FeedbackTransformation<T> extends Transformation<T> {
    // 上游输入StreamTransformation
    private final Transformation<T> input;

    private final List<Transformation<T>> feedbackEdges;
    // 默认为0，即永远等待，如果设置了等待时间，一旦超过该等待时间，则计算结束并且不再接收数据
    private final Long waitTime;

    /**
     * Creates a new {@code FeedbackTransformation} from the given input.
     *
     * @param input The input {@code Transformation}
     * @param waitTime The wait time of the feedback operator. After the time expires the operation
     *     will close and not receive any more feedback elements.
     */
    public FeedbackTransformation(Transformation<T> input, Long waitTime) {
        super("Feedback", input.getOutputType(), input.getParallelism());
        this.input = input;
        this.waitTime = waitTime;
        this.feedbackEdges = Lists.newArrayList();
    }

    /**
     * Adds a feedback edge. The parallelism of the {@code Transformation} must match the
     * parallelism of the input {@code Transformation} of this {@code FeedbackTransformation}
     * <p> 通过这个方法来收集反馈边，这里的收集就是将下游的StreamTransformation的实例加入到feedbackEdges集合中
     * 要求就是FeedbackTransformation的实例跟待加入StreamTransformation的实例并行度一致
     * @param transform The new feedback {@code Transformation}.
     */
    public void addFeedbackEdge(Transformation<T> transform) {

        if (transform.getParallelism() != this.getParallelism()) {
            throw new UnsupportedOperationException(
                    "Parallelism of the feedback stream must match the parallelism of the original"
                            + " stream. Parallelism of original stream: "
                            + this.getParallelism()
                            + "; parallelism of feedback stream: "
                            + transform.getParallelism()
                            + ". Parallelism can be modified using DataStream#setParallelism() method");
        }

        feedbackEdges.add(transform);
    }

    /** Returns the list of feedback {@code Transformations}. */
    public List<Transformation<T>> getFeedbackEdges() {
        return feedbackEdges;
    }

    /**
     * Returns the wait time. This is the amount of time that the feedback operator keeps listening
     * for feedback elements. Once the time expires the operation will close and will not receive
     * further elements.
     */
    public Long getWaitTime() {
        return waitTime;
    }

    @Override
    public List<Transformation<?>> getTransitivePredecessors() {
        List<Transformation<?>> result = Lists.newArrayList();
        result.add(this);
        result.addAll(input.getTransitivePredecessors());
        return result;
    }

    @Override
    public List<Transformation<?>> getInputs() {
        return Collections.singletonList(input);
    }
}
