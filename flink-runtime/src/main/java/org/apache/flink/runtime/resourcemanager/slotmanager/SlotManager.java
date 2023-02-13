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

package org.apache.flink.runtime.resourcemanager.slotmanager;

import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.blocklist.BlockedTaskManagerChecker;
import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.clusterframework.types.SlotID;
import org.apache.flink.runtime.instance.InstanceID;
import org.apache.flink.runtime.resourcemanager.ResourceManagerId;
import org.apache.flink.runtime.resourcemanager.WorkerResourceSpec;
import org.apache.flink.runtime.resourcemanager.registration.TaskExecutorConnection;
import org.apache.flink.runtime.rest.messages.taskmanager.SlotInfo;
import org.apache.flink.runtime.slots.ResourceRequirements;
import org.apache.flink.runtime.taskexecutor.SlotReport;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The slot manager is responsible for maintaining a view on all registered task manager slots,
 * their allocation and all pending slot requests. Whenever a new slot is registered or an allocated
 * slot is freed, then it tries to fulfill another pending slot request. Whenever there are not
 * enough slots available the slot manager will notify the resource manager about it via {@link
 * ResourceActions#allocateResource(WorkerResourceSpec)}.
 *
 * <p>In order to free resources and avoid resource leaks, idling task managers (task managers whose
 * slots are currently not used) and pending slot requests time out triggering their release and
 * failure, respectively.
 *
 * <p>Slot管理器在Flink中叫作SlotManager，是ResourceManager的组
 * 件，从全局角度维护当前有多少TaskManager、每个TaskManager有多
 * 少空闲的Slot和Slot等资源的使用情况。当Flink作业调度执行时，根
 * 据Slot分配策略为Task分配执行的位置。
 *
 * <p>SlotManager虽然是ResourceManager的组件，但是其逻辑是通用
 * 的 ， 并 不 关 心 到 底 使 用 了 哪 种 资 源 集 群 。 面 向 不 同 的 对 象 ，
 * SlotManager提供不同的功能：
 * <ul>
 *     <li>1）对TaskManager提供注册、取消注册、空闲退出等管理动作，
 * 注册则集群可用的Slot变多，取消注册、空闲推出则释放资源，还给
 * 资源管理集群。
 *      <li>2）对Flink作业，接收Slot的请求和释放、资源汇报等。当资源
 * 不足的时候，SlotManger将资源请求暂存在等待队列中，SlotManager
 * 通 知 ResourceManager 去 申 请 更 多 的 资 源 ， 启 动 新 的 TaskManager ，
 * TaskManager注册到SlotManager之后，SlotManager就有可用的新资源
 * 了，从等待队列中依次分配资源。
 * </ul>
 */
public interface SlotManager extends AutoCloseable {
    int getNumberRegisteredSlots();

    int getNumberRegisteredSlotsOf(InstanceID instanceId);

    int getNumberFreeSlots();

    int getNumberFreeSlotsOf(InstanceID instanceId);

    /**
     * Get number of workers SlotManager requested from {@link ResourceActions} that are not yet
     * fulfilled.
     *
     * @return a map whose key set is all the unique resource specs of the pending workers, and the
     *     corresponding value is number of pending workers of that resource spec.
     */
    Map<WorkerResourceSpec, Integer> getRequiredResources();

    ResourceProfile getRegisteredResource();

    ResourceProfile getRegisteredResourceOf(InstanceID instanceID);

    ResourceProfile getFreeResource();

    ResourceProfile getFreeResourceOf(InstanceID instanceID);

    Collection<SlotInfo> getAllocatedSlotsOf(InstanceID instanceID);

    /**
     * Starts the slot manager with the given leader id and resource manager actions.
     *
     * @param newResourceManagerId to use for communication with the task managers
     * @param newMainThreadExecutor to use to run code in the ResourceManager's main thread
     * @param newResourceActions to use for resource (de-)allocations
     * @param newBlockedTaskManagerChecker to query whether a task manager is blocked
     */
    void start(
            ResourceManagerId newResourceManagerId,
            Executor newMainThreadExecutor,
            ResourceActions newResourceActions,
            BlockedTaskManagerChecker newBlockedTaskManagerChecker);

    /** Suspends the component. This clears the internal state of the slot manager. */
    void suspend();

    /**
     * Notifies the slot manager that the resource requirements for the given job should be cleared.
     * The slot manager may assume that no further updates to the resource requirements will occur.
     *
     * @param jobId job for which to clear the requirements
     */
    void clearResourceRequirements(JobID jobId);

    /**
     * Notifies the slot manager about the resource requirements of a job.
     *
     * @param resourceRequirements resource requirements of a job
     */
    void processResourceRequirements(ResourceRequirements resourceRequirements);

    /**
     * Registers a new task manager at the slot manager. This will make the task managers slots
     * known and, thus, available for allocation.
     *
     * @param taskExecutorConnection for the new task manager
     * @param initialSlotReport for the new task manager
     * @param totalResourceProfile for the new task manager
     * @param defaultSlotResourceProfile for the new task manager
     * @return True if the task manager has not been registered before and is registered
     *     successfully; otherwise false
     */
    boolean registerTaskManager(
            TaskExecutorConnection taskExecutorConnection,
            SlotReport initialSlotReport,
            ResourceProfile totalResourceProfile,
            ResourceProfile defaultSlotResourceProfile);

    /**
     * Unregisters the task manager identified by the given instance id and its associated slots
     * from the slot manager.
     *
     * @param instanceId identifying the task manager to unregister
     * @param cause for unregistering the TaskManager
     * @return True if there existed a registered task manager with the given instance id
     */
    boolean unregisterTaskManager(InstanceID instanceId, Exception cause);

    /**
     * Reports the current slot allocations for a task manager identified by the given instance id.
     *
     * @param instanceId identifying the task manager for which to report the slot status
     * @param slotReport containing the status for all of its slots
     * @return true if the slot status has been updated successfully, otherwise false
     */
    boolean reportSlotStatus(InstanceID instanceId, SlotReport slotReport);

    /**
     * Free the given slot from the given allocation. If the slot is still allocated by the given
     * allocation id, then the slot will be marked as free and will be subject to new slot requests.
     *
     * @param slotId identifying the slot to free
     * @param allocationId with which the slot is presumably allocated
     */
    void freeSlot(SlotID slotId, AllocationID allocationId);

    void setFailUnfulfillableRequest(boolean failUnfulfillableRequest);

    /**
     * Trigger the resource requirement check. This method will be called when some slot statuses
     * changed.
     */
    void triggerResourceRequirementsCheck();
}
