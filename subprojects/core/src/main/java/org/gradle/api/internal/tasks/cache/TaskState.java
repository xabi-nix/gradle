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

/**
 * Describes the state of a task at a given point in time.
 */
public interface TaskState {

    /**
     * Compares this {@code TaskState} with a {@link FileBasedTaskState} and reports differences found between them via the given {@code reporter}.
     */
    void reportDifferences(FileBasedTaskState other, TaskStateComparisonReporter reporter);

    /**
     * Returns a {@link HashCode} that identifies this state in the task cache.
     */
    HashCode getTaskCacheKey();

    interface TaskStateComparisonReporter {
        /**
         * Report a difference with the given message.
         *
         * @return {@code true} if further differences need to be detected, or {@code false} if checking for differences should stop.
         */
        boolean reportDifference(Object... message);

        TaskStateComparisonReporter withPrefix(Object... prefix);

        Iterable<? extends Iterable<Object>> getDifferences();

        /**
         * Returns whether any differences were found.
         */
        boolean hasDifferences();
    }
}
