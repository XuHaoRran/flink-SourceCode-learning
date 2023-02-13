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

package org.apache.flink.runtime.io.network.partition;

import org.apache.flink.runtime.io.network.buffer.Buffer;

import javax.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

/**
 * BoundedData is the data store| in a single bounded blocking subpartition.
 * <p>有限数据集在Flink中叫作BoundedData，它定义了存储和读取批
 * 处 理 中 间 计 算 结 果 数 据 集 的 阻 塞 式 接 口 ，
 * BoundedBlockingSubPartition使用BoundedData接口来实现中间结果
 * 集的访问。BoundedData有3个不同的实现，分别对应于不同Buffer的
 * 存储方式。这3种实现方式都是基于java.nio包的内存映射文件和
 * FileChannel。
 * <ul>
     * <li>（1）FileChannelBoundedData
     * 使用Java NIO的FileChannel写入数据和读取文件。
     * <li>（2）FileChannelMemoryMappedBoundedData
     * 使用FileChannel写入数据到文件，使用内存映射文件读取数据。
     * <li>（3）MemoryMappedBoundedData
     * 使用内存映射文件写入、读取，全部是内存操作。
 * </ul>
 * <h2>Life cycle</h2>
 *
 * <p>内存映射文件（Memory-mapped File）是将一段虚拟内存逐字节
 * 映射于一个文件，使得应用程序处理文件如同访问主内存（但在真正
 * 使 用 到 这 些 数 据 前 却 不 会 消 耗 物 理 内 存 ， 也 不 会 有 读 写 磁 盘 的 操
 * 作），内存映射文件与磁盘的真正交互由操作系统负责。
 * <p>java.io基于流的文件读写，读文件会产生2次数据复制，首先是
 * 从硬盘复制到操作系统内核，然后从操作系统内核复制到用户态的应
 * 用程序，写文件也类似。而使用java.nio包内存映射文件，一般情况
 * 下，只有一次复制，且内存分配在操作系统内核，应用程序访问的就
 * 是操作系统的内核内存空间，比基于流进行文件读写快几个数量级。
 * <p>FileChannel 是 直 接 读 写 文 件 ， 与 内 存 映 射 文 件 相 比 ，
 * FileChannel对小文件的读写效率高，内存映射文件则是针对大文件效
 * 率高。
 *
 * <p>The BoundedData is first created during the "write phase" by writing a sequence of buffers
 * through the {@link #writeBuffer(Buffer)} method. The write phase is ended by calling {@link
 * #finishWrite()}. After the write phase is finished, the data can be read multiple times through
 * readers created via {@link #createReader(ResultSubpartitionView)}. Finally, the BoundedData is
 * dropped / deleted by calling {@link #close()}.
 *
 * <h2>Thread Safety and Concurrency</h2>
 *
 * <p>The implementations generally make no assumptions about thread safety. The only contract is
 * that multiple created readers must be able to work independently concurrently.
 */
interface BoundedData extends Closeable {

    /**
     * Writes this buffer to the bounded data. This call fails if the writing phase was already
     * finished via {@link #finishWrite()}.
     */
    void writeBuffer(Buffer buffer) throws IOException;

    /**
     * Finishes the current region and prevents further writes. After calling this method, further
     * calls to {@link #writeBuffer(Buffer)} will fail.
     */
    void finishWrite() throws IOException;

    /**
     * Gets a reader for the bounded data. Multiple readers may be created. This call only succeeds
     * once the write phase was finished via {@link #finishWrite()}.
     */
    BoundedData.Reader createReader(ResultSubpartitionView subpartitionView) throws IOException;

    /**
     * Gets a reader for the bounded data. Multiple readers may be created. This call only succeeds
     * once the write phase was finished via {@link #finishWrite()}.
     */
    default BoundedData.Reader createReader() throws IOException {
        return createReader(new NoOpResultSubpartitionView());
    }

    /**
     * Gets the number of bytes of all written data (including the metadata in the buffer headers).
     */
    long getSize();

    /** The file path for the persisted {@link BoundedBlockingSubpartition}. */
    Path getFilePath();

    // ------------------------------------------------------------------------

    /** A reader to the bounded data. */
    interface Reader extends Closeable {

        @Nullable
        Buffer nextBuffer() throws IOException;
    }
}
