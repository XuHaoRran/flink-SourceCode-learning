package com.xuhaoran.chapter11;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.functions.TableFunction;

public class UdfTest_TableFunction {
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
        tableEnv.createTemporarySystemFunction("MySplit", MySplit.class);

        Table resultTable = tableEnv.sqlQuery("select user, url, word, length " +
                "from clickTable, " +
                "LATERAL TABLE(MySplit(url)) as T(word, length)");

        tableEnv.toDataStream(resultTable).print();




        env.execute();
    }

    // 实现自定义的表函数
    public static class MySplit extends TableFunction<Tuple2<String, Integer>>{
        public void eval(String str){
            String[] fields = str.split("\\?");
            for (String field : fields) {
                collect(Tuple2.of(field, field.length()));
            }
        }
    }
}
