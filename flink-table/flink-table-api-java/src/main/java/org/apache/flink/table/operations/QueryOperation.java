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

package org.apache.flink.table.operations;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.catalog.ResolvedSchema;

import java.util.List;

/**
 * Base class for representing an operation structure behind a user-facing {@link Table} API.
 * <p>SQL查询，一般在批处理中使用。在流计算
 * 中很少单独使用SQL查询，一般是配合SQL Insert语句使用
 *
 * <p>It represents an operation that can be a node of a relational query. It has a schema, that can
 * be used to validate a {@link QueryOperation} applied on top of this one.
 *
 * <p>SQL查询Operation的抽象接口，表示关系查询树的节点，Table
 * API的调用最终会转换为QueryOperation，每个节点带有Schema，用来
 * 校验QueryOperation的合法性
 *
 * <p>1.常规的SQL运算操作:包 括 Join 、 Filter 、 Project 、 Sort 、 Distinct 、 Aggregate 、
 * WindowAggregate、Set集合等常规的SQL运算操作
 * <p>2.UDF运算:CalculatedQueryOperation是表示在表上应用TableFunction的数
 * 据结构，一般包含TabeFunction、入参、返回类型等信息
 * <p>3.Catalog查询运算:CatalogQueryOperation，表示对元数据目录的查询运算
 * <p>4.StreamQueryOperation运算:包 含 DataStreamQueryOperation 、
 * JavaDataStreamQueryOperation 、 ScalaDataStreamQueryOperation 三
 * 个实现，用来表达从DataStream读取数据的QueryOperation
 * <p>5.DataSetQueryOperation:用来表达从DataSet中读取数据的QueryOperation
 * <p>6.读取数据源的运算:TableSourceQueryOperation 、
 * RichTableSourceQueryOperation （ 带 有 统 计 信 息 的
 * TableSourceQueryOperation，未来会删除）用来表示从数据源读取数
 * 据 ， 由
 * org.apache.flink.table.api.TableEnvironment.fromTableSource （
 * Table Source）调用生成。
 *
 */
@PublicEvolving
public interface QueryOperation extends Operation {

    /** Resolved schema of this operation. */
    ResolvedSchema getResolvedSchema();

    /**
     * Returns a string that fully serializes this instance. The serialized string can be used for
     * storing the query in e.g. a {@link org.apache.flink.table.catalog.Catalog} as a view.
     *
     * @return detailed string for persisting in a catalog
     * @see Operation#asSummaryString()
     */
    default String asSerializableString() {
        throw new UnsupportedOperationException(
                "QueryOperations are not string serializable for now.");
    }

    List<QueryOperation> getChildren();

    default <T> T accept(QueryOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
