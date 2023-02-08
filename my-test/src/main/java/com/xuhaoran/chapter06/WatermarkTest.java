package com.xuhaoran.chapter06;

import com.xuhaoran.chapter05.Event;
import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.time.Duration;

public class WatermarkTest {
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
                .assignTimestampsAndWatermarks(WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(5)).withTimestampAssigner(new SerializableTimestampAssigner<Event>(){

                    @Override
                    public long extractTimestamp(Event element, long recordTimestamp) {
                        return element.timestamp;
                    }
                }));
    }
}
