package com.xuhaoran.chapter05;

import org.apache.flink.api.common.functions.AbstractRichFunction;
import org.apache.flink.api.common.functions.IterationRuntimeContext;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class TransformRichFunctionTest {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);
        DataStreamSource<Event> stream = env.fromElements(new Event("Marry", "./1000", 1000l),
                new Event("Bob", "./2000", 3000l),
                new Event("Bob", "./3000", 4000l),
                new Event("Bob", "./6000", 5000l),
                new Event("Marry", "./3000", 2000l),
                new Event("Marry", "./4000", 4000l),
                new Event("Marry", "./5000", 123l)
        );
        SingleOutputStreamOperator<Integer> result = stream.map(new MyRichMapper());

        result.print();

        env.execute();

    }

    // 实现一个自定义的富函数类
    public static class MyRichMapper extends RichMapFunction<Event, Integer>{

        @Override
        public void setRuntimeContext(RuntimeContext t) {
            super.setRuntimeContext(t);
        }

        @Override
        public RuntimeContext getRuntimeContext() {
            return super.getRuntimeContext();
        }

        @Override
        public IterationRuntimeContext getIterationRuntimeContext() {
            return super.getIterationRuntimeContext();
        }

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            System.out.println("open生命周期被调用 " + getRuntimeContext().getIndexOfThisSubtask() + "号任务");
        }

        @Override
        public void close() throws Exception {
            super.close();
            System.out.println("close生命周期被调用 " + getRuntimeContext().getIndexOfThisSubtask() + "号任务");
        }

        @Override
        public Integer map(Event value) throws Exception {
            return value.url.length();
        }
    }
}
