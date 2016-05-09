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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import groovy.lang.GString;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.tasks.FileContentsMode;
import org.gradle.api.tasks.FileOrderMode;
import org.gradle.api.tasks.FilePathMode;
import org.gradle.api.tasks.TaskInputs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.gradle.api.tasks.FilePathMode.ABSOLUTE;
import static org.gradle.util.GUtil.uncheckedCall;

public class DefaultTaskInputs implements TaskInputsInternal {
    private static final Predicate<TaskPropertyInputFiles> SKIP_WHEN_EMPTY_ENTRIES = new Predicate<TaskPropertyInputFiles>() {
        @Override
        public boolean apply(TaskPropertyInputFiles input) {
            return input.isSkipWhenEmpty();
        }
    };
    private static String DEFAULT_PROPERTY_PREFIX = "$default$";
    private int defaultPropertyCounter;

    private final String description;
    private final FileResolver resolver;
    private final TaskMutator taskMutator;
    private final Map<String, Object> properties = Maps.newTreeMap();
    private final Map<String, TaskPropertyInputFiles> propertyFiles = Maps.newTreeMap();
    private FileCollection files;
    private FileCollection sourceFiles;

    public DefaultTaskInputs(FileResolver resolver, TaskInternal task, TaskMutator taskMutator) {
        this.taskMutator = taskMutator;
        this.description = task + " input files";
        this.resolver = resolver;
    }

    @Override
    public boolean getHasInputs() {
        return !propertyFiles.isEmpty() || !properties.isEmpty();
    }

    @Override
    public FileCollection getFiles() {
        if (files == null) {
            files = collectFiles(Predicates.alwaysTrue());
        }
        return files;
    }

    private ConfigurableFileCollection collectFiles(Predicate<? super TaskPropertyInputFiles> predicate) {
        ConfigurableFileCollection files = new DefaultConfigurableFileCollection(description, resolver, null);
        for (TaskPropertyInputFiles input : propertyFiles.values()) {
            if (predicate.apply(input)) {
                files.from(input.resolve(resolver));
            }
        }
        return files;
    }

    @Override
    public TaskInputs files(final Object... paths) {
        taskMutator.mutate("TaskInputs.files(Object...)", new Runnable() {
            @Override
            public void run() {
                addFilePropertyInput(DEFAULT_PROPERTY_PREFIX + defaultPropertyCounter++, new DefaultTaskPropertyInputDirectories(FileOrderMode.UNORDERED, ABSOLUTE, FileContentsMode.USE, false, paths));
            }
        });
        return this;
    }

    @Override
    public TaskInputs includeFiles(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object... paths) {
        taskMutator.mutate("TaskInputs.includeFiles(String, FileOrderMode, FilePathMode, FileContentsMode, Object...)", new Runnable() {
            @Override
            public void run() {
                addFilePropertyInput(property, new DefaultTaskPropertyInputDirectories(orderMode, pathMode, contentsMode, false, paths));
            }
        });
        return this;
    }

    @Override
    public TaskInputs file(final Object path) {
        taskMutator.mutate("TaskInputs.file(Object)", new Runnable() {
            @Override
            public void run() {
                addFilePropertyInput(DEFAULT_PROPERTY_PREFIX + defaultPropertyCounter++, new DefaultTaskPropertyInputDirectories(FileOrderMode.UNORDERED, ABSOLUTE, FileContentsMode.USE, false, path));
            }
        });
        return this;
    }

    @Override
    public TaskInputs includeFile(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object path) {
        taskMutator.mutate("TaskInputs.includeFile(String, FileOrderMode, FilePathMode, FileContentsMode, Object)", new Runnable() {
            @Override
            public void run() {
                addFilePropertyInput(property, new DefaultTaskPropertyInputDirectories(orderMode, pathMode, contentsMode, false, path));
            }
        });
        return this;
    }

    @Override
    public TaskInputs dir(final Object dirPath) {
        taskMutator.mutate("TaskInputs.dir(Object)", new Runnable() {
            @Override
            public void run() {
                addFilePropertyInput(DEFAULT_PROPERTY_PREFIX + defaultPropertyCounter++, new DefaultTaskPropertyInputFiles(FileOrderMode.UNORDERED, ABSOLUTE, FileContentsMode.USE, false, dirPath));
            }
        });
        return this;
    }

    @Override
    public TaskInputs includeDir(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object path) {
        taskMutator.mutate("TaskInputs.includeDir(String, FileOrderMode, FilePathMode, FileContentsMode, Object)", new Runnable() {
            @Override
            public void run() {
                addFilePropertyInput(property, new DefaultTaskPropertyInputFiles(orderMode, pathMode, contentsMode, false, path));
            }
        });
        return this;
    }

    private void addFilePropertyInput(String property, TaskPropertyInputFiles input) {
        if (propertyFiles.containsKey(property)) {
            throw new IllegalStateException(String.format("Input property '%s' already declared", property));
        }
        propertyFiles.put(property, input);
    }

    @Override
    public boolean getHasSourceFiles() {
        return Iterables.any(propertyFiles.values(), SKIP_WHEN_EMPTY_ENTRIES);
    }

