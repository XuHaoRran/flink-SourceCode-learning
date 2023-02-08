package com.xuhaoran.chapter05;

import com.sun.org.apache.bcel.internal.generic.RET;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class TransformUDFTest {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        DataStreamSource<Event> stream = env.fromElements(new Event("Marry", "./1000", 1000l),
                new Event("Bob", "./2000", 3000l),
                new Event("Bob", "./3000", 4000l),
                new Event("Bob", "./6000", 5000l),
                new Event("Marry", "./3000", 2000l),
                new Event("Marry", "./4000", 4000l),
                new Event("Marry", "./5000", 123l)
        );

        SingleOutputStreamOperator<Event> result = stream.filter(new FlinkFilter());

        result.print();

        env.execute();
    }

    public static class FlinkFilter implements FilterFunction<Event> {

        @Override
        public boolean filter(Event value) throws Exception {
            return value.url.contains("./3000");
        }
    }

}


