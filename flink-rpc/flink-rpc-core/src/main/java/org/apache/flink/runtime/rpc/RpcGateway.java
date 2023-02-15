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

package org.apache.flink.runtime.rpc;

/** Rpc gateway interface which has to be implemented by Rpc gateways.
 * RpcGateway又叫作远程调用网关，是Flink远程调用的接口协议，
 * 对外提供可调用的接口，所有实现RPC的组件、类都实现了此接口。
 *
 * <p>Flink的几个重要的角色都实现了RpcGateway
 * 接 口 。 JobMasterGateway 接 口 是 JobMaster 提 供 的 对 外 服 务 接 口 ，
 * TaskExecutorGateway是TaskManager（其实现类是TaskExecutor）提
 * 供的对外服务接口，ResourceManagerGateway是ResourceManager资源
 * 管理器提供的对外服务接口，DispatcherGateway是Flink提供的作业
 * 提交接口。组件之间的通信行为都是通过RpcGateway进行交互的。
 * <p>前面在RPC消息类型中提到过Fenced消息，专门用来解决集群脑裂
 * 问题，JobMaster、ResourceManager、DispatcherGateway在高可用模
 * 式下，因为涉及Leader的选举，可能导致集群的脑裂问题，所以这几
 * 个涉及选举的组件，都继承了FencedRpcGateway。
 *
 *
 * */
public interface RpcGateway {

    /**
     * Returns the fully qualified address under which the associated rpc endpoint is reachable.
     *
     * @return Fully qualified (RPC) address under which the associated rpc endpoint is reachable
     */
    String getAddress();

    /**
     * Returns the fully qualified hostname under which the associated rpc endpoint is reachable.
     *
     * @return Fully qualified hostname under which the associated rpc endpoint is reachable
     */
    String getHostname();
}
