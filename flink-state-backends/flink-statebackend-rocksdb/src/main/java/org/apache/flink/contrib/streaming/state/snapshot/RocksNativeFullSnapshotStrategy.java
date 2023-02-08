/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.contrib.streaming.state.snapshot;

import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.contrib.streaming.state.RocksDBKeyedStateBackend;
import org.apache.flink.contrib.streaming.state.RocksDBKeyedStateBackend.RocksDbKvStateInfo;
import org.apache.flink.contrib.streaming.state.RocksDBStateUploader;
import org.apache.flink.core.fs.CloseableRegistry;
import org.apache.flink.runtime.checkpoint.CheckpointOptions;
import org.apache.flink.runtime.state.CheckpointStreamFactory;
import org.apache.flink.runtime.state.CheckpointedStateScope;
import org.apache.flink.runtime.state.IncrementalRemoteKeyedStateHandle;
import org.apache.flink.runtime.state.KeyGroupRange;
import org.apache.flink.runtime.state.KeyedStateHandle;
import org.apache.flink.runtime.state.LocalRecoveryConfig;
import org.apache.flink.runtime.state.SnapshotDirectory;
import org.apache.flink.runtime.state.SnapshotResult;
import org.apache.flink.runtime.state.StateHandleID;
import org.apache.flink.runtime.state.StateObject;
import org.apache.flink.runtime.state.StreamStateHandle;
import org.apache.flink.runtime.state.metainfo.StateMetaInfoSnapshot;
import org.apache.flink.util.Preconditions;
import org.apache.flink.util.ResourceGuard;

import org.rocksdb.RocksDB;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.apache.flink.contrib.streaming.state.snapshot.RocksSnapshotUtil.getUploadedStateSize;

/**
 * Snapshot strategy for {@link RocksDBKeyedStateBackend} based on RocksDB's native checkpoints and
 * creates full snapshots. the difference between savepoint is that sst files will be uploaded
 * rather than states.全量持久化，也就是说每次把全量的State写入到状态存储中（如HDFS）。
 * 内存型、文件型、RocksDB类型的StateBackend都支持全量持久化策略。
 * <p>在执行持久化策略的时候，使用异步机制，每个算子启动1个独立
 * 的线程，将自身的状态写入分布式存储可靠存储中。在做持久化的过
 * 程 中 ， 状 态 可 能 会 被 持 续 修 改 ， 基 于 内 存 的 状 态 后 端 使 用
 * CopyOnWriteStateTable来保证线程安全，RocksDBStateBackend则使
 * 用RockDB的快照机制，使用快照来保证线程安全。</p>
 * @param <K> type of the backend keys.
 */
