package com.xuhaoran.chapter05;


import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import java.util.Properties;
import java.util.concurrent.TimeUnit;




public class SinkToFileKafka {

//    kafka-console-consumer.sh --bootstrap-server hadoop1102:9092 --topic abc
//    bin/kafka-console-producer.sh --broker-list hadoop1102:9092-topic  clicks
//    kafka-server-start.sh /opt/module/kafka_2.11-2.4.1/config/server.properties

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 从kafka中读取数据
        Properties properties = new Properties();
        properties.setProperty("group.id", "123");
        properties.setProperty("bootstrap.servers", "hadoop1102:9092");
        properties.setProperty("key.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer");
        properties.setProperty("value.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer");
        properties.setProperty("auto.offset.reset", "latest");
        DataStreamSource<String> kafkaStream = env.addSource(new FlinkKafkaConsumer<String>("clicks", new SimpleStringSchema(),
                properties));


        // 2.用flink来处理转化
        SingleOutputStreamOperator<String> result = kafkaStream.map(new MapFunction<String, String>() {

            @Override
            public String map(String value) throws Exception {
                String[] fields = value.split(",");
                return new Event(fields[0].trim(), fields[1].trim(), Long.valueOf(fields[2].trim())).toString();
            }
        });
        // 3.结果数据写入kafka
        result.addSink(new FlinkKafkaProducer<String>("abc", new SimpleStringSchema(), properties));

        result.print();
//        String brokerList, String topicId, SerializationSchema<IN> serializationSchema

        env.execute();





    }

}
