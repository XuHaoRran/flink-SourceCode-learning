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

package org.apache.flink.table.expressions;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.table.types.DataType;

import java.util.List;

/**
 * <p>Expression是所有表达式的顶层接口，用来表示尚未经过解析的
 * 表达式，解析、验证之后成为ResolvedExpression。Expression可以
 * 表达树形的表达式结构。Expression的子类特别多，数学运算、条件
 * 运算、逻辑运算、函数调用等都是用Expression表示的。
 * <p>Expression可以表达以下类型。
 * <ul>
 *  <li>1）常量值。</li>
 *  <li>2）字段引用。</li>
 *  <li>3）函数调用。</li>
 * </ul>
 *
 * <p>所有的表达式运算都用函数来表示（如数学运算），DIV函数表示
 * 除法运算、EqualTo函数表示相等判断。表达式Expression可以有子表
 * 达式。
 *
 * <p>General interface for all kinds of expressions.
 *
 * <p>Expressions represent a logical tree for producing a computation result. Every expression
 * consists of zero, one, or more subexpressions. Expressions might be literal values, function
 * calls, or field references.
 *
 * <p>Expressions are part of the API. They might be transformed multiple times within the API stack
 * until they are fully {@link ResolvedExpression}s. Value types and output types are expressed as
 * instances of {@link DataType}.
 */
@PublicEvolving
public interface Expression {

    /**
     * Returns a string that summarizes this expression for printing to a console. An implementation
     * might skip very specific properties.
     *
     * @return summary string of this expression for debugging purposes
     */
    String asSummaryString();

    List<Expression> getChildren();

    <R> R accept(ExpressionVisitor<R> visitor);
}
