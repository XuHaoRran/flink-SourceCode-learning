/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.scheduler.adaptive.allocator;

import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.instance.SlotSharingGroupId;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.jobmanager.scheduler.SlotSharingGroup;
import org.apache.flink.runtime.jobmaster.LogicalSlot;
import org.apache.flink.runtime.jobmaster.SlotInfo;
import org.apache.flink.runtime.jobmaster.SlotRequestId;
import org.apache.flink.runtime.jobmaster.slotpool.PhysicalSlot;
import org.apache.flink.runtime.scheduler.strategy.ExecutionVertexID;
import org.apache.flink.runtime.util.ResourceCounter;
import org.apache.flink.util.Preconditions;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** {@link SlotAllocator} implementation that supports slot sharing.
 *
 * <p>每个TaskManager都是一个Java进程，TaskManager为每个Task分
 * 配独立的执行线程，一个TaskManager中可能执行一个或者多个Task。
 *
 * <p>TaskManager 通 过 Slot 来 控 制 （ 一 个 TaskManager 至 少 有 一 个 Slot ）
 * TaskManager能够接收多少个Task。
 *
 * <p>Slot表示TaskManager拥有资源的一个固定大小的子集。假如一个
 * TaskManager 有 3 个 Slot, 那 么 它 会 将 其 管 理 的 内 存 分 成 3 份 给 各 个
 * Slot，在没有Slot共享的情况下，并行度为2的作业部署之后，Slot与
 * Task的分配关系如图9-5所示。Slot的资源化意味着一个作业的Task将
 * 不需要跟来自其他作业的Task竞争内存、CPU等计算资源。
 *
 * <p>通过调整Slot的数量，用户可以定义Task之间如何互相隔离。如
 * 果一个TaskManager只有一个Slot，意味着每个Task独立地运行在JVM
 * 中。而一个TaskManager有多个Slot，则意味着更多的Task可以共享一
 * 个JVM。在同一个JVM进程中的Task将共享TCP连接和心跳消息。Task之
 * 间也可能共享数据集和数据结构，这样可以减少每个Task的负载。
 *
 * <p>虽然通过Slot对TaskManager的资源进行划分，在一定程度上能够
 * 提高集群的计算资源利用率，但是这种做法并没有考虑到不同Task的
 * 计算任务对资源需求的差异，计算任务有IO密集型、内存密集型、CPU
 * 密集型、GPU密集型等不同的资源消耗类型，有时候还会是多种资源混
 * 合类型。
 *
 * <p>所 以 在 Slot 的 基 础 上 ， Flink 设 计 了 Slot 共 享 机 制 。
 * 用在Flink作业的执行调度中，负责Slot的共享，不同的Task可以共享Slot。
 * */
public class SlotSharingSlotAllocator implements SlotAllocator {

    private final ReserveSlotFunction reserveSlotFunction;
    private final FreeSlotFunction freeSlotFunction;
    private final IsSlotAvailableAndFreeFunction isSlotAvailableAndFreeFunction;

    private SlotSharingSlotAllocator(
            ReserveSlotFunction reserveSlot,
            FreeSlotFunction freeSlotFunction,
            IsSlotAvailableAndFreeFunction isSlotAvailableAndFreeFunction) {
        this.reserveSlotFunction = reserveSlot;
        this.freeSlotFunction = freeSlotFunction;
        this.isSlotAvailableAndFreeFunction = isSlotAvailableAndFreeFunction;
    }

    public static SlotSharingSlotAllocator createSlotSharingSlotAllocator(
            ReserveSlotFunction reserveSlot,
            FreeSlotFunction freeSlotFunction,
            IsSlotAvailableAndFreeFunction isSlotAvailableAndFreeFunction) {
        return new SlotSharingSlotAllocator(
                reserveSlot, freeSlotFunction, isSlotAvailableAndFreeFunction);
    }

    @Override
    public ResourceCounter calculateRequiredSlots(
            Iterable<JobInformation.VertexInformation> vertices) {
        int numTotalRequiredSlots = 0;
        for (Integer requiredSlots : getMaxParallelismForSlotSharingGroups(vertices).values()) {
            numTotalRequiredSlots += requiredSlots;
        }
        return ResourceCounter.withResource(ResourceProfile.UNKNOWN, numTotalRequiredSlots);
    }

