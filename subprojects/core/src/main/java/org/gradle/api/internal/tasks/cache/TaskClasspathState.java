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

package org.gradle.api.internal.tasks.cache;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import org.gradle.api.internal.changedetection.taskcache.CacheKeyBuilder;

public class TaskClasspathState implements TaskStateElement<TaskClasspathState> {
    private static final byte[] CLASSPATH_HASH_CODE = "CLASSPATH_HASH".getBytes(Charsets.UTF_8);

    private final HashCode classpathHash;

    public TaskClasspathState(HashCode classpathHash) {
        this.classpathHash = classpathHash;
    }

    @Override
    public boolean reportDifferences(TaskClasspathState previousState, TaskState.TaskStateComparisonReporter reporter) {
        return classpathHash.equals(previousState.classpathHash) || reporter.reportDifference("Task classpath is different");
    }

    @Override
    public void appendToCacheKey(CacheKeyBuilder keyBuilder) {
        keyBuilder.put(CLASSPATH_HASH_CODE);
        keyBuilder.put(classpathHash.asBytes());
    }
}
