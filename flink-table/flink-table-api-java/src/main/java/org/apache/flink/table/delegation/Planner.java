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

package org.apache.flink.table.delegation;

import org.apache.flink.annotation.Experimental;
import org.apache.flink.annotation.Internal;
import org.apache.flink.api.dag.Transformation;
import org.apache.flink.table.api.ExplainDetail;
import org.apache.flink.table.api.PlanReference;
import org.apache.flink.table.operations.ModifyOperation;
import org.apache.flink.table.operations.Operation;
import org.apache.flink.table.operations.QueryOperation;

import java.io.IOException;
import java.util.List;

/**
 * <p>Table API和SQL API面向的是开发者，Planner是Flink引擎内部
 * 的组件，是用户编写的代码和Flink运行时的中介，负责将用户代码转
 * 换到Flink运行时可以识别的Transformation。
 * <p>其主要为Flink中的Planner定义了两个关键行为：
 * <p>SQL解析：将SQL字符串解析为对Table API调用的Operation
 * 树。
 * <p>关 系 代 数 到 Flink 执 行 计 划 ： 将 Operation 树 转 换 为
 * Transformation。
 * <p>在Blink Table模块和Flink Table模块中，各自实现了不同的优
 * 化器
 *
 * <p>This interface serves two purposes:
 *
 * <ul>
 *   <li>SQL parser via {@link #getParser()} - transforms a SQL string into a Table API specific
 *       objects e.g. tree of {@link Operation}s
 *   <li>relational planner - provides a way to plan, optimize and transform tree of {@link
 *       ModifyOperation} into a runnable form ({@link Transformation})
 * </ul>
 *
 * <p>The Planner is execution agnostic. It is up to the {@link
 * org.apache.flink.table.api.TableEnvironment} to ensure that if any of the {@link QueryOperation}
 * pull any runtime configuration, all those configurations are equivalent. Example: If some of the
 * {@link QueryOperation}s scan DataStreams, all those DataStreams must come from the same
 * StreamExecutionEnvironment, because the result of {@link Planner#translate(List)} will strip any
 * execution configuration from the DataStream information.
 *
 * <p>All Tables referenced in either {@link Parser#parse(String)} or {@link
 * Planner#translate(List)} should be previously registered in a {@link
 * org.apache.flink.table.catalog.CatalogManager}, which will be provided during instantiation of
 * the {@link Planner}.
 */
@Internal
public interface Planner {

    /**
     * Retrieves a {@link Parser} that provides methods for parsing a SQL string.
     *
     * @return initialized {@link Parser}
     */
    Parser getParser();

    /**
     * Retrieves a {@link ExtendedOperationExecutor} that provides method for executing operation in
     * a custom way.
     *
     * @return initialized {@link ExtendedOperationExecutor}
     */
    ExtendedOperationExecutor getExtendedOperationExecutor();

    /**
     * Converts a relational tree of {@link ModifyOperation}s into a set of runnable {@link
     * Transformation}s.
     *
     * <p>This method accepts a list of {@link ModifyOperation}s to allow reusing common subtrees of
     * multiple relational queries. Each query's top node should be a {@link ModifyOperation} in
     * order to pass the expected properties of the output {@link Transformation} such as output
     * mode (append, retract, upsert) or the expected output type.
     *
     * @param modifyOperations list of relational operations to plan, optimize and convert in a
     *     single run.
     * @return list of corresponding {@link Transformation}s.
     */
    List<Transformation<?>> translate(List<ModifyOperation> modifyOperations);

    /**
     * Returns the AST of the specified Table API and SQL queries and the execution plan to compute
     * the result of the given collection of {@link QueryOperation}s.
     *
     * @param operations The collection of relational queries for which the AST and execution plan
     *     will be returned.
     * @param extraDetails The extra explain details which the explain result should include, e.g.
     *     estimated cost, changelog mode for streaming, displaying execution plan in json format
     */
    String explain(List<Operation> operations, ExplainDetail... extraDetails);

    // --- Plan compilation and restore

    @Experimental
    InternalPlan loadPlan(PlanReference planReference) throws IOException;

    @Experimental
    InternalPlan compilePlan(List<ModifyOperation> modifyOperations);

    @Experimental
    List<Transformation<?>> translatePlan(InternalPlan plan);

    @Experimental
    String explainPlan(InternalPlan plan, ExplainDetail... extraDetails);
}
