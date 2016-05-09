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

package org.gradle.api.internal.tasks.execution

import com.google.common.hash.HashCode
import org.gradle.api.Project
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.taskcache.TaskOutputPacker
import org.gradle.api.internal.changedetection.taskcache.TaskOutputReader
import org.gradle.api.internal.changedetection.taskcache.TaskOutputWriter
import org.gradle.api.internal.changedetection.taskcache.TaskResultCache
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.internal.tasks.cache.FileBasedTaskState
import org.gradle.api.internal.tasks.cache.TaskStateProvider
import spock.lang.Specification

public class SkipCachedTaskExecuterTest extends Specification {
    def delegate = Mock(TaskExecuter)
    def task = Mock(TaskInternal)
    def project = Mock(Project)
    def projectDir = Mock(File)
    def outputs = Mock(TaskOutputsInternal)
    def taskState = Mock(TaskStateInternal)
    def taskContext = Mock(TaskExecutionContext)
    def taskResultCache = Mock(TaskResultCache)
    def taskResultPacker = Mock(TaskOutputPacker)
    def taskStateProvider = Mock(TaskStateProvider)
    def taskCachingState = Mock(FileBasedTaskState)
    def cacheKey = Mock(HashCode)

    def executer = new SkipCachedTaskExecuter(taskResultCache, taskResultPacker, taskStateProvider, delegate)

    def "skip task when cached results exist"() {
        def cachedResult = Mock(TaskOutputReader)

        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.isCacheEnabled() >> true
        1 * outputs.isCacheAllowed() >> true

        1 * taskStateProvider.getTaskState(task) >> taskCachingState
        1 * taskCachingState.getTaskCacheKey() >> cacheKey
        1 * taskResultCache.get(cacheKey) >> cachedResult
        1 * taskResultPacker.unpack(outputs, cachedResult)
        1 * taskState.upToDate("CACHED")
        0 * _
    }

    def "executes task when no cached result is available"() {
        def cachedResult = Mock(TaskOutputWriter)

        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.isCacheEnabled() >> true
        1 * outputs.isCacheAllowed() >> true

        1 * taskStateProvider.getTaskState(task) >> taskCachingState
        1 * taskCachingState.getTaskCacheKey() >> cacheKey
        1 * taskResultCache.get(cacheKey) >> null

        then:
        1 * delegate.execute(task, taskState, taskContext)
        1 * taskState.getFailure() >> null

        then:
        1 * taskResultPacker.createWriter(outputs) >> cachedResult
        1 * taskResultCache.put(cacheKey, cachedResult)
        0 * _
    }

    def "does not cache results when executed task fails"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.isCacheEnabled() >> true
        1 * outputs.isCacheAllowed() >> true

        1 * taskStateProvider.getTaskState(task) >> taskCachingState
        1 * taskCachingState.getTaskCacheKey() >> cacheKey
        1 * taskResultCache.get(cacheKey) >> null

        then:
        1 * delegate.execute(task, taskState, taskContext)
        _ * taskState.getFailure() >> new RuntimeException()
        0 * _
    }

    def "executes task and does not cache results when cacheIf is false"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.isCacheAllowed() >> true
        1 * outputs.isCacheEnabled() >> false

        then:
        1 * delegate.execute(task, taskState, taskContext)
        0 * _
    }

    def "executes task and does not cache results when task is not allowed to use cache"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.isCacheAllowed() >> false

        then:
        1 * delegate.execute(task, taskState, taskContext)
        0 * _
    }
}