public class RocksNativeFullSnapshotStrategy<K>
        extends RocksDBSnapshotStrategyBase<
                K, RocksDBSnapshotStrategyBase.NativeRocksDBSnapshotResources> {

    private static final String DESCRIPTION = "Asynchronous full RocksDB snapshot";

    /** The help class used to upload state files. */
    private final RocksDBStateUploader stateUploader;

    public RocksNativeFullSnapshotStrategy(
            @Nonnull RocksDB db,
            @Nonnull ResourceGuard rocksDBResourceGuard,
            @Nonnull TypeSerializer<K> keySerializer,
            @Nonnull LinkedHashMap<String, RocksDbKvStateInfo> kvStateInformation,
            @Nonnull KeyGroupRange keyGroupRange,
            @Nonnegative int keyGroupPrefixBytes,
            @Nonnull LocalRecoveryConfig localRecoveryConfig,
            @Nonnull File instanceBasePath,
            @Nonnull UUID backendUID,
            @Nonnull RocksDBStateUploader rocksDBStateUploader) {
        super(
                DESCRIPTION,
                db,
                rocksDBResourceGuard,
                keySerializer,
                kvStateInformation,
                keyGroupRange,
                keyGroupPrefixBytes,
                localRecoveryConfig,
                instanceBasePath,
                backendUID);
        this.stateUploader = rocksDBStateUploader;
    }

    @Override
    public SnapshotResultSupplier<KeyedStateHandle> asyncSnapshot(
            NativeRocksDBSnapshotResources snapshotResources,
            long checkpointId,
            long timestamp,
            @Nonnull CheckpointStreamFactory checkpointStreamFactory,
            @Nonnull CheckpointOptions checkpointOptions) {

        if (snapshotResources.stateMetaInfoSnapshots.isEmpty()) {
            return registry -> SnapshotResult.empty();
        }

        return new RocksDBNativeFullSnapshotOperation(
                checkpointId,
                checkpointStreamFactory,
                snapshotResources.snapshotDirectory,
                snapshotResources.stateMetaInfoSnapshots);
    }

    @Override
    public void notifyCheckpointComplete(long completedCheckpointId) {
        // nothing to do
    }

    @Override
    public void notifyCheckpointAborted(long abortedCheckpointId) {
        // nothing to do
    }

    @Override
    protected PreviousSnapshot snapshotMetaData(
            long checkpointId, @Nonnull List<StateMetaInfoSnapshot> stateMetaInfoSnapshots) {
        for (Map.Entry<String, RocksDbKvStateInfo> stateMetaInfoEntry :
                kvStateInformation.entrySet()) {
            stateMetaInfoSnapshots.add(stateMetaInfoEntry.getValue().metaInfo.snapshot());
        }
        return EMPTY_PREVIOUS_SNAPSHOT;
    }

    @Override
    public void close() {
        stateUploader.close();
    }

    /** Encapsulates the process to perform a full snapshot of a RocksDBKeyedStateBackend. */
    private final class RocksDBNativeFullSnapshotOperation extends RocksDBSnapshotOperation {

        private RocksDBNativeFullSnapshotOperation(
                long checkpointId,
                @Nonnull CheckpointStreamFactory checkpointStreamFactory,
                @Nonnull SnapshotDirectory localBackupDirectory,
                @Nonnull List<StateMetaInfoSnapshot> stateMetaInfoSnapshots) {

            super(
                    checkpointId,
                    checkpointStreamFactory,
                    localBackupDirectory,
                    stateMetaInfoSnapshots);
        }

        @Override
        public SnapshotResult<KeyedStateHandle> get(CloseableRegistry snapshotCloseableRegistry)
                throws Exception {

            boolean completed = false;

            // Handle to the meta data file
            SnapshotResult<StreamStateHandle> metaStateHandle = null;
            // Handles to all the files in the current snapshot will go here
            final Map<StateHandleID, StreamStateHandle> privateFiles = new HashMap<>();

            try {

                metaStateHandle =
                        materializeMetaData(
                                snapshotCloseableRegistry,
                                stateMetaInfoSnapshots,
                                checkpointId,
                                checkpointStreamFactory);

                // Sanity checks - they should never fail
                Preconditions.checkNotNull(metaStateHandle, "Metadata was not properly created.");
                Preconditions.checkNotNull(
                        metaStateHandle.getJobManagerOwnedSnapshot(),
                        "Metadata for job manager was not properly created.");

                uploadSstFiles(privateFiles, snapshotCloseableRegistry);
                long checkpointedSize = metaStateHandle.getStateSize();
                checkpointedSize += getUploadedStateSize(privateFiles.values());

                final IncrementalRemoteKeyedStateHandle jmIncrementalKeyedStateHandle =
                        new IncrementalRemoteKeyedStateHandle(
                                backendUID,
                                keyGroupRange,
                                checkpointId,
                                Collections.emptyMap(),
                                privateFiles,
                                metaStateHandle.getJobManagerOwnedSnapshot(),
                                checkpointedSize);

                Optional<KeyedStateHandle> localSnapshot =
                        getLocalSnapshot(
                                metaStateHandle.getTaskLocalSnapshot(), Collections.emptyMap());
                final SnapshotResult<KeyedStateHandle> snapshotResult =
                        localSnapshot
                                .map(
                                        keyedStateHandle ->
                                                SnapshotResult.withLocalState(
                                                        jmIncrementalKeyedStateHandle,
                                                        keyedStateHandle))
                                .orElseGet(() -> SnapshotResult.of(jmIncrementalKeyedStateHandle));

                completed = true;

                return snapshotResult;
            } finally {
                if (!completed) {
                    final List<StateObject> statesToDiscard =
                            new ArrayList<>(1 + privateFiles.size());
                    statesToDiscard.add(metaStateHandle);
                    statesToDiscard.addAll(privateFiles.values());
                    cleanupIncompleteSnapshot(statesToDiscard, localBackupDirectory);
                }
            }
        }

        private void uploadSstFiles(
                @Nonnull Map<StateHandleID, StreamStateHandle> privateFiles,
                @Nonnull CloseableRegistry snapshotCloseableRegistry)
                throws Exception {

            // write state data
            Preconditions.checkState(localBackupDirectory.exists());

            Map<StateHandleID, Path> privateFilePaths = new HashMap<>();

            Path[] files = localBackupDirectory.listDirectory();
            if (files != null) {
                // all sst files are private in full snapshot
                for (Path filePath : files) {
                    final String fileName = filePath.getFileName().toString();
                    final StateHandleID stateHandleID = new StateHandleID(fileName);
                    privateFilePaths.put(stateHandleID, filePath);
                }

                privateFiles.putAll(
                        stateUploader.uploadFilesToCheckpointFs(
                                privateFilePaths,
                                checkpointStreamFactory,
                                CheckpointedStateScope.EXCLUSIVE,
                                snapshotCloseableRegistry));
            }
        }
    }
}
