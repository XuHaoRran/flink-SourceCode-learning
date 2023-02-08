package com.xuhaoran.chapter05;

import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.ParallelSourceFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

import java.util.Calendar;
import java.util.Random;

public class SourceCustomTest {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 实现Clicksource自定义
//        DataStreamSource<Event> customStream = env.addSource(new ClickSource());

        DataStreamSource<Integer> customStream = env.addSource(new ParallelCustomSource()).setParallelism(2);

        customStream.print();

        env.execute();
    }

    //实现自定义并行sourcefunction
    public static class ParallelCustomSource implements ParallelSourceFunction<Integer> {
        private Boolean running = true;

        @Override
        public void run(SourceContext<Integer> ctx) throws Exception {
            // 随机生成数据
            Random random = new Random();

            // 循环生成数据
            while (running){
                ctx.collect(random.nextInt(100));
                Thread.sleep(1000l);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }
}
