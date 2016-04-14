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

import com.google.common.base.Predicates;
import groovy.lang.Closure;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.changedetection.taskcache.CacheKeyBuilder;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.FileContentsMode;
import org.gradle.api.tasks.FileOrderMode;
import org.gradle.api.tasks.FilePathMode;
import org.gradle.api.tasks.TaskOutputs;

import static org.gradle.api.internal.tasks.TaskPropertyFiles.DEFAULT_PROPERTY;

public class DefaultTaskOutputs implements TaskOutputsInternal {
    private final TaskPropertyFiles propertyFiles;
    private AndSpec<TaskInternal> upToDateSpec = new AndSpec<TaskInternal>();
    private AndSpec<TaskInternal> cacheIfSpec = new AndSpec<TaskInternal>();
    private TaskExecutionHistory history;
    private final TaskInternal task;
    private final TaskMutator taskMutator;
    private ConfigurableFileCollection files;

    public DefaultTaskOutputs(FileResolver resolver, TaskInternal task, TaskMutator taskMutator) {
        this.task = task;
        this.taskMutator = taskMutator;
        this.propertyFiles = new TaskPropertyFiles(task + " output files", resolver);
    }

    @Override
    public void appendToCacheKey(CacheKeyBuilder keyBuilder) {
        propertyFiles.appendToCacheKey(keyBuilder, false);
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
        return getDeclaresOutput() || !upToDateSpec.getSpecs().isEmpty();
    }

    @Override
    public boolean getDeclaresOutput() {
        return propertyFiles.hasEntries(Predicates.alwaysTrue());
    }

    @Override
    public FileCollection getFiles() {
        if (files == null) {
            this.files = propertyFiles.collectFiles(Predicates.alwaysTrue())
                .builtBy(task);
        }
        return files;
    }

    @Override
    public TaskOutputs files(final Object... paths) {
        taskMutator.mutate("TaskOutputs.files(Object...)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.files(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, FilePathMode.ABSOLUTE, FileContentsMode.USE, false, paths);
            }
        });
        return this;
    }

    @Override
    public TaskOutputs files(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object... paths) {
        taskMutator.mutate("TaskOutputs.files(String, FileOrderMode, FilePathMode, FileContentsMode, Object...)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.files(property, orderMode, pathMode, contentsMode, false, paths);
            }
        });
        return this;
    }

    @Override
    public TaskOutputs file(final Object path) {
        taskMutator.mutate("TaskOutputs.file(Object)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.files(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, FilePathMode.ABSOLUTE, FileContentsMode.USE, false, path);
            }
        });
        return this;
    }

    @Override
    public TaskOutputs file(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object path) {
        taskMutator.mutate("TaskOutputs.file(String, FileOrderMode, FilePathMode, FileContentsMode, Object)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.files(property, orderMode, pathMode, contentsMode, false, path);
            }
        });
        return this;
    }

    @Override
    public TaskOutputs dir(final Object path) {
        taskMutator.mutate("TaskOutputs.dir(Object)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.dirs(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, FilePathMode.ABSOLUTE, FileContentsMode.USE, false, path);
            }
        });
        return this;
    }

    @Override
    public TaskOutputs dir(String property, FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, final Object path) {
        taskMutator.mutate("TaskOutputs.dir(String, FileOrderMode, FilePathMode, FileContentsMode, Object)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.dirs(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, FilePathMode.ABSOLUTE, FileContentsMode.USE, false, path);
            }
        });
        return this;
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
