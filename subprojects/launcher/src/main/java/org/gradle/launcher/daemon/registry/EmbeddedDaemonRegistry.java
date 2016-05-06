/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.registry;

import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.remote.Address;
import org.gradle.launcher.daemon.common.DaemonState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A daemon registry for daemons running in the same JVM.
 * <p>
 * This implementation is thread safe in that its getAll(), getIdle() and getBusy() methods are expected to be called from “consumer” threads,
 * while the newEntry() method is expected to be called by “producer” threads.
 * <p>
 * The collections returned by the consumer methods do not return live collections so may not reflect the precise state of the registry
 * by the time they are returned to the caller. Clients must therefore be prepared for this and expect connection failures, either through
 * the endpoint disappearing or becoming busy between asking for idle daemons and trying to connect.
 */
public class EmbeddedDaemonRegistry implements DaemonRegistry {
    private final Map<Address, DaemonInfo> daemonInfos = new ConcurrentHashMap<Address, DaemonInfo>();
    private final Spec<DaemonInfo> allSpec = new Spec<DaemonInfo>() {
        public boolean isSatisfiedBy(DaemonInfo entry) {
            return true;
        }
    };

    @SuppressWarnings("unchecked")
    private final Spec<DaemonInfo> availableSpec = Specs.<DaemonInfo>intersect(allSpec, new Spec<DaemonInfo>() {
        public boolean isSatisfiedBy(DaemonInfo daemonInfo) {
            return daemonInfo.getState() == DaemonState.Idle;
        }
    });

    @SuppressWarnings("unchecked")
    private final Spec<DaemonInfo> unavailableSpec = Specs.<DaemonInfo>intersect(allSpec, new Spec<DaemonInfo>() {
        public boolean isSatisfiedBy(DaemonInfo daemonInfo) {
            return daemonInfo.getState() != DaemonState.Idle;
        }
    });

    @SuppressWarnings("unchecked")
    private final Spec<DaemonInfo> startedSpec = Specs.<DaemonInfo>intersect(allSpec, new Spec<DaemonInfo>() {
        public boolean isSatisfiedBy(DaemonInfo daemonInfo) {
            return daemonInfo.getState() == DaemonState.Started;
        }
    });

    @SuppressWarnings("unchecked")
    private final Spec<DaemonInfo> stoppedSpec = Specs.<DaemonInfo>intersect(allSpec, new Spec<DaemonInfo>() {
        public boolean isSatisfiedBy(DaemonInfo daemonInfo) {
            return daemonInfo.getState() == DaemonState.Stopped;
        }
    });

    public List<DaemonInfo> getAll() {
        return daemonInfosOfEntriesMatching(allSpec);
    }

    @Override
    public List<DaemonInfo> getStarted() {
        return daemonInfosOfEntriesMatching(startedSpec);
    }

    public List<DaemonInfo> getIdle() {
        return daemonInfosOfEntriesMatching(availableSpec);
    }

    // FIXME(ew): Fix semantic mismatch of "busy" and "unavailable" here
    public List<DaemonInfo> getBusy() {
        return daemonInfosOfEntriesMatching(unavailableSpec);
    }

    @Override
    public List<DaemonInfo> getStopped() {
        return daemonInfosOfEntriesMatching(stoppedSpec);
    }

    public void store(DaemonInfo info) {
        daemonInfos.put(info.getAddress(), info);
    }

    public void remove(Address address) {
        daemonInfos.remove(address);
    }

    @Override
    public void markState(Address address, DaemonState state) {
        synchronized (daemonInfos) {
            daemonInfos.get(address).setState(state);
        }
    }

    private List<DaemonInfo> daemonInfosOfEntriesMatching(Spec<DaemonInfo> spec) {
        List<DaemonInfo> matches = new ArrayList<DaemonInfo>();
        for (DaemonInfo daemonInfo : daemonInfos.values()) {
            if (spec.isSatisfiedBy(daemonInfo)) {
                matches.add(daemonInfo);
            }
        }

        return matches;
    }
}
