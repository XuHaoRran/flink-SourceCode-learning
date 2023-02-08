package com.xuhaoran.chapter11;

import com.xuhaoran.chapter05.ClickSource;
import com.xuhaoran.chapter05.Event;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.time.Duration;

public class TopNExample {
    public static void main(String[] args) throws Exception{

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setParallelism(1);


        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // 1.再创建表的DDL中直接定义时间属性
        String createDDL = "create table clickTable(" +
                "`user` STRING, " +
                "url STRING, " +
                "ts BIGINT," +
                "et as TO_TIMESTAMP(FROM_UNIXTIME(ts/1000))," +
                "WATERMARK FOR et as et - INTERVAL '1' SECOND" +
                ") WITH (" +
                "'connector' = 'filesystem', " +
                "'path'='input/click.txt'," +
                "'format'='csv'" +
                "" +
                ")";
        tableEnv.executeSql(createDDL);

        // 2.在流转换成table的时候定义时间属性
        SingleOutputStreamOperator<Event> clickStream = env.addSource(new ClickSource()
        ).assignTimestampsAndWatermarks(WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ZERO)
                .withTimestampAssigner(new SerializableTimestampAssigner<Event>() {
                    @Override
                    public long extractTimestamp(Event element, long recordTimestamp) {

                        return element.timestamp;
                    }
                }));


        // 普通top n，选取当前所有用户中浏览量最大的2个
        Table topNResultTable = tableEnv.sqlQuery("select user, cnt, row_num " +
                "from (" +
                " select *, row_number() over (" +
                " order by cnt desc " +
                ") as row_num" +
                " from (select user, count(url) as cnt from clickTable group by user) " +
                ") where row_num <= 2");
        tableEnv.toChangelogStream(topNResultTable).print("top 2");


        // 窗口top n 统计一段时间内的前2名活跃用户
        String subQuery = "select user, count(url) as cnt, window_start, window_end " +
                "from TABLE(" +
                " TUMBLE(TABLE clickTable, DESCRIPTOR(et), INTERVAL '10' SECOND)" +
                ")" +
                " group by user, window_start, window_end";
        Table windowTopNResultTable = tableEnv.sqlQuery("select user, cnt, row_num " +
                "from (" +
                " select *, row_number() over (" +
                " partition by window_start, window_end" +
                " order by cnt desc " +
                ") as row_num" +
                " from (" + subQuery + ") " +
                ") where row_num <= 2");

        tableEnv.toDataStream(windowTopNResultTable).print("window top n");










        env.execute();
    }
}
