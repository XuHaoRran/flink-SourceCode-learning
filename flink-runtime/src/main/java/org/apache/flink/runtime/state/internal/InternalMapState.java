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

package org.apache.flink.runtime.state.internal;

import org.apache.flink.api.common.state.MapState;

import java.util.Map;

/**
 * The peer to the {@link MapState} in the internal state type hierarchy.
 * <p>内部State接口是给Flink框架使用的，除了对State中数据的访问
 * 之外，还提供了内部的运行时信息接口，如State中数据的序列化器、
 * 命名空间（namespace）、命名空间的序列化器、命名空间合并的接
 * 口。
 * </p>
 *
 * <p>
 *     内部State接口的命名方式为InternalxxxState，内部State接口
 *  * 的 体 系 非 常 复 杂 。
 * </p>
 *
 * <p>
 * InternalMapState继承了面向应用开发者的State接口，也继承了InternalKvState接口，既能访问MapState中保
 * 存的数据，也能访问MapState运行时的信息，
 * </p>
 *
 * <p>See {@link InternalKvState} for a description of the internal state hierarchy.
 *
 * @param <K> The type of key the state is associated to
 * @param <N> The type of the namespace
 * @param <UK> Type of the values folded into the state
 * @param <UV> Type of the value in the state
 */
public interface InternalMapState<K, N, UK, UV>
        extends InternalKvState<K, N, Map<UK, UV>>, MapState<UK, UV> {}
