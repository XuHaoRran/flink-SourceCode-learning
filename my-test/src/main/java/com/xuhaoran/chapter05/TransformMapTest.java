package com.xuhaoran.chapter05;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.ArrayList;

public class TransformMapTest {
    public static void main(String[] args) throws Exception{
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 从元素中读取
        ArrayList<Event> events = new ArrayList<>();
        DataStreamSource<Event> stream = env.fromElements(new Event("Marry", "./1000", 1000l),
                new Event("Bob", "./2000", 2000l));

        // 进行转换计算
        // 1.使用自定义类，实现mapfunction接口
        SingleOutputStreamOperator<String> result = stream.map(new MyMapper());

        // 2.使用匿名类实现mapfunction接口
        SingleOutputStreamOperator<String> result2 = stream.map(new MapFunction<Event, String>(){
            @Override
            public String map(Event value) throws Exception {
                return value.user;
            }
        });

        // 3.使用lambda表达式进行搞
        SingleOutputStreamOperator<String> result3 = stream.map(data -> data.user);

        result.print();

        result2.print();

        result3.print();

        env.execute();

    }

    // 自定义mapfunction
    public static class MyMapper implements MapFunction<Event, String> {

        @Override
        public String map(Event value) throws Exception {
            return value.user;
        }
    }
}
