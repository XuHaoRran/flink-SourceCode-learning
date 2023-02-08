package com.xuhaoran.chapter05;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class TransformUnionTest {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        DataStreamSource<Event> stream1 = env.fromElements(new Event("Marry", "./1000", 1000l),
                new Event("Bob", "./2000", 3000l),
                new Event("Bob", "./3000", 4000l),
                new Event("Bob", "./6000", 5000l),
                new Event("Marry", "./3000", 2000l),
                new Event("Marry", "./4000", 4000l),
                new Event("Marry", "./5000", 123l)
        );


        DataStreamSource<Event> stream2 = env.fromElements(new Event("Marry", "./1000", 1000l),
                new Event("A", "./2000", 3000l),
                new Event("B", "./3000", 4000l),
                new Event("C", "./6000", 5000l),
                new Event("D", "./3000", 2000l),
                new Event("E", "./4000", 4000l),
                new Event("F", "./5000", 123l)
        );

        DataStreamSink<Event> result = stream1.union(stream2).print();

        env.execute();

    }
}
