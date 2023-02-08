package com.xuhaoran.chapter12;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.functions.TimedOutPartialMatchHandler;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class OderTimeoutDetectExample {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setParallelism(1);

        SingleOutputStreamOperator<OrderEvent> orderEventStream = env.fromElements(
                new OrderEvent("user_1", "order_1", "create", 1000L),
                new OrderEvent("user_2", "order_2", "create", 2000L),
                new OrderEvent("user_1", "order_1", "modify", 10 * 1000L),
                new OrderEvent("user_1", "order_1", "pay", 60 * 1000L),
                new OrderEvent("user_2", "order_3", "create", 10 * 60 * 1000L),
                new OrderEvent("user_2", "order_3", "pay", 20 * 60 * 1000L)
        ).assignTimestampsAndWatermarks(WatermarkStrategy.<OrderEvent>forBoundedOutOfOrderness(Duration.ZERO).withTimestampAssigner(new SerializableTimestampAssigner<OrderEvent>() {
            @Override
            public long extractTimestamp(OrderEvent element, long recordTimestamp) {
                return element.timestamp;
            }
        }));

        // 2.定义模式
        Pattern<OrderEvent, OrderEvent> pattern = Pattern.<OrderEvent>begin("create")
                .where(new SimpleCondition<OrderEvent>() {
                    @Override
                    public boolean filter(OrderEvent value) throws Exception {
                        return value.evnetType.equals("create");
                    }
                })
                .followedBy("pay")
                .where(new SimpleCondition<OrderEvent>() {
                    @Override
                    public boolean filter(OrderEvent value) throws Exception {
                        return value.evnetType.equals("pay");
                    }
                })
                .within(Time.minutes(15));

        // 3.将模式应用到顶到数据流上
        PatternStream<OrderEvent> patternStream = CEP.pattern(orderEventStream.keyBy(event -> event.orderId), pattern);

        // 4.定义一个侧输出流标签
        OutputTag<String> timeoutTag = new OutputTag<String>("timeout") {
        };

        // 5.将完全匹配和超市部分匹配的突发事件单独提取出来，进行处理
        SingleOutputStreamOperator<String> result = patternStream.process(new OrderPayMatch());
        result.print("payed");
        result.getSideOutput(timeoutTag).print("timeout");

        env.execute();

    }
    public static class OrderPayMatch extends PatternProcessFunction<OrderEvent, String> implements TimedOutPartialMatchHandler<OrderEvent>{

        @Override
        public void processMatch(Map<String, List<OrderEvent>> map, Context context, Collector<String> out) throws Exception {
            // 获取当前的支付事件
            OrderEvent payEvent = map.get("pay").get(0);

            out.collect("用户"+payEvent.userId + "订单 " + payEvent.orderId + " 已支付！！！");
        }

        @Override
        public void processTimedOutMatch(Map<String, List<OrderEvent>> map, Context context) throws Exception {
            OrderEvent createEvent = map.get("create").get(0);
            OutputTag<String> timeoutTag = new OutputTag<String>("timeout"){};
            context.output(timeoutTag, "用户"+createEvent.userId+"订单 "+createEvent.orderId+"已支付！！！");
        }
    }
}
