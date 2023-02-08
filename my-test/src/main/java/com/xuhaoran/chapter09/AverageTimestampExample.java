package com.xuhaoran.chapter09;

import com.xuhaoran.chapter05.ClickSource;
import com.xuhaoran.chapter05.Event;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.AggregatingState;
import org.apache.flink.api.common.state.AggregatingStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

import java.time.Duration;

public class AverageTimestampExample {
    public static void main(String[] args) throws Exception{
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        SingleOutputStreamOperator<Event> stream = env.addSource(new ClickSource()).assignTimestampsAndWatermarks(WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ZERO)
                .withTimestampAssigner(new SerializableTimestampAssigner<Event>() {
                    @Override
                    public long extractTimestamp(Event element, long recordTimestamp) {
                        return element.timestamp;
                    }
                }));

        stream.print("input");

        // 自定义实现平均时间戳的统计
        stream.keyBy(data -> data.user)
                        .flatMap(new AvgTSResult(5l))
                        .print();

        env.execute();

    }
    // 实现自定义的richflatmapfunction
    public static class AvgTSResult extends RichFlatMapFunction<Event, String> {
        private Long count;

        public AvgTSResult(Long count) {
            this.count = count;
        }

        // 定义一个值状态，保存用户访问的次数
        ValueState<Long> countState;

        @Override
        public void open(Configuration parameters) throws Exception {
            avgTsAggState = getRuntimeContext().getAggregatingState(new AggregatingStateDescriptor<Event, Tuple2<Long, Long>, Long>(
                    "avg-ts",
                    new AggregateFunction<Event, Tuple2<Long, Long>, Long>() {
                        @Override
                        public Tuple2<Long, Long> createAccumulator() {
                            return Tuple2.of(0L, 0L);
                        }

                        @Override
                        public Tuple2<Long, Long> add(Event value, Tuple2<Long, Long> accumulator) {

                            return Tuple2.of(accumulator.f0 + accumulator.f0 + value.timestamp, accumulator.f1 + 1);
                        }

                        @Override
                        public Long getResult(Tuple2<Long, Long> accumulator) {
                            return accumulator.f0 / accumulator.f1;
                        }

                        @Override
                        public Tuple2<Long, Long> merge(Tuple2<Long, Long> a, Tuple2<Long, Long> b) {
                            return null;
                        }
                    },
                    Types.TUPLE(Types.LONG, Types.LONG)
            ));

            countState = getRuntimeContext().getState(new ValueStateDescriptor<Long>("count", Long.class));

        }

        // 定义一个聚合的状态，用来保存平均时间戳
        AggregatingState<Event, Long> avgTsAggState;


        @Override
        public void flatMap(Event value, Collector<String> out) throws Exception {
            // 每来一条数据， curr count + 1
            Long currCount = countState.value();
            if (currCount == null){
                currCount = 1l;
            }else {
                currCount++;
            }

            // 更新状态
            countState.update(currCount);
            avgTsAggState.add(value);

            // 如果达到count次数就输出结果
            if (currCount.equals(count)){
                out.collect(value.user + "过去" + count + "次访问平均时间戳为:" + avgTsAggState.get());
                // 清理状态
                countState.clear();
                avgTsAggState.clear();;
            }

        }
    }
}
