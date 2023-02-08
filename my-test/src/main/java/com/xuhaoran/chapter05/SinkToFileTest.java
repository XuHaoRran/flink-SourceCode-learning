package com.xuhaoran.chapter05;


import org.apache.flink.api.common.functions.Partitioner;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;

import java.util.concurrent.TimeUnit;

public class SinkToFileTest {
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

        StreamingFileSink<String> streamingFileSink = StreamingFileSink.<String>forRowFormat(new Path("./output"),
                        new SimpleStringEncoder<>("UTF-8"))
                .withRollingPolicy(
                        DefaultRollingPolicy.builder()
                                .withMaxPartSize(1024 * 1024 * 1024)
                                .withRolloverInterval(TimeUnit.MINUTES.toMinutes(15))
                                .withInactivityInterval(TimeUnit.MINUTES.toMinutes(5))
                                .build()
                )
                .build();

        stream.map(data->data.toString()).addSink(streamingFileSink);

        env.execute();





    }

}
