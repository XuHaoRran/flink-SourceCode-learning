package com.xuhaoran.wc;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

import java.lang.reflect.Type;
import java.util.Arrays;

public class StreamWordCount {
    public static void main(String[] args) throws Exception {
        // 1. 创建流式执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
//        env.setRuntimeMode(RuntimeExecutionMode.BATCH)
        // 从参数中提取主机号和端口号
        ParameterTool parameterTool = ParameterTool.fromArgs(args);
        String hostname = parameterTool.get("host");
        Integer port = parameterTool.getInt("port");

        // 2.读取文本流
        DataStreamSource<String> lineDataStream = env.socketTextStream(hostname, port);

        // 3.转换计算
        SingleOutputStreamOperator<Tuple2<String, Long>>  wordAndOne = lineDataStream.flatMap((String line, Collector<String> words)->{
            Arrays.stream(line.split(" ")).forEach(words::collect);
        })
                .returns(Types.STRING)
                .map(word-> Tuple2.of(word, 1l))
                .returns(Types.TUPLE(Types.STRING, Types.LONG));

        // 4.分组
        KeyedStream<Tuple2<String, Long>, String> wordAndOneKS = wordAndOne.keyBy(t-> t.f0);

        // 5.求和
        SingleOutputStreamOperator<Tuple2<String, Long>> result = wordAndOneKS.sum(1);

        // 6.打印
        result.print();

        // 7.执行
        env.execute();

    }
}
