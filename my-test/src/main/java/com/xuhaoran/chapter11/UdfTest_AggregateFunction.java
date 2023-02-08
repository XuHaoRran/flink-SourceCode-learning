package com.xuhaoran.chapter11;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.functions.AggregateFunction;

public class UdfTest_AggregateFunction {
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

        tableEnv.createTemporarySystemFunction("WeightedAverage", WeightedAverage.class);

        Table resultTable = tableEnv.sqlQuery("select user, WeightedAverage(ts, 1) as w_avg" +
                " from clickTable group by user");

        tableEnv.toChangelogStream(resultTable).print();

        env.execute();
    }

    // 单独定义一个累加器类型
    public static class WeightedAvgAccumulator {
        public long sum = 0;
        public int count = 0;



    }

    /// 实现自定义的聚合函数，计算加权平均值
    public static class WeightedAverage extends AggregateFunction<Long, WeightedAvgAccumulator> {

        @Override
        public WeightedAvgAccumulator createAccumulator() {
            return new WeightedAvgAccumulator();
        }

        // 累加计算的方法
        public void accumulate(WeightedAvgAccumulator accumulator, Long iValue, Integer iWeight){
            accumulator.sum += iValue * iWeight;
            accumulator.count += iWeight;

        }

        @Override
        public Long getValue(WeightedAvgAccumulator accumulator) {
            if (accumulator.count == 0){
                return null;
            }else {
                return accumulator.sum / accumulator.count;
            }
        }
    }
}
