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

package org.apache.flink.runtime.rpc.messages;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/** Message for asynchronous runnable invocations.
 * 执行消息是RunAsync，带有Runnable对象的异步执行请求消息。
 * RpcEndpoint.runAsync方法调用RpcService.runAsync，然后调用
 * RpcService.scheduleRunAsync；RpcService.scheduleRunAsync 调 用
 * AkkaInvocationHanlder.tell方法发送RunAsync消息。
 * */
public final class RunAsync implements Message {

    private final Runnable runnable;

    /** The delay after which the runnable should be called. */
    private final long atTimeNanos;

    /**
     * Creates a new {@code RunAsync} message.
     *
     * @param runnable The Runnable to run.
     * @param atTimeNanos The time (as for System.nanoTime()) when to execute the runnable.
     */
    public RunAsync(Runnable runnable, long atTimeNanos) {
        checkArgument(atTimeNanos >= 0);
        this.runnable = checkNotNull(runnable);
        this.atTimeNanos = atTimeNanos;
    }

    public Runnable getRunnable() {
        return runnable;
    }

    public long getTimeNanos() {
        return atTimeNanos;
    }
}
