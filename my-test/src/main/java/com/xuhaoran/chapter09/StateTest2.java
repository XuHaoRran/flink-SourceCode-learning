package com.xuhaoran.chapter09;

import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

public class StateTest2 {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);
        env.fromElements(Tuple2.of(1l, 3l), Tuple2.of(1l, 5l), Tuple2.of(1l, 7l), Tuple2.of(1l, 4l), Tuple2.of(1l, 2l))
                .keyBy(0)
                .flatMap(new CountWindowAverage())
                .print();
        // LocalStreamEvironment
        env.execute();
    }
}
class CountWindowAverage extends RichFlatMapFunction<Tuple2<Long, Long>, Tuple2<Long, Long>> {

    private transient ValueState<Tuple2<Long, Long>> sum;

    @Override
    public void flatMap(
            Tuple2<Long, Long> input,
            Collector<Tuple2<Long, Long>> out) throws Exception {
        Tuple2<Long, Long> currentSum = sum.value();
        currentSum.f0 += 1;
        currentSum.f1 += input.f1;
        sum.update(currentSum);
        // 当累计个数超过2个的时候进行求和，并将结果发送给下游
        if (currentSum.f0 >= 2){
            out.collect(new Tuple2<>(input.f0, currentSum.f1 / currentSum.f0));
            sum.clear();
        }
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        ValueStateDescriptor<Tuple2<Long, Long>> descriptor = new ValueStateDescriptor<Tuple2<Long, Long>>(
                "average", // state名字， 在获取state的时候使用
                TypeInformation.of(new TypeHint<Tuple2<Long, Long>>(){}),// state中数据的类型描述，用于做序列化和反序列化
                Tuple2.of(0l, 0l));// valuestate的默认值
        sum = getRuntimeContext().getState(descriptor);
    }

}
