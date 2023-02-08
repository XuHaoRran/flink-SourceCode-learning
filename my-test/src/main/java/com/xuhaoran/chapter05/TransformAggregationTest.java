package com.xuhaoran.chapter05;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

public class TransformAggregationTest {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        DataStreamSource<Event> stream = env.fromElements(new Event("Marry", "./1000", 1000l),
                new Event("Bob", "./2000", 3000l),
                new Event("Bob", "./2000", 4000l),
                new Event("Bob", "./2000", 5000l),
                new Event("Marry", "./3000", 2000l),
                new Event("Marry", "./4000", 4000l),
                new Event("Marry", "./5000", 123l)
                );

        // 按照分组后进行聚合,提取当前用户最近一次访问数据
        SingleOutputStreamOperator<Event> max = stream.keyBy(new KeySelector<Event, String>() {
            @Override
            public String getKey(Event value) throws Exception {
                return value.user;
            }
        }).max("timestamp");

        max.print();

        stream.keyBy(data-> data.user).maxBy("timestamp").print();


        env.execute();

    }
}
