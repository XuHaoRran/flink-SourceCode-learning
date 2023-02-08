package com.xuhaoran.chapter09;

import com.xuhaoran.chapter05.ClickSource;
import com.xuhaoran.chapter05.Event;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.*;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

import java.time.Duration;

public class StateTest {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        SingleOutputStreamOperator<Event> stream = env.addSource(new ClickSource())
                .assignTimestampsAndWatermarks(WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ZERO)
                        .withTimestampAssigner(new SerializableTimestampAssigner<Event>() {
                            @Override
                            public long extractTimestamp(Event element, long recordTimestamp) {
                                return element.timestamp;
                            }
                        }));
        stream.keyBy(data -> data.user)
                .flatMap(new MyFlatMap())
                .print();

        env.execute();

        // 实现自定义的flatmapfunction，用keyed state测试

    }

    public static class MyFlatMap extends RichFlatMapFunction<Event, String> {
        // 定义状态
        ValueState<Event> myValueState;
        ListState<Event> myListState;
        MapState<String, Long> myMapState;
        ReducingState<Event> myReducingState;
        AggregatingState<Event,String> myAggregatingState;

        @Override
        public void open(Configuration parameters) throws Exception {
            ValueStateDescriptor valueStateDescriptor = new ValueStateDescriptor<Event>("my-state", Event.class);
             myValueState = getRuntimeContext().getState(valueStateDescriptor);
             myListState = getRuntimeContext().getListState(new ListStateDescriptor<Event>("list-state", Event.class));
             myMapState = getRuntimeContext().getMapState(new MapStateDescriptor<String, Long>("map-state", String.class, Long.class));
             myReducingState = getRuntimeContext().getReducingState(new ReducingStateDescriptor<Event>("reduce-state",
                     new ReduceFunction<Event>() {
                         @Override
                         public Event reduce(Event value1, Event value2) throws Exception {
                             return new Event(value1.user, value2.url, value2.timestamp);
                         }
                     }, Event.class));
            myAggregatingState = getRuntimeContext().getAggregatingState(new AggregatingStateDescriptor<Event, Long, String>("aggre-state", new AggregateFunction<Event, Long, String>() {
                @Override
                public Long createAccumulator() {
                    return 0l;
                }

                @Override
                public Long add(Event value, Long accumulator) {
                    return accumulator + 1;
                }

                @Override
                public String getResult(Long accumulator) {
                    return "count:" + accumulator;
                }

                @Override
                public Long merge(Long a, Long b) {
                    return a + b;
                }
            }, Long.class));
            // 配置状态的TTL
            StateTtlConfig ttlConfig = StateTtlConfig.newBuilder(Time.hours(1))
                    .setUpdateType(StateTtlConfig.UpdateType.OnReadAndWrite)
                    .setStateVisibility(StateTtlConfig.StateVisibility.ReturnExpiredIfNotCleanedUp)
                    .build();
            valueStateDescriptor.enableTimeToLive(ttlConfig);
        }





        @Override
        public void flatMap(Event value, Collector<String> out) throws Exception {
            // 访问和更新状态
            Event v = myValueState.value();
            System.out.println(v);
            myValueState.update(value );
            System.out.println("my value:" + myValueState.value());


            myListState.add(value);

            myMapState.put(value.user, myMapState.get(value.user) == null ? 1 : myMapState.get(value.user)+ 1);
            System.out.println("my map value:" +myMapState.get(value.user));

            myAggregatingState.add(value);
            System.out.println("my agg state " + myAggregatingState.get());

            myReducingState.add(value);
            System.out.println("reducing state" + myReducingState.get());



            // 清除状态
//            myValueState.clear();
        }
    }
}
