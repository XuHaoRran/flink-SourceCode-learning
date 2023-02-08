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

package org.apache.flink.api.common.state;

import org.apache.flink.annotation.PublicEvolving;

/**
 * Interface that different types of partitioned state must implement.
 *
 * <p>The state is only accessible by functions applied on a {@code KeyedStream}. The key is
 * automatically supplied by the system, so the function always sees the value mapped to the key of
 * the current element. That way, the system can handle stream and state partitioning consistently
 * together.
 *
 * <p>
 * State既然是暴露给用户的，那么就有一些属性需要指定，如
 * State名称、State中类型信息和序列化/反序列化器、State的过期时
 * 间等。在对应的状态后端（StateBackend）中，会调用对应的create
 * 方 法 获 取 到 StateDescriptor 中 的 值 。 在 Flink 中 状 态 描 述 叫 作
 * StateDescriptor.
 * </p>
 *
 * <p>
 * 对 应 于 每 一 类 State ， Flink 内 部 都 设 计 了 对 应 的
 * StateDescriptor ， 在 任 何 使 用 State 的 地 方 ， 都 需 要 通 过
 * StateDescriptor描述状态的信息。
 * 运 行 时 ， 在 RichFunction 和 ProcessFunction 中 ， 通 过
 * RuntimeContext 上 下 文 对 象 ， 使 用 StateDescriptor 从 状 态 后 端
 * （StateBackend）中获取实际的State实例，然后在开发者编写的UDF
 * 中 就 可 以 使 用 这 个 State 了 。 StateBackend 中 有 对 应 则 返 回 现 有 的
 * State，没有则创建新的State。
 * </p>
 */
@PublicEvolving
public interface State {

    /** Removes the value mapped under the current key. */
    void clear();
}
