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

import java.util.concurrent.CompletableFuture;

/** Interface for self gateways.
 * RpcServer是RpcEndpoint的成员变量，负责接收响应远端的RPC消
 * 息 请 求 。 其 有 RpcServer 有 两 个 实 现 ： AkkaInvocationHandler 和
 * FencedAkkaInvocationHandler。
 *
 * <p>RpcServer的启动实质上是通知底层的AkkaRpcActor切换到START
 * 状态，开始处理远程调用请求
 *
 * */
public interface RpcServer extends StartStoppable, MainThreadExecutable, RpcGateway {

    /**
     * Return a future which is completed when the rpc endpoint has been terminated.
     *
     * @return Future indicating when the rpc endpoint has been terminated
     */
    CompletableFuture<Void> getTerminationFuture();
}
