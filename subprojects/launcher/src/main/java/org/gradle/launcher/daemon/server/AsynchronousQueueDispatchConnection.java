/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.launcher.daemon.server;

import com.google.common.base.Optional;
import org.gradle.api.Nullable;
import org.gradle.launcher.daemon.protocol.OutputMessage;
import org.gradle.messaging.remote.internal.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class AsynchronousQueueDispatchConnection<T> implements Connection<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsynchronousQueueDispatchConnection.class);
    private static final AtomicInteger THREAD_NAME_SUFFIX = new AtomicInteger(0);
    private final Connection<T> delegate;
    private final ConcurrentLinkedQueue<Optional<T>> queue;
    private final Thread dispatcherThread;
    private volatile boolean stopped;

    public AsynchronousQueueDispatchConnection(Connection<T> delegate) {
        this.delegate = delegate;
        this.queue = new ConcurrentLinkedQueue<Optional<T>>();
        this.dispatcherThread = new Thread(new Dispatcher(), "daemon-async-dispatcher-" + THREAD_NAME_SUFFIX.getAndIncrement());
        this.dispatcherThread.start();
    }

    @Override
    public void dispatch(T message) {
        queue.offer(Optional.of(message));
    }

    @Nullable
    @Override
    public T receive() {
        T result = delegate.receive();
        LOGGER.debug("thread {}: received {}", Thread.currentThread().getId(), result == null ? "null" : result.getClass());
        return result;
    }

    @Override
    public void requestStop() {
        LOGGER.debug("thread {}: requesting stop for connection", Thread.currentThread().getId());
        stopped = true;
        delegate.requestStop();
    }

    @Override
    public void stop() {
        LOGGER.debug("thread {}: stopping connection", Thread.currentThread().getId());
        delegate.stop();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    public class Dispatcher implements Runnable {
        @Override
        public void run() {
            while (true) {
                if (stopped) {
                    break;
                }
                Optional<T> message = queue.poll();
                if (message != null) {
                    if (message.isPresent() && !(message.get() instanceof OutputMessage)) {
                        LOGGER.debug("thread {}: dispatching {}", Thread.currentThread().getId(), message.getClass());
                    }
                    delegate.dispatch(message.orNull());
                }
            }
        }
    }
}
