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

import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.metrics.groups.OperatorMetricGroup;
import org.apache.flink.streaming.api.operators.Input;
import org.apache.flink.streaming.api.operators.OneInputStreamOperator;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.util.OutputTag;

import javax.annotation.Nullable;

/**
 * ChainingOutput & CopyingChainingOutput
 * 这两个类是在OperatorChain内部的算子之间传递数据用的，并不
 * 会 有 序 列 化 的 过 程 。 直 接 在 Output 中 调 用 下 游 算 子 的
 * processElement（）方法。在同一个线程内的算子直接传递数据，跟
 * 普通的Java方法调用一样，这样就直接省略了线程间的数据传送和网
 * 络间的数据传送的开销。
 * @param <T>
 */
final class CopyingChainingOutput<T> extends ChainingOutput<T> {

    private final TypeSerializer<T> serializer;

    public CopyingChainingOutput(
            OneInputStreamOperator<T, ?> operator,
            TypeSerializer<T> serializer,
            @Nullable OutputTag<T> outputTag) {
        super(operator, outputTag);
        this.serializer = serializer;
    }

    public CopyingChainingOutput(
            Input<T> input,
            TypeSerializer<T> serializer,
            OperatorMetricGroup operatorMetricGroup,
            @Nullable OutputTag<T> outputTag) {
        super(input, operatorMetricGroup, outputTag);
        this.serializer = serializer;
    }

    @Override
    public void collect(StreamRecord<T> record) {
        if (this.outputTag != null) {
            // we are not responsible for emitting to the main output.
            return;
        }

        pushToOperator(record);
    }

    @Override
    public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        if (this.outputTag == null || !this.outputTag.equals(outputTag)) {
            // we are not responsible for emitting to the side-output specified by this
            // OutputTag.
            return;
        }

        pushToOperator(record);
    }

    @Override
    protected <X> void pushToOperator(StreamRecord<X> record) {
        try {
            // we know that the given outputTag matches our OutputTag so the record
            // must be of the type that our operator (and Serializer) expects.
            @SuppressWarnings("unchecked")
            StreamRecord<T> castRecord = (StreamRecord<T>) record;

            numRecordsIn.inc();
            StreamRecord<T> copy = castRecord.copy(serializer.copy(castRecord.getValue()));
            input.setKeyContextElement(copy);
            input.processElement(copy);
        } catch (ClassCastException e) {
            if (outputTag != null) {
                // Enrich error message
                ClassCastException replace =
                        new ClassCastException(
                                String.format(
                                        "%s. Failed to push OutputTag with id '%s' to operator. "
                                                + "This can occur when multiple OutputTags with different types "
                                                + "but identical names are being used.",
                                        e.getMessage(), outputTag.getId()));

                throw new ExceptionInChainedOperatorException(replace);
            } else {
                throw new ExceptionInChainedOperatorException(e);
            }
        } catch (Exception e) {
            throw new ExceptionInChainedOperatorException(e);
        }
    }
}