    private static Map<SlotSharingGroupId, Integer> getMaxParallelismForSlotSharingGroups(
            Iterable<JobInformation.VertexInformation> vertices) {
        final Map<SlotSharingGroupId, Integer> maxParallelismForSlotSharingGroups = new HashMap<>();
        for (JobInformation.VertexInformation vertex : vertices) {
            maxParallelismForSlotSharingGroups.compute(
                    vertex.getSlotSharingGroup().getSlotSharingGroupId(),
                    (slotSharingGroupId, currentMaxParallelism) ->
                            currentMaxParallelism == null
                                    ? vertex.getParallelism()
                                    : Math.max(currentMaxParallelism, vertex.getParallelism()));
        }
        return maxParallelismForSlotSharingGroups;
    }

    @Override
    public Optional<VertexParallelismWithSlotSharing> determineParallelism(
            JobInformation jobInformation, Collection<? extends SlotInfo> freeSlots) {
        // TODO: This can waste slots if the max parallelism for slot sharing groups is not equal
        final int slotsPerSlotSharingGroup =
                freeSlots.size() / jobInformation.getSlotSharingGroups().size();

        if (slotsPerSlotSharingGroup == 0) {
            // => less slots than slot-sharing groups
            return Optional.empty();
        }

        final Iterator<? extends SlotInfo> slotIterator = freeSlots.iterator();

        final Collection<ExecutionSlotSharingGroupAndSlot> assignments = new ArrayList<>();
        final Map<JobVertexID, Integer> allVertexParallelism = new HashMap<>();

        for (SlotSharingGroup slotSharingGroup : jobInformation.getSlotSharingGroups()) {
            final List<JobInformation.VertexInformation> containedJobVertices =
                    slotSharingGroup.getJobVertexIds().stream()
                            .map(jobInformation::getVertexInformation)
                            .collect(Collectors.toList());

            final Map<JobVertexID, Integer> vertexParallelism =
                    determineParallelism(containedJobVertices, slotsPerSlotSharingGroup);

            final Iterable<ExecutionSlotSharingGroup> sharedSlotToVertexAssignment =
                    createExecutionSlotSharingGroups(vertexParallelism);

            for (ExecutionSlotSharingGroup executionSlotSharingGroup :
                    sharedSlotToVertexAssignment) {
                final SlotInfo slotInfo = slotIterator.next();

                assignments.add(
                        new ExecutionSlotSharingGroupAndSlot(executionSlotSharingGroup, slotInfo));
            }
            allVertexParallelism.putAll(vertexParallelism);
        }

        return Optional.of(new VertexParallelismWithSlotSharing(allVertexParallelism, assignments));
    }

    private static Map<JobVertexID, Integer> determineParallelism(
            Collection<JobInformation.VertexInformation> containedJobVertices, int availableSlots) {
        final Map<JobVertexID, Integer> vertexParallelism = new HashMap<>();
        for (JobInformation.VertexInformation jobVertex : containedJobVertices) {
            final int parallelism = Math.min(jobVertex.getParallelism(), availableSlots);

            vertexParallelism.put(jobVertex.getJobVertexID(), parallelism);
        }

        return vertexParallelism;
    }

