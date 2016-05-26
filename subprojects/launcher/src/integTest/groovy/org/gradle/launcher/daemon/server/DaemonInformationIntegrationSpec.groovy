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

package org.gradle.launcher.daemon.server

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec

class DaemonInformationIntegrationSpec extends DaemonIntegrationSpec {

    def "should capture basic data via the service registry"() {
        given:
        buildFile << """
           def healthService = project.getServices().get(org.gradle.launcher.daemon.server.health.DaemonHealthServices)
           org.gradle.launcher.daemon.server.health.DaemonInformation info = healthService.getDaemonInformation()

           assert info.getNumberOfBuilds() == 1
           assert info.getIdleTimeout() == 120000
           assert info.getNumberOfRunningDaemons() == 1
           assert info.getStartedAt() <= System.currentTimeMillis()
        """

        expect:
        buildSucceeds()

    }

    def "The daemon expiration listener is fired"() {
        given:
        buildFile << """
           import org.gradle.launcher.daemon.server.DaemonExpirationListener
           import org.gradle.launcher.daemon.server.DaemonExpirationStrategy
           import org.gradle.launcher.daemon.server.DaemonExpirationResult
           import org.gradle.launcher.daemon.server.AllDaemonExpirationStrategy
           import org.gradle.launcher.daemon.server.DaemonExpirationStatus

           class TestExpirationStrategy implements DaemonExpirationStrategy {
                @Override
                public DaemonExpirationResult checkExpiration() {
                    return new DaemonExpirationResult(DaemonExpirationStatus.GRACEFUL_EXPIRE, "expiring daemon with TestExpirationStrategy")
                }
            }

           def daemon =  project.getServices().get(org.gradle.launcher.daemon.server.Daemon)
           daemon.scheduleExpirationChecks(new AllDaemonExpirationStrategy([new TestExpirationStrategy()]), 500)

           def registry = project.getServices().get(org.gradle.launcher.daemon.server.DaemonExpirationListenerRegistry)
           registry.register(new DaemonExpirationListener() {
                @Override
                public void onExpirationEvent(org.gradle.launcher.daemon.server.DaemonExpirationResult result) {
                    println "onExpirationEvent fired with: \${result.getReason()}"
                }
            });

            task delay {
                doFirst{
                 sleep(1000)
                }
            }
        """
        when:
        run("delay")

        then:
        outputContains('onExpirationEvent fired with: expiring daemon with TestExpirationStrategy')

    }
}
