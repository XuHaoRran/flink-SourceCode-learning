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

package org.apache.flink.core.execution;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.dag.Pipeline;
import org.apache.flink.configuration.Configuration;

import java.util.concurrent.CompletableFuture;

/** The entity responsible for executing a {@link Pipeline}, i.e. a user job.
 *  <p>
 * 流水线执行器在Flink中叫作PipelineExecutor，是Flink Client
 * 生成JobGraph之后，将作业提交给集群的重要环节。集群有Session和
 * Per-Job两种模式。在这两种模式下，集群的启动时机、提交作业的方
 * 式不同，所以在生产环境中有两种PipelineExecutor。Session模式对
 * 应 于 AbstractSessionClusterExecutor ， Per-Job 模 式 对 应 于
 * AbstractJobClusterExecutor。
 * </p>
 *
 * <p>
 * 除了上述两种部署模式外，在IDE环境中运行Flink MiniCluster
 * 进行调试的时候，使用LocalExecutor。
 * </p>
 * */
@Internal
public interface PipelineExecutor {

    /**
     * Executes a {@link Pipeline} based on the provided configuration and returns a {@link
     * JobClient} which allows to interact with the job being executed, e.g. cancel it or take a
     * savepoint.
     *
     * <p><b>ATTENTION:</b> The caller is responsible for managing the lifecycle of the returned
     * {@link JobClient}. This means that e.g. {@code close()} should be called explicitly at the
     * call-site.
     *
     * @param pipeline the {@link Pipeline} to execute
     * @param configuration the {@link Configuration} with the required execution parameters
     * @param userCodeClassloader the {@link ClassLoader} to deserialize usercode
     * @return a {@link CompletableFuture} with the {@link JobClient} corresponding to the pipeline.
     */
    CompletableFuture<JobClient> execute(
            final Pipeline pipeline,
            final Configuration configuration,
            final ClassLoader userCodeClassloader)
            throws Exception;
}
