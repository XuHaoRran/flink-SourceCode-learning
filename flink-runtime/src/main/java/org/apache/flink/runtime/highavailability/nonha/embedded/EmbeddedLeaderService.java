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

package org.apache.flink.runtime.highavailability.nonha.embedded;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.runtime.leaderelection.LeaderContender;
import org.apache.flink.runtime.leaderelection.LeaderElectionService;
import org.apache.flink.runtime.leaderretrieval.LeaderRetrievalListener;
import org.apache.flink.runtime.leaderretrieval.LeaderRetrievalService;
import org.apache.flink.util.FlinkException;
import org.apache.flink.util.concurrent.FutureUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.apache.flink.util.Preconditions.checkNotNull;
import static org.apache.flink.util.Preconditions.checkState;

/**
 * A simple leader election service, which selects a leader among contenders and notifies listeners.
 *
 * <p>An election service for contenders can be created via {@link #createLeaderElectionService()},
 * a listener service for leader observers can be created via {@link
 * #createLeaderRetrievalService()}.
 *
 * <p>内嵌的轻量级选举服务，用在Flink Local模式中，主要用来做本地的代码调试和单元测试。
 */
public class EmbeddedLeaderService {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedLeaderService.class);

    private final Object lock = new Object();

    private final Executor notificationExecutor;
    // 维护所有参与Leader；选举的竞争者所对应的EmbeddedLeaderElectionService
    private final Set<EmbeddedLeaderElectionService> allLeaderContenders;
    // 当确认Leader时会触发监听器，这个集合会维护所有监听器
    private final Set<EmbeddedLeaderRetrievalService> listeners;

    /**
     * proposed leader, which has been notified of leadership grant, but has not confirmed.
     * 表示被提名但还没有被确认的Leader对应的EmbeddedLeaderElectionService
     * */
    private EmbeddedLeaderElectionService currentLeaderProposed;

    /**
     *  actual leader that has confirmed leadership and of which listeners have been notified.
     *  已被确认且监听器被触发的Leader对应的EmbeddedLeaderElectionService
     *  */
    private EmbeddedLeaderElectionService currentLeaderConfirmed;

    /** fencing UID for the current leader (or proposed leader).当前被提名的Leader的唯一标识 */
    private volatile UUID currentLeaderSessionId;

    /** the cached address of the current leader. 当前Leader的地址 */
    private String currentLeaderAddress;

    /** flag marking the service as terminated. */
    private boolean shutdown;

    // ------------------------------------------------------------------------

    public EmbeddedLeaderService(Executor notificationsDispatcher) {
        this.notificationExecutor = checkNotNull(notificationsDispatcher);
        this.allLeaderContenders = new HashSet<>();
        this.listeners = new HashSet<>();
    }

    // ------------------------------------------------------------------------
    //  shutdown and errors
    // ------------------------------------------------------------------------

    /**
     * Shuts down this leader election service.
     *
     * <p>This method does not perform a clean revocation of the leader status and no notification
     * to any leader listeners. It simply notifies all contenders and listeners that the service is
     * no longer available.
     */
    public void shutdown() {
        synchronized (lock) {
            shutdownInternally(new Exception("Leader election service is shutting down"));
        }
    }

    @VisibleForTesting
    public boolean isShutdown() {
        synchronized (lock) {
            return shutdown;
        }
    }

    private void fatalError(Throwable error) {
        LOG.error(
                "Embedded leader election service encountered a fatal error. Shutting down service.",
                error);

        synchronized (lock) {
            shutdownInternally(
                    new Exception(
                            "Leader election service is shutting down after a fatal error", error));
        }
    }

    @GuardedBy("lock")
    private void shutdownInternally(Exception exceptionForHandlers) {
        assert Thread.holdsLock(lock);

        if (!shutdown) {
            // clear all leader status
            currentLeaderProposed = null;
            currentLeaderConfirmed = null;
            currentLeaderSessionId = null;
            currentLeaderAddress = null;

            // fail all registered listeners
            for (EmbeddedLeaderElectionService service : allLeaderContenders) {
                service.shutdown(exceptionForHandlers);
            }
            allLeaderContenders.clear();

            // fail all registered listeners
            for (EmbeddedLeaderRetrievalService service : listeners) {
                service.shutdown(exceptionForHandlers);
            }
            listeners.clear();

            shutdown = true;
        }
    }

    // ------------------------------------------------------------------------
    //  creating contenders and listeners
    // ------------------------------------------------------------------------

    public LeaderElectionService createLeaderElectionService() {
        checkState(!shutdown, "leader election service is shut down");
        return new EmbeddedLeaderElectionService();
    }

    public LeaderRetrievalService createLeaderRetrievalService() {
        checkState(!shutdown, "leader election service is shut down");
        return new EmbeddedLeaderRetrievalService();
    }

    // ------------------------------------------------------------------------
    //  adding and removing contenders & listeners
    // ------------------------------------------------------------------------

    /** Callback from leader contenders when they start their service. */
    private void addContender(EmbeddedLeaderElectionService service, LeaderContender contender) {
        synchronized (lock) {
            checkState(!shutdown, "leader election service is shut down");
            checkState(!service.running, "leader election service is already started");

            try {
                // 将EmbeddedLeaderElectionService对象添加到allLeaderContenders
                if (!allLeaderContenders.add(service)) {
                    throw new IllegalStateException(
                            "leader election service was added to this service multiple times");
                }

                service.contender = contender;
                service.running = true;
                // 更新Leader
                updateLeader()
                        .whenComplete(
                                (aVoid, throwable) -> {
                                    if (throwable != null) {
                                        fatalError(throwable);
                                    }
                                });
            } catch (Throwable t) {
                fatalError(t);
            }
        }
    }

    /** Callback from leader contenders when they stop their service. */
    private void removeContender(EmbeddedLeaderElectionService service) {
        synchronized (lock) {
            // if the service was not even started, simply do nothing
            if (!service.running || shutdown) {
                return;
            }

            try {
                if (!allLeaderContenders.remove(service)) {
                    throw new IllegalStateException(
                            "leader election service does not belong to this service");
                }

                // stop the service
                service.contender = null;
                service.running = false;
                service.isLeader = false;

                // if that was the current leader, unset its status
                if (currentLeaderConfirmed == service) {
                    currentLeaderConfirmed = null;
                    currentLeaderSessionId = null;
                    currentLeaderAddress = null;
                }
                if (currentLeaderProposed == service) {
                    currentLeaderProposed = null;
                    currentLeaderSessionId = null;
                }

                updateLeader()
                        .whenComplete(
                                (aVoid, throwable) -> {
                                    if (throwable != null) {
                                        fatalError(throwable);
                                    }
                                });
            } catch (Throwable t) {
                fatalError(t);
            }
        }
    }

    /** Callback from leader contenders when they confirm a leader grant. 确认Leader时调用这一个方法*/
    private void confirmLeader(
            final EmbeddedLeaderElectionService service,
            final UUID leaderSessionId,
            final String leaderAddress) {
        synchronized (lock) {
            // if the service was shut down in the meantime, ignore this confirmation
            if (!service.running || shutdown) {
                return;
            }

            try {
                // check if the confirmation is for the same grant, or whether it is a stale grant
                if (service == currentLeaderProposed
                        && currentLeaderSessionId.equals(leaderSessionId)) {
                    LOG.info(
                            "Received confirmation of leadership for leader {} , session={}",
                            leaderAddress,
                            leaderSessionId);

                    // mark leadership
                    currentLeaderConfirmed = service;
                    currentLeaderAddress = leaderAddress;
                    currentLeaderProposed = null;

                    // notify all listeners
                    notifyAllListeners(leaderAddress, leaderSessionId);
                } else {
                    LOG.debug(
                            "Received confirmation of leadership for a stale leadership grant. Ignoring.");
                }
            } catch (Throwable t) {
                fatalError(t);
            }
        }
    }

    private CompletableFuture<Void> notifyAllListeners(String address, UUID leaderSessionId) {
        final List<CompletableFuture<Void>> notifyListenerFutures =
                new ArrayList<>(listeners.size());

        for (EmbeddedLeaderRetrievalService listener : listeners) {
            notifyListenerFutures.add(notifyListener(address, leaderSessionId, listener.listener));
        }

        return FutureUtils.waitForAll(notifyListenerFutures);
    }

    @GuardedBy("lock")
    private CompletableFuture<Void> updateLeader() {
        // this must be called under the lock
        assert Thread.holdsLock(lock);

        if (currentLeaderConfirmed == null && currentLeaderProposed == null) {
            // we need a new leader
            if (allLeaderContenders.isEmpty()) {
                // no new leader available, tell everyone that there is no leader currently
                return notifyAllListeners(null, null);
            } else {
                // 为当前竞争者生成唯一标识
                // propose a leader and ask it
                final UUID leaderSessionId = UUID.randomUUID();
                EmbeddedLeaderElectionService leaderService = allLeaderContenders.iterator().next();
                // 更新相关字段的值
                currentLeaderSessionId = leaderSessionId;
                currentLeaderProposed = leaderService;
                currentLeaderProposed.isLeader = true;

                LOG.info(
                        "Proposing leadership to contender {}",
                        leaderService.contender.getDescription());
                // 将上面获取的LeaderContender的对象选举为Leader
                // 选举异步执行
                return execute(
                        new GrantLeadershipCall(leaderService.contender, leaderSessionId, LOG));
            }
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Void> notifyListener(
            @Nullable String address,
            @Nullable UUID leaderSessionId,
            LeaderRetrievalListener listener) {
        return CompletableFuture.runAsync(
                new NotifyOfLeaderCall(address, leaderSessionId, listener, LOG),
                notificationExecutor);
    }

    private void addListener(
            EmbeddedLeaderRetrievalService service, LeaderRetrievalListener listener) {
        synchronized (lock) {
            checkState(!shutdown, "leader election service is shut down");
            checkState(!service.running, "leader retrieval service is already started");

            try {
                // 添加监听器
                if (!listeners.add(service)) {
                    throw new IllegalStateException(
                            "leader retrieval service was added to this service multiple times");
                }

                service.listener = listener;
                service.running = true;

                // if we already have a leader, immediately notify this new listener
                // 如果已经确认Leader，则立刻触发监听器
                if (currentLeaderConfirmed != null) {
                    notifyListener(currentLeaderAddress, currentLeaderSessionId, listener);
                }
            } catch (Throwable t) {
                fatalError(t);
            }
        }
    }

    private void removeListener(EmbeddedLeaderRetrievalService service) {
        synchronized (lock) {
            // if the service was not even started, simply do nothing
            if (!service.running || shutdown) {
                return;
            }

            try {
                if (!listeners.remove(service)) {
                    throw new IllegalStateException(
                            "leader retrieval service does not belong to this service");
                }

                // stop the service
                service.listener = null;
                service.running = false;
            } catch (Throwable t) {
                fatalError(t);
            }
        }
    }

    @VisibleForTesting
    CompletableFuture<Void> grantLeadership() {
        synchronized (lock) {
            if (shutdown) {
                return getShutDownFuture();
            }

            return updateLeader();
        }
    }

    private CompletableFuture<Void> getShutDownFuture() {
        return FutureUtils.completedExceptionally(
                new FlinkException("EmbeddedLeaderService has been shut down."));
    }

    @VisibleForTesting
    CompletableFuture<Void> revokeLeadership() {
        synchronized (lock) {
            if (shutdown) {
                return getShutDownFuture();
            }

            if (currentLeaderProposed != null || currentLeaderConfirmed != null) {
                final EmbeddedLeaderElectionService leaderService;

                if (currentLeaderConfirmed != null) {
                    leaderService = currentLeaderConfirmed;
                } else {
                    leaderService = currentLeaderProposed;
                }

                LOG.info("Revoking leadership of {}.", leaderService.contender);
                leaderService.isLeader = false;
                CompletableFuture<Void> revokeLeadershipCallFuture =
                        execute(new RevokeLeadershipCall(leaderService.contender));

                CompletableFuture<Void> notifyAllListenersFuture = notifyAllListeners(null, null);

                currentLeaderProposed = null;
                currentLeaderConfirmed = null;
                currentLeaderAddress = null;
                currentLeaderSessionId = null;

                return CompletableFuture.allOf(
                        revokeLeadershipCallFuture, notifyAllListenersFuture);
            } else {
                return CompletableFuture.completedFuture(null);
            }
        }
    }

    private CompletableFuture<Void> execute(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, notificationExecutor);
    }

    // ------------------------------------------------------------------------
    //  election and retrieval service implementations
    // ------------------------------------------------------------------------

    private class EmbeddedLeaderElectionService implements LeaderElectionService {
        // 需要参与Leader选举的组件都实现了LeaderC;ontender接口，如ResourceManager、DefaultDispatcherRunner类和JobManagerRunneerImpl类
        volatile LeaderContender contender;

        volatile boolean isLeader;

        volatile boolean running;

        @Override
        public void start(LeaderContender contender) throws Exception {
            checkNotNull(contender);
            addContender(this, contender);
        }

        @Override
        public void stop() throws Exception {
            removeContender(this);
        }

        @Override
        public void confirmLeadership(UUID leaderSessionID, String leaderAddress) {
            checkNotNull(leaderSessionID);
            checkNotNull(leaderAddress);
            confirmLeader(this, leaderSessionID, leaderAddress);
        }

        @Override
        public boolean hasLeadership(@Nonnull UUID leaderSessionId) {
            return isLeader && leaderSessionId.equals(currentLeaderSessionId);
        }

        void shutdown(Exception cause) {
            if (running) {
                running = false;
                isLeader = false;
                contender.revokeLeadership();
                contender = null;
            }
        }
    }

    // ------------------------------------------------------------------------

    private class EmbeddedLeaderRetrievalService implements LeaderRetrievalService {
        // 选举成功时要触发的监听器
        volatile LeaderRetrievalListener listener;
        // 用于设置是否正在运行
        volatile boolean running;

        @Override
        public void start(LeaderRetrievalListener listener) throws Exception {
            checkNotNull(listener);
            // 添加监听器的
            addListener(this, listener);
        }

        @Override
        public void stop() throws Exception {
            removeListener(this);
        }

        public void shutdown(Exception cause) {
            if (running) {
                running = false;
                listener = null;
            }
        }
    }

    // ------------------------------------------------------------------------
    //  asynchronous notifications
    //  在EmbeddedLeaderRetrievalService中，添加监听器后最终会异步地执行NotifyOfLeaderCall。
    // 这个Runnable接口的实现类的作用是通知Leader的地址等信息
    // ------------------------------------------------------------------------

    private static class NotifyOfLeaderCall implements Runnable {

        @Nullable private final String address; // null if leader revoked without new leader
        @Nullable private final UUID leaderSessionId; // null if leader revoked without new leader

        private final LeaderRetrievalListener listener; // 监听器
        private final Logger logger;

        NotifyOfLeaderCall(
                @Nullable String address,
                @Nullable UUID leaderSessionId,
                LeaderRetrievalListener listener,
                Logger logger) {

            this.address = address;
            this.leaderSessionId = leaderSessionId;
            this.listener = checkNotNull(listener);
            this.logger = checkNotNull(logger);
        }

        @Override
        public void run() {
            try {
                listener.notifyLeaderAddress(address, leaderSessionId);
            } catch (Throwable t) {
                logger.warn("Error notifying leader listener about new leader", t);
                listener.handleError(t instanceof Exception ? (Exception) t : new Exception(t));
            }
        }
    }

    // ------------------------------------------------------------------------

    /**
     * 在EmbeddLeaderElectionService中，添加竞争者时最终会异步地执行GrantLeadershipCall。
     * 这个Runnable接口的实现类的作用是赋予竞争者Leader的地位
     */
    private static class GrantLeadershipCall implements Runnable {
        // LeaderContender类型，表示竞争者
        private final LeaderContender contender;
        // Leader的唯一标识
        private final UUID leaderSessionId;
        private final Logger logger;

        GrantLeadershipCall(LeaderContender contender, UUID leaderSessionId, Logger logger) {

            this.contender = checkNotNull(contender);
            this.leaderSessionId = checkNotNull(leaderSessionId);
            this.logger = checkNotNull(logger);
        }

        @Override
        public void run() {
            try {
                contender.grantLeadership(leaderSessionId);
            } catch (Throwable t) {
                logger.warn("Error granting leadership to contender", t);
                contender.handleError(t instanceof Exception ? (Exception) t : new Exception(t));
            }
        }
    }

    /**
     * 也实现了Runnable接口。EmbeddedLeaderService类中有rovokeLeadership方法，表示废除Leader，在该方法中会异步执行
     * RevokeLeadershipCall。RevokeLeadershipCall的字段只有LeaderContender类型的contender字段
     */
    private static class RevokeLeadershipCall implements Runnable {

        @Nonnull private final LeaderContender contender;

        RevokeLeadershipCall(@Nonnull LeaderContender contender) {
            this.contender = contender;
        }

        @Override
        public void run() {
            contender.revokeLeadership();
        }
    }
}
