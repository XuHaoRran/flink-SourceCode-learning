package com.xuhaoran.chapter06;

import com.xuhaoran.chapter05.ClickSource;
import com.xuhaoran.chapter05.Event;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.OutputTag;

import java.time.Duration;

public class LateDataTest {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env =
                StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        SingleOutputStreamOperator<Event> stream =
                env.socketTextStream("hadoop1102", 7777)
                        .map(new MapFunction<String, Event>() {
                            @Override
                            public Event map(String value) throws Exception {
                                String[] fields = value.split(" ");
                                return new Event(fields[0].trim(), fields[1].trim(),
                                        Long.valueOf(fields[2].trim()));
                            }
                        })
// 方式一：设置 watermark 延迟时间，2 秒钟
                        .assignTimestampsAndWatermarks(WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                        .withTimestampAssigner(new SerializableTimestampAssigner<Event>() {
                                                               @Override
                                                               public long extractTimestamp(Event element, long
                                                                       recordTimestamp) {
                                                                   return element.timestamp;
                                                               }
                                                           }));
//        定义一个输出标签
        OutputTag<Event> late = new OutputTag<Event>("late"){};


        // 统计每个url的访问量
        SingleOutputStreamOperator<UrlViewCount> result = stream.keyBy(data -> data.url)
                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                .allowedLateness(Time.minutes(1))
                .aggregate(new UrlCountExample.UrlViewCountAgg(), new UrlCountExample.UrlViewCountResult());
        result.print("result");
        result.getSideOutput(late).print("late");

        env.execute();


    }
}
