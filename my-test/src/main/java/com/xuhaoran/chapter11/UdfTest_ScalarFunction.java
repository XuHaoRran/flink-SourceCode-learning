package com.xuhaoran.chapter11;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.functions.ScalarFunction;

public class UdfTest_ScalarFunction {
    public static void main(String[] args) throws Exception {
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

        // 2. 注册自定义标量函数
        tableEnv.createTemporaryFunction("MyHash", MyHashFunciton.class);

        // 3.调用UDF进行查询转换
        Table hashResult = tableEnv.sqlQuery("select user, MyHash(user) from clickTable");
        tableEnv.toChangelogStream(hashResult).print();

        // 3.调用UDF进行查询转换

        // 4.输出转换成流打印输出

        env.execute();
    }

    // 自定义实现ScalarFunction
    public static class MyHashFunciton extends ScalarFunction {
        public int eval(String str){
            return str.hashCode();
        }
    }
}
