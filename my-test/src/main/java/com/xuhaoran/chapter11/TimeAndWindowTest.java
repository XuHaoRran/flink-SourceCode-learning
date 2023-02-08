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

import static org.apache.flink.table.api.Expressions.$;

public class TimeAndWindowTest {
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

        Table clickTable = tableEnv.fromDataStream(clickStream, $("user"), $("url"), $("timestamp").as("ts"),
                $("et").rowtime());


        // 聚合查询转换
        // 1.分组聚合
        Table aggTable = tableEnv.sqlQuery("select user ,count(1) from clickTable group by user");
//        tableEnv.toChangelogStream(aggTable).print();
//        clickTable.printSchema();

        // 2. 分组窗口聚合
        Table groupWindowResultTable = tableEnv.sqlQuery("select user, count(1) as cnt, tumble_end(et, interval '10' second) as entT from clickTable group by user, tumble(et, interval '10' second)");
        tableEnv.toChangelogStream(aggTable).print("agg");
        tableEnv.toChangelogStream(groupWindowResultTable).print("group window:");

        // 3.窗口聚合
        // 3.1 滚动窗口
        Table tumbleWindowResultTable = tableEnv.sqlQuery("select user, count(1) as cnt, " +
                " window_end as endT " +
                "from TABLE(" +
                "  TUMBLE(TABLE clickTable, DESCRIPTOR(et), INTERVAL '10' SECOND)" +
                ")" +
                "group by user, window_end, window_start");
        tableEnv.toDataStream(tumbleWindowResultTable).print("tumble window");


        // 3.2 滑动窗口
        Table hopWindowResultTable = tableEnv.sqlQuery("select user, count(1) as cnt, " +
                " window_end as endT " +
                "from TABLE(" +
                "  HOP(TABLE clickTable, DESCRIPTOR(et), INTERVAL '5' SECOND, INTERVAL '10' SECOND)" +
                ")" +
                "group by user, window_end, window_start");
        tableEnv.toDataStream(hopWindowResultTable).print("HOP window");



        // 3.3 累积窗口
        Table cumWindowResultTable = tableEnv.sqlQuery("select user, count(1) as cnt, " +
                " window_end as endT " +
                "from TABLE(" +
                "  CUMULATE(TABLE clickTable, DESCRIPTOR(et), INTERVAL '5' SECOND, INTERVAL '10' SECOND)" +
                ")" +
                "group by user, window_end, window_start");
        tableEnv.toDataStream(cumWindowResultTable).print("CUM window");


        // 4. 开窗聚合（over）
        Table windowTable = tableEnv.sqlQuery("select user, avg(ts) " +
                "over" +
                "(partition by user order by et rows between 3 preceding and current row) " +
                "as avg_ts from clickTable ");
        tableEnv.toDataStream(windowTable).print("over window");






        env.execute();





    }
}
