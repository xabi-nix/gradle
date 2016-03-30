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
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DaemonInstanceDetails;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.registry.DaemonRegistry;

import java.util.List;

public class ExpireDaemonConnector implements DaemonConnector {
    private final List<DaemonExpirationStategy> expirationStrategies;
    private DefaultDaemonConnector delegate;
    private DaemonRegistry daemonRegistry;

    public ExpireDaemonConnector(DefaultDaemonConnector delegate, DaemonRegistry daemonRegistry, List<DaemonExpirationStategy> expirationStrategies) {
        this.delegate = delegate;
        this.expirationStrategies = expirationStrategies;
        this.daemonRegistry = daemonRegistry;
        System.out.println("expirationStrategies = " + expirationStrategies.size());
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
        // for now only consider daemons in idle mode to be expired
        final List<DaemonInfo> idleDaemons = daemonRegistry.getIdle();
        // remove the one we're going to pass/use from list of potential stoppable daemons
        idleDaemons.remove(connect);
        for (DaemonExpirationStategy expirationStrategy : expirationStrategies) {
            expirationStrategy.maybeExpireDaemons(delegate, idleDaemons);
        }
        return connect;
    }

    @Override
    public DaemonClientConnection startDaemon(ExplainingSpec<DaemonContext> constraint) {
        return delegate.connect(constraint);
    }
}
