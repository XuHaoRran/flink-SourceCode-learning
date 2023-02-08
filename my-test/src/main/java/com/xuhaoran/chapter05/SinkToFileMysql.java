package com.xuhaoran.chapter05;


import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;

import java.util.Properties;


public class SinkToFileMysql {

//    kafka-console-consumer.sh --bootstrap-server hadoop1102:9092 --topic abc
//    bin/kafka-console-producer.sh --broker-list hadoop1102:9092-topic  clicks
//    kafka-server-start.sh /opt/module/kafka_2.11-2.4.1/config/server.properties

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        DataStreamSource<Event> stream = env.fromElements(new Event("Marry", "./1000", 1000l),
                new Event("Bob", "./2000", 3000l),
                new Event("Bob", "./3000", 4000l),
                new Event("Bob", "./6000", 5000l),
                new Event("Marry", "./3000", 2000l),
                new Event("Marry", "./4000", 4000l),
                new Event("Marry", "./5000", 123l),
                new Event("Alice", "./5000", 123l),
                new Event("Alice", "./9000", 9002l)
        );

        stream.addSink(JdbcSink.sink("insert into clicks (user, url) values(?, ?)",
                ((statement, event)->{
                    statement.setString(1, event.user);
                    statement.setString(2, event.url);
                }),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl("jdbc:mysql://hadoop1102:3306/test")
                        .withDriverName("com.mysql.jdbc.Driver")
                        .withUsername("root")
                        .withPassword("ab13711522991")
                        .build()
        ));
        env.execute();





    }

}
