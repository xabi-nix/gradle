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

import org.gradle.api.internal.changedetection.taskcache.CacheKeyBuilder;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class TaskPropertiesState implements TaskStateElement<TaskPropertiesState> {
    private final SortedMap<String, Object> properties;

    public TaskPropertiesState(Map<String, Object> properties) {
        // Cannot use ImmutableSortedMap because of potential null values :(
        this.properties = new TreeMap<String, Object>(properties);
    }

    @Override
    public boolean reportDifferences(TaskPropertiesState previousState, final TaskState.TaskStateComparisonReporter reporter) {
        return DiffUtil.diff(previousState.properties, properties, new DiffUtil.Listener<String>() {
            @Override
            public boolean added(String key) {
                return reporter.reportDifference("input property '", key, "' has been added");
            }

            @Override
            public boolean removed(String key) {
                return reporter.reportDifference("input property '", key, "' has been removed");
            }

            @Override
            public boolean changed(String key) {
                return reporter.reportDifference("input property '", key, "' has changed");
            }
        });
    }

    @Override
    public void appendToCacheKey(CacheKeyBuilder keyBuilder) {
        keyBuilder.put(properties);
    }
}
