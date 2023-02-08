package com.xuhaoran.chapter05;

import org.apache.flink.api.common.eventtime.*;
import org.apache.flink.api.common.functions.IterationRuntimeContext;
import org.apache.flink.api.common.functions.Partitioner;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.ParallelSourceFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;

public class TransformPartitionTest {
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

        // 1.随机分区
//        stream.shuffle().print().setParallelism(4);

        // 2.轮询分区
//        stream.rebalance().print().setParallelism(4);

        // 3. rescale重缩放分区
//        env.addSource(new RichParallelSourceFunction<Integer>() {
//
//            @Override
//            public void run(SourceContext<Integer> ctx) throws Exception {
//                for(int i = 0; i < 8; i++){
//                    // 将数分别发送到0和1的并行分区
//                    if (i % 2 == getRuntimeContext().getIndexOfThisSubtask()) {
//                        ctx.collect(i);
//                    }
//                }
//            }
//            @Override
//            public void cancel(){
//
//            }
//
//
//        }).setParallelism(2)
//                .rescale()
//                .print()
//                .setParallelism(4);


        // 广播
//        stream.broadcast().print().setParallelism(4);

        // 全局分区
//        stream.global().print().setParallelism(4);

        // 自定义分区
        env.fromElements(1,2,3,4,5,6,7,8)
                        .partitionCustom(new Partitioner<Integer>() {
                                             @Override
                                             public int partition(Integer key, int numPartitions) {
                                                 return key % 2;
                                             }
                                         }, new KeySelector<Integer, Integer>() {
                                             @Override
                                             public Integer getKey(Integer value) throws Exception {
                                                 return value;
                                             }
                                         }
                        )
                .print().setParallelism(4);
        env.getConfig().setAutoWatermarkInterval(100);
        // 设置水位线
        stream.assignTimestampsAndWatermarks(new WatermarkStrategy<Event>() {
            @Override
            public WatermarkGenerator<Event> createWatermarkGenerator(WatermarkGeneratorSupplier.Context context) {
                 return null;
            }

            @Override
            public TimestampAssigner<Event> createTimestampAssigner(TimestampAssignerSupplier.Context context) {
                return WatermarkStrategy.super.createTimestampAssigner(context);
            }
        });

        env.execute();





    }

}
