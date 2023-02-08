package com.xuhaoran.chapter06;

import com.xuhaoran.chapter05.ClickSource;
import com.xuhaoran.chapter05.Event;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

import java.sql.Timestamp;
import java.time.Duration;

public class WindowAggregateTest {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env =
                StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
// 从自定义数据源读取数据，并提取时间戳、生成水位线
        SingleOutputStreamOperator<Event> stream = env.addSource(new
                        ClickSource())
                .assignTimestampsAndWatermarks(WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ZERO)
                                .withTimestampAssigner(new SerializableTimestampAssigner<Event>()
                                {
                                    @Override
                                    public long extractTimestamp(Event element, long recordTimestamp)
                                    {
                                        return element.timestamp;
                                    }
                                })); stream.map(new MapFunction<Event, Tuple2<String,
                        Long>>() {
                    @Override
                    public Tuple2<String, Long> map(Event value) throws Exception {
// 将数据转换成二元组，方便计算
                        return Tuple2.of(value.user, 1L);
                    }
                })
                .keyBy(r -> r.f0)
// 设置滚动事件时间窗口
                .window(TumblingEventTimeWindows.of(Time.seconds(5)))
                .reduce(new ReduceFunction<Tuple2<String, Long>>() {
                    @Override
                    public Tuple2<String, Long> reduce(Tuple2<String, Long> value1,
                                                       Tuple2<String, Long> value2) throws Exception {
// 定义累加规则，窗口闭合时，向下游发送累加结果
                        return Tuple2.of(value1.f0, value1.f1 + value2.f1);
                    }
                })
                .print();
stream.keyBy(data -> data.user)
.window(TumblingEventTimeWindows.of(Time.seconds(10)))
        .aggregate(new AggregateFunction<Event, Tuple2<Long, Integer>, String>() {

            @Override
            public Tuple2<Long, Integer> createAccumulator() {
                return Tuple2.of(0l, 0);
            }

            @Override
            public Tuple2<Long, Integer> add(Event value, Tuple2<Long, Integer> accumulator) {
                return Tuple2.of(accumulator.f0 + value.timestamp, accumulator.f1 + 1);
            }

            @Override
            public String getResult(Tuple2<Long, Integer> accumulator) {
                Timestamp timestamp = new Timestamp(accumulator.f0 / accumulator.f1);
                return timestamp.toString();
            }

            @Override
            public Tuple2<Long, Integer> merge(Tuple2<Long, Integer> a, Tuple2<Long, Integer> b) {
                return Tuple2.of(a.f0 + b.f0, a.f1 + b.f1);
            }
        })
        .print();
        env.execute();
    }
}
