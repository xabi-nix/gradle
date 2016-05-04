/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks;

import com.google.common.collect.Maps;
import groovy.lang.Closure;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskOutputs;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class DefaultTaskOutputs implements TaskOutputsInternal {
    private static final String DEFAULT_PROPERTY = "$default";

    private final Map<String, TaskPropertyOutput> propertyOutputs = Maps.newTreeMap();
    private AndSpec<TaskInternal> upToDateSpec = new AndSpec<TaskInternal>();
    private AndSpec<TaskInternal> cacheIfSpec = new AndSpec<TaskInternal>();
    private TaskExecutionHistory history;
    private final FileResolver resolver;
    private final TaskInternal task;
    private final TaskMutator taskMutator;
    private ConfigurableFileCollection files;

    public DefaultTaskOutputs(FileResolver resolver, TaskInternal task, TaskMutator taskMutator) {
        this.resolver = resolver;
        this.task = task;
        this.taskMutator = taskMutator;
    }

    @Override
    public Spec<? super TaskInternal> getUpToDateSpec() {
        return upToDateSpec;
    }

    @Override
    public boolean isCacheEnabled() {
        return !cacheIfSpec.getSpecs().isEmpty() && cacheIfSpec.isSatisfiedBy(task);
    }

    @Override
    public boolean isCacheAllowed() {
        // If there's nothing to cache, we don't allow caching
        if (propertyOutputs.isEmpty()) {
            return false;
        }
        for (TaskPropertyOutput output : propertyOutputs.values()) {
            // When a single property refers to multiple outputs, we don't know how to cache those
            if (output instanceof MultiPathTaskPropertyOutput) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void upToDateWhen(final Closure upToDateClosure) {
        taskMutator.mutate("TaskOutputs.upToDateWhen(Closure)", new Runnable() {
            @Override
            public void run() {
                upToDateSpec = upToDateSpec.and(upToDateClosure);
            }
        });
    }

    @Override
    public void upToDateWhen(final Spec<? super Task> spec) {
        taskMutator.mutate("TaskOutputs.upToDateWhen(Spec)", new Runnable() {
            @Override
            public void run() {
                upToDateSpec = upToDateSpec.and(spec);
            }
        });
    }

    @Override
    public void cacheIf(final Closure closure) {
        taskMutator.mutate("TaskOutputs.cacheIf(Closure)", new Runnable() {
            public void run() {
                cacheIfSpec = cacheIfSpec.and(closure);
            }
        });
    }

    @Override
    public void cacheIf(final Spec<? super Task> spec) {
        taskMutator.mutate("TaskOutputs.cacheIf(Spec)", new Runnable() {
            public void run() {
                cacheIfSpec = cacheIfSpec.and(spec);
            }
        });
    }

    @Override
    public boolean getHasOutput() {
        return !propertyOutputs.isEmpty() || !upToDateSpec.getSpecs().isEmpty();
    }

    @Override
    public FileCollection getFiles() {
        if (files == null) {
            files = new DefaultConfigurableFileCollection(task + " output files", resolver, null)
                .builtBy(task);
            for (TaskPropertyOutput output : propertyOutputs.values()) {
                output.collectFiles(files);
            }
        }
        return files;
    }

    @Override
    public Collection<TaskPropertyOutput> getPropertyOutputs() {
        return Collections.unmodifiableCollection(propertyOutputs.values());
    }

    @Override
    public TaskPropertyOutput getPropertyOutput(String propertyName) {
        TaskPropertyOutput propertyOutput = propertyOutputs.get(propertyName);
        if (propertyOutput == null) {
            throw new IllegalArgumentException(String.format("No output property '%s' registered for %s", propertyName, task));
        }
        return propertyOutput;
    }

    @Override
    public TaskOutputs files(final Object... paths) {
        taskMutator.mutate("TaskOutputs.files(Object...)", new Runnable() {
            @Override
            public void run() {
                addMultiplePropertyOutput(DEFAULT_PROPERTY, paths);
            }
        });
        return this;
    }

    @Override
    public TaskOutputs includeFiles(final String property, final Object... paths) {
        taskMutator.mutate("TaskOutputs.includeFiles(String, Object...)", new Runnable() {
            @Override
            public void run() {
                addMultiplePropertyOutput(property, paths);
            }
        });
        return this;
    }

    @Override
    public TaskOutputs file(final Object path) {
        taskMutator.mutate("TaskOutputs.file(Object)", new Runnable() {
            @Override
            public void run() {
                addMultiplePropertyOutput(DEFAULT_PROPERTY, path);
            }
        });
        return this;
    }

    @Override
    public TaskOutputs includeFile(final String property, final Object path) {
        taskMutator.mutate("TaskOutputs.includeFile(String, Object)", new Runnable() {
            @Override
            public void run() {
                addPropertyOutput(new OutputFileTaskPropertyOutput(property, path, resolver));
            }
        });
        return this;
    }

    @Override
    public TaskOutputs dir(final Object path) {
        taskMutator.mutate("TaskOutputs.dir(Object)", new Runnable() {
            @Override
            public void run() {
                addMultiplePropertyOutput(DEFAULT_PROPERTY, path);
            }
        });
        return this;
    }

    @Override
    public TaskOutputs includeDir(final String property, final Object path) {
        taskMutator.mutate("TaskOutputs.includeDir(String, FileOrderMode, Object)", new Runnable() {
            @Override
            public void run() {
                addPropertyOutput(new OutputDirectoryTaskPropertyOutput(property, path, resolver));
            }
        });
        return this;
    }

    private void addPropertyOutput(TaskPropertyOutput output) {
        String property = output.getProperty();
        if (propertyOutputs.containsKey(property)) {
            throw new IllegalStateException(String.format("Property '%s' is already registered as an output of %s", property, task));
        }
        propertyOutputs.put(property, output);
    }

    private void addMultiplePropertyOutput(String property, Object... paths) {
        TaskPropertyOutput output = propertyOutputs.get(property);
        if (output == null) {
            propertyOutputs.put(property, new MultiPathTaskPropertyOutput(property, paths));
        } else {
            if (output instanceof MultiPathTaskPropertyOutput) {
                ((MultiPathTaskPropertyOutput) output).addPaths(paths);
            } else {
                throw new IllegalStateException(String.format("Property '%s' is already registered as an output of %s", property, task));
            }
        }
    }

    @Override
    public FileCollection getPreviousFiles() {
        if (history == null) {
            throw new IllegalStateException("Task history is currently not available for this task.");
        }
        return history.getOutputFiles();
    }

    @Override
    public void setHistory(TaskExecutionHistory history) {
        this.history = history;
    }
}
