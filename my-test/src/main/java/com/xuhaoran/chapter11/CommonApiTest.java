package com.xuhaoran.chapter11;

import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;

import static org.apache.flink.table.api.Expressions.$;

public class CommonApiTest {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // 1.定义环境配置来创建执行环境
        // 基于blink版本planner版本进行流处理
        EnvironmentSettings settings = EnvironmentSettings.newInstance()
                .inBatchMode()
//                .useBlinkPlanner()
                .build();

        TableEnvironment tableEnv0 = TableEnvironment.create(settings);

        // 1.1 基于老版本planner进行批处理
        EnvironmentSettings settings1 = EnvironmentSettings.newInstance()
                .inStreamingMode()
//                .useOldPlanner()
                .build();

        // 1.3 基于blink版本planner进行批处理
        EnvironmentSettings settings3 = EnvironmentSettings.newInstance()
                .inBatchMode()
//                .useBlinkPlanner()
                .build();

        TableEnvironment tableEnv3 = TableEnvironment.create(settings);

        // 2. 创建表
        String createDLL = "CREATE TABLE clickTable("+
                " `user` STRING, " +
                " url STRING, " +
                " ts BIGINT " +
                ") WITH (" +
                " 'connector' = 'filesystem'," +
                " 'path' = 'input/click.txt', " +
                " 'format' = 'csv'" +
                " )";

        tableEnv.executeSql(createDLL);


        // 创建一张用于输出的表
        String createOutDLL = "CREATE TABLE outTable("+
                " `user` STRING, " +
                " url STRING " +
                ") WITH (" +
                " 'connector' = 'filesystem'," +
                " 'path' = 'output', " +
                " 'format' = 'csv'" +
                " )";
        tableEnv.executeSql(createOutDLL);

        Table clickTable = tableEnv.from("clickTable");
        Table resultTable = clickTable.where($("user").isEqual("Bob"))
                .select($("user"), $("url"));
        tableEnv.createTemporaryView("result2", resultTable);

        // 执行SQL进行表的查询转换
        Table resultTable2 = tableEnv.sqlQuery("select url, user from result2");

        // 创建一张用于控制台打印的输出表
        String createPrintDLL = "CREATE TABLE printTable("+
                " `user` STRING, " +
                " url STRING " +
                ") WITH (" +
                " 'connector' = 'print'" +
                " )";
        tableEnv.executeSql(createPrintDLL);

        // 执行聚合计算的查询转换
        Table aggResult = tableEnv.sqlQuery("select user, count(1) as cnt from clickTable group by user");
        // 创建一张用于控制台打印的输出表
        String createAggPrintDLL = "CREATE TABLE printAggTable("+
                " `user` STRING, " +
                " cnt BIGINT" +
                ") WITH (" +
                " 'connector' = 'print'" +
                " )";
        tableEnv.executeSql(createAggPrintDLL);
        // 测试一下
        Table testResult = tableEnv.sqlQuery("select user, url from clickTable");
        String createtestPrintDLL = "CREATE TABLE printTestTable("+
                " `user` STRING, " +
                " url STRING" +
                ") WITH (" +
                " 'connector' = 'print'" +
                " )";
        tableEnv.executeSql(createtestPrintDLL);
//        testResult.executeInsert("printTestTable");



        // 输出表
        resultTable.executeInsert("outTable");
//        resultTable2.executeInsert("printTable");
//        aggResult.executeInsert("printAggTable");


        // 转换成datastream
        DataStream<Row> dataStream1 = tableEnv.toChangelogStream(resultTable2);
        DataStream<Row> dataStream2 = tableEnv.toChangelogStream(resultTable2);
        dataStream1.print();
        dataStream2.print();
        env.execute();


    }
}
