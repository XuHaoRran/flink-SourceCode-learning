package com.xuhaoran.chapter06;

import com.xuhaoran.chapter05.Event;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

import java.time.Duration;

public class WindowTest {
    public static void main(String[] args) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.getConfig().setAutoWatermarkInterval(100);

        env.setParallelism(1);
        SingleOutputStreamOperator<Event> stream = env.fromElements(new Event("Marry", "./1000", 1000l),
                        new Event("Bob", "./2000", 3000l),
                        new Event("Bob", "./3000", 4000l),
                        new Event("Bob", "./6000", 5000l),
                        new Event("Marry", "./3000", 2000l),
                        new Event("Marry", "./4000", 4000l),
                        new Event("Marry", "./5000", 123l),
                        new Event("Alice", "./5000", 123l),
                        new Event("Alice", "./9000", 9002l)
                        // 有序流的watermark生成
                )
//                .assignTimestampsAndWatermarks(WatermarkStrategy.<Event>forMonotonousTimestamps().withTimestampAssigner(new SerializableTimestampAssigner<Event>() {
//            @Override
//            public long extractTimestamp(Event element, long recordTimestamp) {
//                return element.timestamp * 1000;
//            }
//        }));
//                .assignTimestampsAndWatermarks(WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(5)).withTimestampAssigner(new SerializableTimestampAssigner<Event>(){
//
//                    @Override
//                    public long extractTimestamp(Event element, long recordTimestamp) {
//                        return element.timestamp;
//                    }
//                }));
        ;

        stream.map(new MapFunction<Event, Tuple2<String, Long>>() {
                    @Override
                    public Tuple2<String, Long> map(Event value) throws Exception {
                        return Tuple2.of(value.user, 1l);
                    }
                }).keyBy(data->data.f0)
//                .countWindow(10, 2); // 滑动计数窗口
//                .window(TumblingEventTimeWindows.of(Time.hours(1))); // 时间时间滚动串口
                .window(SlidingEventTimeWindows.of(Time.hours(1), Time.minutes(5))) // 时间时间滚动串口
//                .window(EventTimeSessionWindows.withGap(Time.hours(1))); // 事件时间会话时间
                .reduce(new ReduceFunction<Tuple2<String, Long>>() {
                    @Override
                    public Tuple2<String, Long> reduce(Tuple2<String, Long> value1, Tuple2<String, Long> value2) throws Exception {
                        return Tuple2.of(value1.f0, value1.f1 + value2.f1);
                    }
                })
                .print();
        ;

    }
}
