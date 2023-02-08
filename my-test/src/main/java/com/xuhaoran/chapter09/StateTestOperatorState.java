package com.xuhaoran.chapter09;

import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.util.Collector;

import java.util.List;

public class StateTestOperatorState {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.fromElements(Tuple2.of(1l, 3l), Tuple2.of(1l, 5l), Tuple2.of(1l, 7l), Tuple2.of(1l, 4l), Tuple2.of(1l, 2l))
                .keyBy(0)
                .flatMap(new CountWindowAverage())
                .print();

        env.execute();
    }
}

class BufferingSink implements SinkFunction<Tuple2<String, Integer>>, CheckpointedFunction {
    private final int threshold;
    private transient ListState<Tuple2<String, Integer>> checkpointedState;
    private List<Tuple2<String, Integer>> bufferedElements;

    public BufferingSink(int threshold) {
        this.threshold = threshold;
    }

    /**
     * operator级别的State需要用户来实现状态的初始化
     * @param context the context for drawing a snapshot of the operator
     * @throws Exception
     */
    @Override
    public void snapshotState(FunctionSnapshotContext context) throws Exception {
        checkpointedState.clear();
        for (Tuple2<String, Integer> bufferedElement : bufferedElements) {
            checkpointedState.add(bufferedElement);
        }
    }

    @Override
    public void initializeState(FunctionInitializationContext context) throws Exception {
        ListStateDescriptor<Tuple2<String, Integer>> descriptor = new ListStateDescriptor<>(
                "buffered0ekenebts",
                TypeInformation.of(new TypeHint<Tuple2<String, Integer>>(){}
                )
        );
        checkpointedState = context.getOperatorStateStore().getListState(descriptor);

        if (context.isRestored()){
            for (Tuple2<String, Integer> element : checkpointedState.get()) {
                bufferedElements.add(element);
            }
        }
    }

    @Override
    public void invoke(Tuple2<String, Integer> value, Context context) throws Exception {
        bufferedElements.add(value);
        if (bufferedElements.size() == this.threshold) {
            for (Tuple2<String, Integer> element : bufferedElements) {
                // 用户业务逻辑，写出数据到外部存储

            }
            bufferedElements.clear();
        }
    }
}
