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
import org.gradle.internal.UncheckedException;
import org.gradle.launcher.daemon.protocol.Failure;
import org.gradle.launcher.daemon.protocol.OutputMessage;
import org.gradle.messaging.remote.internal.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AsynchronousQueueDrainedOnFailureDispatchConnection<T> implements Connection<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsynchronousQueueDrainedOnFailureDispatchConnection.class);
    private static final AtomicInteger THREAD_NAME_SUFFIX = new AtomicInteger(0);
    private final Connection<T> delegate;
    private final BlockingQueue<Optional<T>> queue;
    private final Thread dispatcherThread;
    private final Lock lock = new ReentrantLock();
    private final Condition drained = lock.newCondition();
    private final Condition dispatched = lock.newCondition();
    private volatile boolean stopped;

    public AsynchronousQueueDrainedOnFailureDispatchConnection(Connection<T> delegate) {
        this.delegate = delegate;
        this.queue = new LinkedBlockingQueue<Optional<T>>();
        this.dispatcherThread = new Thread(new Dispatcher(), "daemon-async-dispatcher-" + THREAD_NAME_SUFFIX.getAndIncrement());
        this.dispatcherThread.start();
    }

    @Override
    public void dispatch(T message) {
        if (message instanceof Failure) {
            // Enqueue, then wait for drain and dispatch
            lock.lock();
            try {
                queue.put(Optional.of(message));
                drained.await();
                dispatched.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UncheckedException(e);
            } finally {
                lock.unlock();
            }
        } else {
            // Offer message
            boolean overCapacity = !queue.offer(Optional.of(message));
            if (overCapacity) {
                // Wait for available space
                try {
                    queue.put(Optional.of(message));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new UncheckedException(e);
                }
            }
        }
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
        // Wait for drain and dispatch if any message queued
        /*
        lock.lock();
        try {
            if (queue.peek() != null) {
                drained.await();
                dispatched.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedException(e);
        } finally {
            lock.unlock();
        }
        */
        delegate.requestStop();
    }

    @Override
    public void stop() {
        LOGGER.debug("thread {}: stopping connection", Thread.currentThread().getId());
        // Wait for drain and dispatch if any message queued, then stop the dispatch thread
        lock.lock();
        try {
            drained.await();
            dispatched.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedException(e);
        } finally {
            stopped = true;
            lock.unlock();
        }
        delegate.stop();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    private class Dispatcher implements Runnable {
        @Override
        public void run() {
            List<Optional<T>> messages = new ArrayList<Optional<T>>();
            while (true) {
                if (stopped) {
                    break;
                }
                lock.lock();
                try {
                    queue.drainTo(messages);
                    drained.signal();
                    for (Optional<T> message : messages) {
                        if (message.isPresent() && !(message.get() instanceof OutputMessage)) {
                            LOGGER.debug("thread {}: dispatching {}", Thread.currentThread().getId(), message.getClass());
                        }
                        delegate.dispatch(message.orNull());
                    }
                    messages.clear();
                    dispatched.signal();
                } finally {
                    lock.unlock();
                }
            }
        }
    }
}
