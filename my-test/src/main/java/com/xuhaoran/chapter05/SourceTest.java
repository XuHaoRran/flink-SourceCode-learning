package com.xuhaoran.chapter05;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Properties;

public class SourceTest {
    public static void main(String[] args) throws Exception {
        // 创建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 1.从文件直接读取数据
        DataStreamSource<String> stream = env.readTextFile("input/click.txt");

        // 2.从集合中读取数据
        ArrayList<Integer> nums = new ArrayList<>();
        nums.add(2);
        nums.add(3);
        DataStreamSource<Integer> numStream = env.fromCollection(nums);

        ArrayList<Event> events = new ArrayList<>();
        events.add(new Event("Marry", "./1000", 1000l));
        events.add(new Event("Bob", "./2000", 2000l));
        DataStreamSource<Event> stream2 = env.fromCollection(events);


        // 4.从socket文本流中读取
//        DataStreamSource<String> stream4 = env.socketTextStream("hadoop1102", 7777);
//        stream.print("1");
//        numStream.print("nums");
//        stream2.print("2");
//        stream4.print();

        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "hadoop1102:9092");
        properties.setProperty("group.id", "123");
        properties.setProperty("key.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer");
        properties.setProperty("value.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer");
        properties.setProperty("auto.offset.reset", "latest");
        DataStreamSource<String> kafkaStream = env.addSource(new
                FlinkKafkaConsumer<String>(
                "clicks",
                new SimpleStringSchema(),
                properties
        ));
        System.out.println(
                "123"
        );
        kafkaStream.print();
//        stream4.print("socketsource");

        System.out.println(
                "33212"
        );
        env.execute();
        System.out.println(
                "12"
        );
    }
}
