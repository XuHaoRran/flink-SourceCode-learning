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

package org.apache.flink.runtime.jobmaster.slotpool;

import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.clusterframework.types.SlotProfile;
import org.apache.flink.runtime.jobmanager.scheduler.Locality;
import org.apache.flink.runtime.jobmaster.SlotInfo;

import javax.annotation.Nonnull;

import java.util.Collection;
import java.util.Optional;

/** Interface for slot selection strategies.
 * 选择策略从总体上分为两大类：
 * <p>1.位 置 优 先 的 选 择 策 略 LocationPreferenceSlotSelectionStrategy
 * <ul>
 *     <li>1 ） 默 认 策 略 。
 * DefaultLocationPreferenceSlotSelectionStrategy ， 该 策 略 不 考 虑
 * 资源的均衡分配，会从满足条件的可用Slot集合选择第1个，以此类
 * 推。</li>
 *    <li>
 *        2 ） 均 衡 策 略 。
 * EvenlySpreadOutLocationPreferenceSlotSelectionStrategy ， 该 策
 * 略考虑资源的均衡分配，会从满足条件的可用Slot集合中选择剩余资
 * 源最多的Slot，尽量让各个TaskManager均衡地承担计算压力。
 *    </li>
 * </ul>
 *
 * <p>2.已 分 配 Slot 优 先 的 选 择 策 略
 * PreviousAllocationSlotSelectionStrategy</p>
 *
 *
 * */
public interface SlotSelectionStrategy {

    /**
     * Selects the best {@link SlotInfo} w.r.t. a certain selection criterion from the provided list
     * of available slots and considering the given {@link SlotProfile} that describes the
     * requirements.
     *
     * @param availableSlots a list of the available slots together with their remaining resources
     *     to select from.
     * @param slotProfile a slot profile, describing requirements for the slot selection.
     * @return the selected slot info with the corresponding locality hint.
     */
    Optional<SlotInfoAndLocality> selectBestSlotForProfile(
            @Nonnull Collection<SlotInfoAndResources> availableSlots,
            @Nonnull SlotProfile slotProfile);

    /**
     * This class is a value type that combines a {@link SlotInfo} with its remaining {@link
     * ResourceProfile}.
     */
    final class SlotInfoAndResources {

        @Nonnull private final SlotInfo slotInfo;

        @Nonnull private final ResourceProfile remainingResources;

        private final double taskExecutorUtilization;

        public SlotInfoAndResources(
                @Nonnull SlotInfo slotInfo,
                @Nonnull ResourceProfile remainingResources,
                double taskExecutorUtilization) {
            this.slotInfo = slotInfo;
            this.remainingResources = remainingResources;
            this.taskExecutorUtilization = taskExecutorUtilization;
        }

        @Nonnull
        public SlotInfo getSlotInfo() {
            return slotInfo;
        }

        @Nonnull
        public ResourceProfile getRemainingResources() {
            return remainingResources;
        }

        public double getTaskExecutorUtilization() {
            return taskExecutorUtilization;
        }

        public static SlotInfoAndResources fromSingleSlot(
                @Nonnull SlotInfoWithUtilization slotInfoWithUtilization) {
            return new SlotInfoAndResources(
                    slotInfoWithUtilization,
                    slotInfoWithUtilization.getResourceProfile(),
                    slotInfoWithUtilization.getTaskExecutorUtilization());
        }
    }

    /** This class is a value type that combines a {@link SlotInfo} with a {@link Locality} hint. */
    final class SlotInfoAndLocality {

        @Nonnull private final SlotInfo slotInfo;

        @Nonnull private final Locality locality;

        private SlotInfoAndLocality(@Nonnull SlotInfo slotInfo, @Nonnull Locality locality) {
            this.slotInfo = slotInfo;
            this.locality = locality;
        }

        @Nonnull
        public SlotInfo getSlotInfo() {
            return slotInfo;
        }

        @Nonnull
        public Locality getLocality() {
            return locality;
        }

        public static SlotInfoAndLocality of(
                @Nonnull SlotInfo slotInfo, @Nonnull Locality locality) {
            return new SlotInfoAndLocality(slotInfo, locality);
        }
    }
}
