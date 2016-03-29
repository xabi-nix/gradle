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

package org.gradle.launcher.daemon.client;

import org.gradle.api.Nullable;
import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.internal.id.RandomLongIdGenerator;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DaemonInstanceDetails;
import org.gradle.launcher.daemon.protocol.Stop;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.util.CollectionUtils;

import java.util.Comparator;
import java.util.List;

public class ExpireDaemonConnector implements DaemonConnector {
    private DefaultDaemonConnector delegate;
    private DaemonRegistry daemonRegistry;

    private static String MAX_DAEMON_COUNT_SYS_PROP_KEY = "org.gradle.daemon.expiration.maxnumber";
    private static int DEFAULT_MAX_DAEMON_COUNT = 2;
    private final int maxDaemonCount;

    public ExpireDaemonConnector(DefaultDaemonConnector delegate, DaemonRegistry daemonRegistry) {
        this.delegate = delegate;
        this.maxDaemonCount = System.getProperty(MAX_DAEMON_COUNT_SYS_PROP_KEY) == null ? DEFAULT_MAX_DAEMON_COUNT : Integer.parseInt(System.getProperty(MAX_DAEMON_COUNT_SYS_PROP_KEY));
        this.daemonRegistry = daemonRegistry;
        System.out.println("maxDaemonCount = " + maxDaemonCount);
    }

    @Nullable
    @Override
    public DaemonClientConnection maybeConnect(DaemonInstanceDetails daemonAddress) {
        return delegate.maybeConnect(daemonAddress);
    }

    @Nullable
    @Override
    public DaemonClientConnection maybeConnect(ExplainingSpec<DaemonContext> constraint) {
        return delegate.maybeConnect(constraint);
    }

    @Override
    public DaemonClientConnection connect(ExplainingSpec<DaemonContext> constraint) {
        final DaemonClientConnection connect = delegate.connect(constraint);
        // 1st try reducing overall daemons by shutting down idle daemons
        final List<DaemonInfo> idleDaemons = daemonRegistry.getIdle();
        // remove the one we're going to pass/use from list of potential stoppable daemons
        idleDaemons.remove(connect);
        final List<DaemonInfo> idleDaemonsByUsage = CollectionUtils.sort(idleDaemons, new Comparator<DaemonInfo>() {
            @Override
            public int compare(DaemonInfo o1, DaemonInfo o2) {
                return o1.getLastProcessedBuild().compareTo(o2.getLastProcessedBuild());
            }
        });

        if (idleDaemonsByUsage.size() >= maxDaemonCount) {
            for (DaemonInfo stopCandidateDaemon : idleDaemonsByUsage.subList(0, idleDaemonsByUsage.size() - maxDaemonCount + 1)) {
                final DaemonClientConnection daemonClientConnection = delegate.maybeConnect(stopCandidateDaemon);
                if (daemonClientConnection != null) {
                    daemonClientConnection.dispatch(new Stop(new RandomLongIdGenerator().generateId()));
                }
            }
        }
        return connect;
    }

    @Override
    public DaemonClientConnection startDaemon(ExplainingSpec<DaemonContext> constraint) {
        return delegate.connect(constraint);
    }
}
