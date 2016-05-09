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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;

public class DefaultTaskStateComparisonReporter implements TaskState.TaskStateComparisonReporter {
    private final int maxCount;
    private final List<List<Object>> differences;

    public DefaultTaskStateComparisonReporter(int maxCount) {
        this.maxCount = maxCount;
        this.differences = Lists.newArrayListWithCapacity(maxCount);
    }

    @Override
    public boolean reportDifference(Object... message) {
        if (differences.size() < maxCount) {
            differences.add(Arrays.asList(message));
        }
        return differences.size() < maxCount;
    }

    @Override
    public TaskState.TaskStateComparisonReporter withPrefix(Object... prefix) {
        return new PrefixingReporter(this, prefix);
    }

    @Override
    public Iterable<? extends Iterable<Object>> getDifferences() {
        return differences;
    }

    @Override
    public boolean hasDifferences() {
        return !differences.isEmpty();
    }

    private class PrefixingReporter implements TaskState.TaskStateComparisonReporter {
        private final TaskState.TaskStateComparisonReporter delegate;
        private final List<Object> prefix;

        public PrefixingReporter(TaskState.TaskStateComparisonReporter delegate, Object... prefix) {
            this.delegate = delegate;
            this.prefix = Arrays.asList(prefix);
        }

        @Override
        public boolean reportDifference(Object... message) {
            return delegate.reportDifference(message);
        }

        @Override
        public TaskState.TaskStateComparisonReporter withPrefix(Object... prefix) {
            return new PrefixingReporter(this, prefix);
        }

        @Override
        public Iterable<? extends Iterable<Object>> getDifferences() {
            Iterable<? extends Iterable<Object>> delegateDifferences = delegate.getDifferences();
            return Iterables.transform(delegateDifferences, new Function<Iterable<Object>, Iterable<Object>>() {
                @Override
                public Iterable<Object> apply(Iterable<Object> message) {
                    return Iterables.concat(prefix, message);
                }
            });
        }

        @Override
        public boolean hasDifferences() {
            return delegate.hasDifferences();
        }
    }
}