    private static Iterable<ExecutionSlotSharingGroup> createExecutionSlotSharingGroups(
            Map<JobVertexID, Integer> containedJobVertices) {
        final Map<Integer, Set<ExecutionVertexID>> sharedSlotToVertexAssignment = new HashMap<>();

        for (Map.Entry<JobVertexID, Integer> jobVertex : containedJobVertices.entrySet()) {
            for (int i = 0; i < jobVertex.getValue(); i++) {
                sharedSlotToVertexAssignment
                        .computeIfAbsent(i, ignored -> new HashSet<>())
                        .add(new ExecutionVertexID(jobVertex.getKey(), i));
            }
        }

        return sharedSlotToVertexAssignment.values().stream()
                .map(ExecutionSlotSharingGroup::new)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ReservedSlots> tryReserveResources(VertexParallelism vertexParallelism) {
        Preconditions.checkArgument(
                vertexParallelism instanceof VertexParallelismWithSlotSharing,
                String.format(
                        "%s expects %s as argument.",
                        SlotSharingSlotAllocator.class.getSimpleName(),
                        VertexParallelismWithSlotSharing.class.getSimpleName()));

        final VertexParallelismWithSlotSharing vertexParallelismWithSlotSharing =
                (VertexParallelismWithSlotSharing) vertexParallelism;

        final Collection<AllocationID> expectedSlots =
                calculateExpectedSlots(vertexParallelismWithSlotSharing.getAssignments());

        if (areAllExpectedSlotsAvailableAndFree(expectedSlots)) {
            final Map<ExecutionVertexID, LogicalSlot> assignedSlots = new HashMap<>();

            for (ExecutionSlotSharingGroupAndSlot executionSlotSharingGroup :
                    vertexParallelismWithSlotSharing.getAssignments()) {
                final SharedSlot sharedSlot =
                        reserveSharedSlot(executionSlotSharingGroup.getSlotInfo());

                for (ExecutionVertexID executionVertexId :
                        executionSlotSharingGroup
                                .getExecutionSlotSharingGroup()
                                .getContainedExecutionVertices()) {
                    final LogicalSlot logicalSlot = sharedSlot.allocateLogicalSlot();
                    assignedSlots.put(executionVertexId, logicalSlot);
                }
            }

            return Optional.of(ReservedSlots.create(assignedSlots));
        } else {
            return Optional.empty();
        }
    }

    @Nonnull
    private Collection<AllocationID> calculateExpectedSlots(
            Iterable<? extends ExecutionSlotSharingGroupAndSlot> assignments) {
        final Collection<AllocationID> requiredSlots = new ArrayList<>();

        for (ExecutionSlotSharingGroupAndSlot assignment : assignments) {
            requiredSlots.add(assignment.getSlotInfo().getAllocationId());
        }
        return requiredSlots;
    }

    private boolean areAllExpectedSlotsAvailableAndFree(
            Iterable<? extends AllocationID> requiredSlots) {
        for (AllocationID requiredSlot : requiredSlots) {
            if (!isSlotAvailableAndFreeFunction.isSlotAvailableAndFree(requiredSlot)) {
                return false;
            }
        }

        return true;
    }

    private SharedSlot reserveSharedSlot(SlotInfo slotInfo) {
        final PhysicalSlot physicalSlot =
                reserveSlotFunction.reserveSlot(
                        slotInfo.getAllocationId(), ResourceProfile.UNKNOWN);

        return new SharedSlot(
                new SlotRequestId(),
                physicalSlot,
                slotInfo.willBeOccupiedIndefinitely(),
                () ->
                        freeSlotFunction.freeSlot(
                                slotInfo.getAllocationId(), null, System.currentTimeMillis()));
    }

    static class ExecutionSlotSharingGroup {
        private final Set<ExecutionVertexID> containedExecutionVertices;

        public ExecutionSlotSharingGroup(Set<ExecutionVertexID> containedExecutionVertices) {
            this.containedExecutionVertices = containedExecutionVertices;
        }

        public Collection<ExecutionVertexID> getContainedExecutionVertices() {
            return containedExecutionVertices;
        }
    }

    static class ExecutionSlotSharingGroupAndSlot {
        private final ExecutionSlotSharingGroup executionSlotSharingGroup;
        private final SlotInfo slotInfo;

        public ExecutionSlotSharingGroupAndSlot(
                ExecutionSlotSharingGroup executionSlotSharingGroup, SlotInfo slotInfo) {
            this.executionSlotSharingGroup = executionSlotSharingGroup;
            this.slotInfo = slotInfo;
        }

        public ExecutionSlotSharingGroup getExecutionSlotSharingGroup() {
            return executionSlotSharingGroup;
        }

        public SlotInfo getSlotInfo() {
            return slotInfo;
        }
    }
}
