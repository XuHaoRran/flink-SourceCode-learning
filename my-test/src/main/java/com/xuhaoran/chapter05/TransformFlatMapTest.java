package com.xuhaoran.chapter05;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

public class TransformFlatMapTest {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        DataStreamSource<Event> stream = env.fromElements(new Event("Marry", "./1000", 1000l),
                new Event("Bob", "./2000", 2000l));

        SingleOutputStreamOperator<Event> result = stream.flatMap(new FlatMapFunction<Event, Event>() {
            @Override
            public void flatMap(Event value, Collector<Event> out) throws Exception {
                value.user = value.user + "_haha";
                out.collect(value);
                out.collect(new Event(value.user+"_good", value.url, value.timestamp));

            }
        });

        SingleOutputStreamOperator<Event> result1 = stream.flatMap((Event value, Collector<Event> out) -> {
            out.collect(value);
        }).returns(new TypeHint<Event>() {});

        result.print();

        result1.print();

        env.execute();

    }
}
