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

import com.google.common.hash.HashCode;
import org.gradle.api.internal.changedetection.taskcache.CacheKeyBuilder;

// TODO:LPTR Include discovered inputs
public class DefaultFileBasedTaskState implements FileBasedTaskState {
    private final TaskClasspathState classpathState;
    private final TaskPropertiesState inputPropertiesState;
    private final TaskFilesState inputFilesState;
    private final TaskFilesState outputFilesState;
    private HashCode cacheKey;

    public DefaultFileBasedTaskState(TaskClasspathState classpathState, TaskPropertiesState inputPropertiesState, TaskFilesState inputFilesState, TaskFilesState outputFilesState) {
        this.classpathState = classpathState;
        this.inputPropertiesState = inputPropertiesState;
        this.inputFilesState = inputFilesState;
        this.outputFilesState = outputFilesState;
    }

    @Override
    public void reportDifferences(FileBasedTaskState other, TaskStateComparisonReporter reporter) {
        if (classpathState.reportDifferences(other.getClasspathState(), reporter.withPrefix("classpath "))) {
            if (inputPropertiesState.reportDifferences(other.getInputPropertiesState(), reporter)) {
                if (inputFilesState.reportDifferences(other.getInputFilesState(), reporter.withPrefix("input "))) {
                    outputFilesState.reportDifferences(other.getOutputFilesState(), reporter.withPrefix("output "));
                }
            }
        }
    }

    @Override
    public HashCode getTaskCacheKey() {
        if (cacheKey == null) {
            CacheKeyBuilder keyBuilder = new CacheKeyBuilder();
            classpathState.appendToCacheKey(keyBuilder);
            inputPropertiesState.appendToCacheKey(keyBuilder);
            inputFilesState.appendToCacheKey(keyBuilder);
            cacheKey = keyBuilder.build();
        }
        return cacheKey;
    }

    @Override
    public TaskClasspathState getClasspathState() {
        return classpathState;
    }

    @Override
    public TaskFilesState getInputFilesState() {
        return inputFilesState;
    }

    @Override
    public TaskPropertiesState getInputPropertiesState() {
        return inputPropertiesState;
    }

    @Override
    public TaskFilesState getOutputFilesState() {
        return outputFilesState;
    }
}
