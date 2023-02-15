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

import java.io.Serializable;

/**
 * Interface for fenced messages.
 * Fenced消息用来防止集群的脑裂（Brain Split）问题，如配置
 * JobMaster HA，开始的时候JobMaster1作为Leader，JobMaser1宕掉，
 * JobMaster2 被 选 为 Leader ， 此 时 如 果 JobMaster1 恢 复 ， 可 能 会 向
 * TaskManager发送消息，TaskManager必须有一种机制能够识别哪个是
 * 当前的JobMaster Leader，此时Fence Token使用JobMaster ID鉴别。
 * 每 个 TaskManager 向 JobMaster 注 册 之 后 ， 都 会 拿 到 当 前 Leader
 * JobMaster的ID作为Fence Token，其他JobMaster发送的消息因为其
 * JobMaster ID与期望的Fence Token不一样，就会被忽略掉。在当前的
 * 实 现 中 ， JobMaster 、 Dispatcher 、 ResourceManager 实 现 了 Fence
 * Token验证机制
 * @param <F> type of the fencing token
 * @param <P> type of the payload
 */
public interface FencedMessage<F extends Serializable, P> extends Message {

    F getFencingToken();

    P getPayload();
}
