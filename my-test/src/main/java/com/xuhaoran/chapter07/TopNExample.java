package com.xuhaoran.chapter07;

import com.xuhaoran.chapter05.ClickSource;
import com.xuhaoran.chapter05.Event;
import com.xuhaoran.chapter06.UrlCountExample;
import com.xuhaoran.chapter06.UrlViewCount;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;

public class TopNExample {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 读取数据
        SingleOutputStreamOperator<Event> stream = env.addSource(new ClickSource())
                .assignTimestampsAndWatermarks(WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ZERO)
                        .withTimestampAssigner(new SerializableTimestampAssigner<Event>() {
                            @Override
                            public long extractTimestamp(Event element, long recordTimestamp) {
                                return element.timestamp;
                            }
                        }));
        // 1. 按照url分组，统计窗口内每个url的访问量
        SingleOutputStreamOperator<UrlViewCount> urlCountStream = stream.keyBy(data -> data.url)
                .window(SlidingEventTimeWindows.of(Time.seconds(10), Time.seconds(5))) // 定义滑动窗口
                .aggregate(new UrlCountExample.UrlViewCountAgg(), new UrlCountExample.UrlViewCountResult());

        urlCountStream.print("url count");

        // 2.对于同一窗口统计出的访问量，进行收集和排序
        urlCountStream.keyBy(data -> data.windowEnd)
                        .process(new TopNProcessResult(2))
                                .print();
        env.execute();
    }
    public static class TopNProcessResult extends KeyedProcessFunction<Long, UrlViewCount, String>{
        // 定义一个属性n
        private Integer n;

        // 定义列表状态
        private ListState<UrlViewCount> urlViewCountListState;

        // 在环境中获取状态
        @Override
        public void open(Configuration parameters) throws Exception {
            urlViewCountListState = getRuntimeContext().getListState(
                    new ListStateDescriptor<UrlViewCount>("url-view-count-list",
                            Types.POJO(UrlViewCount.class)));
        }

        public TopNProcessResult(Integer n) {
            this.n = n;
        }

        @Override
        public void processElement(UrlViewCount value, KeyedProcessFunction<Long, UrlViewCount, String>.Context ctx, Collector<String> out) throws Exception {
            // 将数据保存到状态中
            urlViewCountListState.add(value);

            // 注册windowEnd + 1ms的定时器
            ctx.timerService().registerEventTimeTimer(ctx.getCurrentKey() + 1);
        }

        @Override
        public void onTimer(long timestamp, KeyedProcessFunction<Long, UrlViewCount, String>.OnTimerContext ctx, Collector<String> out) throws Exception {
            ArrayList<UrlViewCount> result = new ArrayList<>();
            for (UrlViewCount urlViewCount : urlViewCountListState.get()) {
                result.add(urlViewCount);
            }

            result.sort(new Comparator<UrlViewCount>() {
                @Override
                public int compare(UrlViewCount o1, UrlViewCount o2) {
                    return o2.count.intValue() - o1.count.intValue();
                }
            });


            // 包装信息打印输出
            StringBuilder sb = new StringBuilder();
            sb.append("----------------------------\n");
            sb.append("窗口结束时间：" + new Timestamp(ctx.getCurrentKey()) + "\n");
            // 取list前两个，包装信息输出
            for (int i = 0; i < 2; i++) {
                UrlViewCount currCount = result.get(i);
                String info = "No. " + (i + 1) + " " + "url: " + currCount.url + " " + "访问量： " + currCount.count + " \n";
                sb.append(info);
            }
            sb.append("----------------------------\n");
            out.collect(sb.toString());
        }
    }
}
