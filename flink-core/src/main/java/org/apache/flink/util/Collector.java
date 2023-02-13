/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.util;

import org.apache.flink.annotation.Public;

/**
 * Collects a record and forwards it. The collector is the "push" counterpart of the {@link
 * java.util.Iterator}, which "pulls" data in.
 *
 * <p>算子调用UDF处理数据完毕之后，需要将数据交给下一个算
 * 子。Flink的算子使用Collector接口进行数据传递。
 *
 * <p>《Flink内核原理与实现》上p366所示：Flink中有3种数据传递的方式：
 *
 * <ul>
 *     <li>本地线程内的数据交换。
 *      <p>算子属于同一个OperatorChain，在执行的时候，
 * 会被调度到同一个Task。上游的算子处理数据，然后通过Collector接
 * 口直接调用下游算子的processElement（）方法，在同一个线程内执
 * 行普通的Java方法，没有将数据序列化写入共享内存、下游读取数据
 * 再反序列化的过程，线程切换的开销也省掉了。</p></li>
 *     <li>本地线程之间的数据传递。
 *          <p>位于同一个TaskManager的不同Task的算子之间，不会通过算子间
 * 的 直 接 调 用 方 法 传 输 数 据 ， 而 是 通 过 本 地 内 存 进 行 数 据 传 递 。 以
 * Source算子所在线程与下游的FlatMap算子所在线程间的通信为例，这
 * 两个Task线程共享同一个BufferPool,通过wait（）/notifyAll（）来
 * 同步。Buffer和Netty中的ByteBuf功能类似，可以看作是一块共享的
 * 内存。InputGate负责读取Buffer或Event。</p>
 *          <p>Source算子和FlatMap算子的数据交换如图12-9所示。
 * 本地线程间的数据交换经历5个步骤：
 * <p>1）FlatMap所在线程首先从InputGate的LocalInputChannel中消
 * 费 数 据 ， 如 果 没 有 数 据 则 通 过 InputGate 中 的
 * inputChannelWithData.wait（）方法阻塞等待数据。
 * <p>2）Source算子持续地从外部数据源（如Kafka读取数据）写入
 * ResultSubPartition中。
 * <p>3）ResultSubPartition将数据刷新写入LocalBufferPool中，然
 * 后 通 过 inputChannelWithDa ta.notifyAll （ ） 方 法 唤 醒 FlatMap 线
 * 程。
 * <p>4） 唤 醒 FlatMap 所 在 的 线 程 （ 通 过
 * inputChannelWithData.notifyAll（）方法唤醒）。
 * <p>5）FlatMap线程首先调用LocalInputChannel从LocalBuffer中读
 * 取数据，然后进行数据的反序列化。
 * FlatMap将反序列化之后的数据交给算子中的用户代码进行业务处
 * 理。
 *     </li>
 *     <li>跨网络的数据交换。
 *      <p>跨网络数据传递，即运行在不同TaskManager JVM中的Task之间的
 * 数 据 传 递 ， 与 本 地 线 程 间 的 数 据 交 换 类 似 。 不 同 点 在 于 ， 当 没 有
 * Buffer 可 以 消 费 时 ， 会 通 过 PartitionartitionRequestClient 向
 * FlatMap Task 所 在 的 进 程 发 起 RPC 请 求 ， 远 程 的
 * PartitionRequestServerHandler 接 收 到 请 求 后 ， 读 取
 * ResultPartition管理的Buffer，并返回给Client。</p>
 *     </li>
 *     <p>1） Keyed/Agg 所 在 线 程 从 InputGate 的 RemoteChannel 中 消 费 数
 * 据，如果没有数据则阻塞在RemoteInputChannel中的receivedBuffers
 * 上，等待数据。
 * <p>2） FlatMap 持 续 处 理 数 据 ， 并 将 数 据 写 入 ResultSubPartition
 * 中。
 * <p>3） ResultSubPartition 通 知 PartitionRequestQueue 有 新 的 数
 * 据。
 * <p>4）PartitionRequestQueue从ResultSub读取数据。
 * <p>5） ResultSubPartition 将 数 据 通 过
 * PartitionRequestServerHandler写入Netty Channel，准备写入下游
 * Netty。
 * <p>6）Netty将数据封装到Response消息中，推送给下游。此处需要
 * 下游对上游的request请求，用来建立数据从上游到下游的通道，此请
 * 求 是 对 ResultSubPartition 的 数 据 请 求 ， 创 建 了
 * PartitionRequestQueue。
 * <p>7）下游Netty收到Response消息，进行解码。
 * <p>8）CreditBasedPartitionRequestClientHandler将解码后的数据
 * 写入RemoteInputChannel的Buffer缓冲队列中，然后唤醒Keyed/Agg所
 * 在线程消费数据。
 * <p>9）从Keyed/Agg所在线程RemoteInputChannel中读取Buffer缓冲
 * 队列中的数据，然后进行数据的反序列化，交给算子中的用户代码进
 * 行业务处理。
 * </ul>
 */
@Public
public interface Collector<T> {

    /**
     * Emits a record.
     *
     * @param record The record to collect.
     */
    void collect(T record);

    /** Closes the collector. If any data was buffered, that data will be flushed. */
    void close();
}
