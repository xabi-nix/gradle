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

package org.gradle.api.internal.project.taskfactory

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskInternal
import org.gradle.api.tasks.TaskInputs
import spock.lang.Specification

class DependencyAutoWireTaskFactoryTest extends Specification {
    def delegate = Mock(ITaskFactory)
    def factory = new DependencyAutoWireTaskFactory(delegate)

    def "adds dependency on input files"() {
        def task = Mock(TaskInternal)
        def taskInputs = Mock(TaskInputs)
        def inputFiles = Mock(FileCollection)

        when:
        def result = factory.createTask([:])

        then:
        result == task
        1 * delegate.createTask(_) >> task
        _ * task.getInputs() >> taskInputs
        _ * taskInputs.getFiles() >> inputFiles
        1 * task.dependsOn(_) >> { List args ->
            assert args[0][0].call() == inputFiles
        }
    }
}
