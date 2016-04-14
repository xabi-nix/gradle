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

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;
import groovy.lang.GString;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.taskcache.CacheKeyBuilder;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.FileContentsMode;
import org.gradle.api.tasks.FileOrderMode;
import org.gradle.api.tasks.FilePathMode;
import org.gradle.api.tasks.TaskInputs;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.tasks.TaskPropertyFiles.DEFAULT_PROPERTY;
import static org.gradle.util.GUtil.uncheckedCall;

public class DefaultTaskInputs implements TaskInputs {
    private static final Predicate<TaskPropertyFiles.Entry> SKIP_WHEN_EMPTY_ENTRIES = new Predicate<TaskPropertyFiles.Entry>() {
        @Override
        public boolean apply(TaskPropertyFiles.Entry entry) {
            return entry.isSkipWhenEmpty();
        }
    };

    private final TaskPropertyFiles propertyFiles;
    private final TaskMutator taskMutator;
    private final Map<String, Object> properties = Maps.newTreeMap();
    private FileCollection files;
    private FileCollection sourceFiles;

    public DefaultTaskInputs(FileResolver resolver, TaskInternal task, TaskMutator taskMutator) {
        this.taskMutator = taskMutator;
        this.propertyFiles = new TaskPropertyFiles(task + " input files", resolver);
    }

    @Override
    public boolean getHasInputs() {
        return propertyFiles.hasEntries(Predicates.alwaysTrue()) || !properties.isEmpty();
    }

    @Override
    public FileCollection getFiles() {
        if (files == null) {
            files = propertyFiles.collectFiles(Predicates.alwaysTrue());
        }
        return files;
    }

    @Override
    public void appendToCacheKey(CacheKeyBuilder keyBuilder) {
        // TODO Allow specifying file ordering and path mode for @Input properties
        keyBuilder.put(properties);
        propertyFiles.appendToCacheKey(keyBuilder, true);
    }

    @Override
    public TaskInputs files(final Object... paths) {
        taskMutator.mutate("TaskInputs.files(Object...)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.files(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, FilePathMode.ABSOLUTE, FileContentsMode.USE, false, paths);
            }
        });
        return this;
    }

    @Override
    public TaskInputs files(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object... paths) {
        taskMutator.mutate("TaskInputs.files(String, FileOrderMode, FilePathMode, FileContentsMode, Object...)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.files(property, orderMode, pathMode, contentsMode, false, paths);
            }
        });
        return this;
    }

    @Override
    public TaskInputs file(final Object path) {
        taskMutator.mutate("TaskInputs.file(Object)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.files(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, FilePathMode.ABSOLUTE, FileContentsMode.USE, false, path);
            }
        });
        return this;
    }

    @Override
    public TaskInputs file(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object path) {
        taskMutator.mutate("TaskInputs.file(String, FileOrderMode, FilePathMode, FileContentsMode, Object)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.files(property, orderMode, pathMode, contentsMode, false, path);
            }
        });
        return this;
    }

    @Override
    public TaskInputs dir(final Object dirPath) {
        taskMutator.mutate("TaskInputs.dir(Object)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.dirs(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, FilePathMode.ABSOLUTE, FileContentsMode.USE, false, dirPath);
            }
        });
        return this;
    }

    @Override
    public TaskInputs dir(String property, FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, final Object path) {
        taskMutator.mutate("TaskInputs.dir(String, FileOrderMode, FilePathMode, FileContentsMode, Object)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.dirs(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, FilePathMode.ABSOLUTE, FileContentsMode.USE, false, path);
            }
        });
        return this;
    }

    @Override
    public boolean getHasSourceFiles() {
        return propertyFiles.hasEntries(SKIP_WHEN_EMPTY_ENTRIES);
    }

    @Override
    public FileCollection getSourceFiles() {
        if (sourceFiles == null) {
            sourceFiles = propertyFiles.collectFiles(SKIP_WHEN_EMPTY_ENTRIES);
        }
        return sourceFiles;
    }

    @Override
    public TaskInputs source(final Object... paths) {
        taskMutator.mutate("TaskInputs.source(Object...)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.files(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, FilePathMode.ABSOLUTE, FileContentsMode.USE, true, paths);
            }
        });
        return this;
    }

    @Override
    public TaskInputs source(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object... paths) {
        taskMutator.mutate("TaskInputs.source(String, FileOrderMode, FilePathMode, FileContentsMode, Object...)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.files(property, orderMode, pathMode, contentsMode, true, paths);
            }
        });
        return this;
    }

    @Override
    public TaskInputs source(final Object path) {
        taskMutator.mutate("TaskInputs.source(Object)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.files(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, FilePathMode.ABSOLUTE, FileContentsMode.USE, true, path);
            }
        });
        return this;
    }

    @Override
    public TaskInputs source(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object path) {
        taskMutator.mutate("TaskInputs.source(String, FileOrderMode, FilePathMode, FileContentsMode, Object)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.files(property, orderMode, pathMode, contentsMode, true, path);
            }
        });
        return this;
    }

    @Override
    public TaskInputs sourceDir(final Object path) {
        taskMutator.mutate("TaskInputs.sourceDir(Object)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.dirs(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, FilePathMode.ABSOLUTE, FileContentsMode.USE, true, path);
            }
        });
        return this;
    }

    @Override
    public TaskInputs sourceDir(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object path) {
        taskMutator.mutate("TaskInputs.sourceDir(String, FileOrderMode, FilePathMode, FileContentsMode, Object...)", new Runnable() {
            @Override
            public void run() {
                propertyFiles.dirs(property, orderMode, pathMode, contentsMode, true, path);
            }
        });
        return this;
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> actualProperties = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Object value = prepareValue(entry.getValue());
            actualProperties.put(entry.getKey(), value);
        }
        return actualProperties;
    }

    private Object prepareValue(Object value) {
        while (true) {
            if (value instanceof Callable) {
                Callable callable = (Callable) value;
                value = uncheckedCall(callable);
            } else if (value instanceof FileCollection) {
                FileCollection fileCollection = (FileCollection) value;
                return fileCollection.getFiles();
            } else {
                return avoidGString(value);
            }
        }
    }

    private static Object avoidGString(Object value) {
        return (value instanceof GString) ? value.toString() : value;
    }

    @Override
    public TaskInputs property(final String name, final Object value) {
        taskMutator.mutate("TaskInputs.property(String, Object)", new Runnable() {
            @Override
            public void run() {
                properties.put(name, value);
            }
        });
        return this;
    }

    @Override
    public TaskInputs properties(final Map<String, ?> newProps) {
        taskMutator.mutate("TaskInputs.properties(Map)", new Runnable() {
            @Override
            public void run() {
                properties.putAll(newProps);
            }
        });
        return this;
    }
}
