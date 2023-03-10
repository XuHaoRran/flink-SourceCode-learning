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

package org.apache.flink.table.catalog;

import org.apache.flink.annotation.PublicEvolving;

import java.util.Map;
import java.util.Optional;

/** Interface of a database in a catalog.
 * CatalogTable对应于数据库中的表，CatalogView对应于数据库中
 * 的视图，两者相似，所以继承了共同的CatalogBaseTable接口
 * */
@PublicEvolving
public interface CatalogDatabase {
    /** Get a map of properties associated with the database. */
    Map<String, String> getProperties();

    /**
     * Get comment of the database.
     *
     * @return comment of the database
     */
    String getComment();

    /**
     * Get a deep copy of the CatalogDatabase instance.
     *
     * @return a copy of CatalogDatabase instance
     */
    CatalogDatabase copy();

    /**
     * Returns a copy of this {@code CatalogDatabase} with the given properties.
     *
     * @return a new copy of this database with replaced properties
     */
    CatalogDatabase copy(Map<String, String> properties);

    /**
     * Get a brief description of the database.
     *
     * @return an optional short description of the database
     */
    Optional<String> getDescription();

    /**
     * Get a detailed description of the database.
     *
     * @return an optional long description of the database
     */
    Optional<String> getDetailedDescription();
}