    @Override
    public FileCollection getSourceFiles() {
        if (sourceFiles == null) {
            sourceFiles = collectFiles(SKIP_WHEN_EMPTY_ENTRIES);
        }
        return sourceFiles;
    }

    @Override
    public TaskInputs source(final Object... paths) {
        taskMutator.mutate("TaskInputs.source(Object...)", new Runnable() {
            @Override
            public void run() {
                addFilePropertyInput(DEFAULT_PROPERTY_PREFIX + defaultPropertyCounter++, new DefaultTaskPropertyInputDirectories(FileOrderMode.UNORDERED, ABSOLUTE, FileContentsMode.USE, true, paths));
            }
        });
        return this;
    }

    @Override
    public TaskInputs includeSource(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object... paths) {
        taskMutator.mutate("TaskInputs.includeSource(String, FileOrderMode, FilePathMode, FileContentsMode, Object...)", new Runnable() {
            @Override
            public void run() {
                addFilePropertyInput(property, new DefaultTaskPropertyInputDirectories(orderMode, pathMode, contentsMode, true, paths));
            }
        });
        return this;
    }

    @Override
    public TaskInputs source(final Object path) {
        taskMutator.mutate("TaskInputs.source(Object)", new Runnable() {
            @Override
            public void run() {
                addFilePropertyInput(DEFAULT_PROPERTY_PREFIX + defaultPropertyCounter++, new DefaultTaskPropertyInputDirectories(FileOrderMode.UNORDERED, ABSOLUTE, FileContentsMode.USE, true, path));
            }
        });
        return this;
    }

    @Override
    public TaskInputs includeSource(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object path) {
        taskMutator.mutate("TaskInputs.includeSource(String, FileOrderMode, FilePathMode, FileContentsMode, Object)", new Runnable() {
            @Override
            public void run() {
                addFilePropertyInput(property, new DefaultTaskPropertyInputDirectories(orderMode, pathMode, contentsMode, true, path));
            }
        });
        return this;
    }

    @Override
    public TaskInputs sourceDir(final Object path) {
        taskMutator.mutate("TaskInputs.sourceDir(Object)", new Runnable() {
            @Override
            public void run() {
                addFilePropertyInput(DEFAULT_PROPERTY_PREFIX + defaultPropertyCounter++, new DefaultTaskPropertyInputFiles(FileOrderMode.UNORDERED, ABSOLUTE, FileContentsMode.USE, true, path));
            }
        });
        return this;
    }

    @Override
    public TaskInputs includeSourceDir(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object path) {
        taskMutator.mutate("TaskInputs.includeSourceDir(String, FileOrderMode, FilePathMode, FileContentsMode, Object...)", new Runnable() {
            @Override
            public void run() {
                addFilePropertyInput(property, new DefaultTaskPropertyInputFiles(orderMode, pathMode, contentsMode, true, path));
            }
        });
        return this;
    }

    @Override
    public Map<String, TaskPropertyInputFiles> getPropertyFiles() {
        return propertyFiles;
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

    public static abstract class AbstractTaskPropertyInputFiles implements TaskPropertyInputFiles {
        private final FileOrderMode orderMode;
        private final FilePathMode pathMode;
        private final FileContentsMode contentsMode;
        private final boolean skipWhenEmpty;
        protected final List<Object> paths;
        private FileCollection resolvedFiles;

        public AbstractTaskPropertyInputFiles(FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, boolean skipWhenEmpty, Object... paths) {
            this.orderMode = orderMode;
            this.pathMode = pathMode;
            this.contentsMode = contentsMode;
            this.skipWhenEmpty = skipWhenEmpty;
            this.paths = ImmutableList.copyOf(paths);
        }

        @Override
        public FileCollection resolve(FileResolver resolver) {
            if (resolvedFiles == null) {
                resolvedFiles = doResolve(resolver);
            }
            return resolvedFiles;
        }

        @Override
        public FileOrderMode getOrderMode() {
            return orderMode;
        }

        @Override
        public FilePathMode getPathMode() {
            return pathMode;
        }

        @Override
        public FileContentsMode getContentsMode() {
            return contentsMode;
        }

        @Override
        public boolean isSkipWhenEmpty() {
            return skipWhenEmpty;
        }

        protected abstract FileCollection doResolve(FileResolver resolver);
    }

    private static class DefaultTaskPropertyInputDirectories extends AbstractTaskPropertyInputFiles {
        public DefaultTaskPropertyInputDirectories(FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, boolean skipWhenEmpty, Object... paths) {
            super(orderMode, pathMode, contentsMode, skipWhenEmpty, paths);
        }

        @Override
        protected FileCollection doResolve(FileResolver resolver) {
            return resolver.resolveFiles(paths);
        }
    }

    private static class DefaultTaskPropertyInputFiles extends AbstractTaskPropertyInputFiles {
        public DefaultTaskPropertyInputFiles(FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, boolean skipWhenEmpty, Object... paths) {
            super(orderMode, pathMode, contentsMode, skipWhenEmpty, paths);
        }

        @Override
        protected FileCollection doResolve(FileResolver resolver) {
            ConfigurableFileCollection files = new DefaultConfigurableFileCollection(resolver, null);
            for (Object path : paths) {
                files.from(resolver.resolveFilesAsTree(path));
            }
            return files;
        }
    }
}
