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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import groovy.lang.GString;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.taskcache.CacheKeyBuilder;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.tasks.FileContentsMode;
import org.gradle.api.tasks.FileOrderMode;
import org.gradle.api.tasks.FilePathMode;
import org.gradle.api.tasks.TaskInputs;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.gradle.api.tasks.FilePathMode.ABSOLUTE;
import static org.gradle.util.GUtil.uncheckedCall;

public class DefaultTaskInputs implements TaskInputs {
    private static final Predicate<TaskPropertyInput> SKIP_WHEN_EMPTY_ENTRIES = new Predicate<TaskPropertyInput>() {
        @Override
        public boolean apply(TaskPropertyInput input) {
            return input.isSkipWhenEmpty();
        }
    };
    private static String DEFAULT_PROPERTY = "$default";
    private static Long MISSING_FILE = 2162696302935415879L;
    private static Long DIRECTORY = -2502020229561429852L;
    private static Long EXISTING_FILE = 8788545890128232955L;

    private final String description;
    private final FileResolver resolver;
    private final TaskMutator taskMutator;
    private final Map<String, Object> properties = Maps.newTreeMap();
    private final List<TaskPropertyInput> propertyInputs = Lists.newArrayList();
    private FileCollection files;
    private FileCollection sourceFiles;

    public DefaultTaskInputs(FileResolver resolver, TaskInternal task, TaskMutator taskMutator) {
        this.taskMutator = taskMutator;
        this.description = task + " input files";
        this.resolver = resolver;
    }

    @Override
    public boolean getHasInputs() {
        return !propertyInputs.isEmpty() || !properties.isEmpty();
    }

    @Override
    public FileCollection getFiles() {
        if (files == null) {
            files = collectFiles(Predicates.alwaysTrue());
        }
        return files;
    }

    private ConfigurableFileCollection collectFiles(Predicate<? super TaskPropertyInput> predicate) {
        ConfigurableFileCollection files = new DefaultConfigurableFileCollection(description, resolver, null);
        for (TaskPropertyInput input : propertyInputs) {
            if (predicate.apply(input)) {
                input.collectFiles(files, resolver);
            }
        }
        return files;
    }

    @Override
    public void appendToCacheKey(final CacheKeyBuilder keyBuilder) {
        keyBuilder.put(properties);

        Collections.sort(propertyInputs);
        String previousProperty = null;
        for (final TaskPropertyInput input : propertyInputs) {
            String property = input.getProperty();
            if (!property.equals(previousProperty)) {
                // TODO:LPTR Add some delimiter
                keyBuilder.put(property);
            }
            FileTree files = input.resolve(resolver).getAsFileTree();
            // TODO:LPTR Handle ordering
            files.visit(new FileVisitor() {
                @Override
                public void visitDir(FileVisitDetails dirDetails) {
                    visit(dirDetails);
                }

                @Override
                public void visitFile(FileVisitDetails fileDetails) {
                    visit(fileDetails);
                }

                private void visit(FileVisitDetails fileDetails) {
                    File file = fileDetails.getFile();
                    switch (input.getPathMode()) {
                        case ABSOLUTE:
                            // TODO:LPTR Add some delimiter
                            keyBuilder.put(file.getAbsolutePath());
                            break;
                        case HIERARCHY_ONLY:
                            // TODO:LPTR Add some different delimiter
                            // TODO:LPTR Figure out place in hierarchy properly
                            keyBuilder.put(fileDetails.getRelativePath().getPathString());
                            break;
                        case IGNORE:
                            break;
                    }

                    if (fileDetails.isDirectory()) {
                        keyBuilder.put(DIRECTORY);
                    } else if (file.isFile()) {
                        keyBuilder.put(EXISTING_FILE);
                    } else {
                        keyBuilder.put(MISSING_FILE);
                    }

                    if (file.isFile() && input.getContentsMode() == FileContentsMode.USE) {
                        // TODO:LPTR Use pre-calculated, locally cached hash instead
                        keyBuilder.put(Files.asByteSource(file));
                    }
                }
            });
        }
    }

    @Override
    public TaskInputs files(final Object... paths) {
        taskMutator.mutate("TaskInputs.files(Object...)", new Runnable() {
            @Override
            public void run() {
                addFiles(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, ABSOLUTE, FileContentsMode.USE, false, paths);
            }
        });
        return this;
    }

    @Override
    public TaskInputs includeFiles(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object... paths) {
        taskMutator.mutate("TaskInputs.includeFiles(String, FileOrderMode, FilePathMode, FileContentsMode, Object...)", new Runnable() {
            @Override
            public void run() {
                addFiles(property, orderMode, pathMode, contentsMode, false, paths);
            }
        });
        return this;
    }

    @Override
    public TaskInputs file(final Object path) {
        taskMutator.mutate("TaskInputs.file(Object)", new Runnable() {
            @Override
            public void run() {
                addFiles(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, ABSOLUTE, FileContentsMode.USE, false, path);
            }
        });
        return this;
    }

    @Override
    public TaskInputs includeFile(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object path) {
        taskMutator.mutate("TaskInputs.includeFile(String, FileOrderMode, FilePathMode, FileContentsMode, Object)", new Runnable() {
            @Override
            public void run() {
                addFiles(property, orderMode, pathMode, contentsMode, false, path);
            }
        });
        return this;
    }

    @Override
    public TaskInputs dir(final Object dirPath) {
        taskMutator.mutate("TaskInputs.dir(Object)", new Runnable() {
            @Override
            public void run() {
                addDirs(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, ABSOLUTE, FileContentsMode.USE, false, dirPath);
            }
        });
        return this;
    }

    @Override
    public TaskInputs includeDir(String property, FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, final Object path) {
        taskMutator.mutate("TaskInputs.includeDir(String, FileOrderMode, FilePathMode, FileContentsMode, Object)", new Runnable() {
            @Override
            public void run() {
                addDirs(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, ABSOLUTE, FileContentsMode.USE, false, path);
            }
        });
        return this;
    }

    private void addFiles(String property, FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, boolean skipWhenEmpty, Object... paths) {
        propertyInputs.add(new InputFilesTaskPropertyInput(property, orderMode, pathMode, contentsMode, skipWhenEmpty, paths));
    }

    private void addDirs(String property, FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, boolean skipWhenEmpty, Object... paths) {
        propertyInputs.add(new InputDirectoriesTaskPropertyInput(property, orderMode, pathMode, contentsMode, skipWhenEmpty, paths));
    }

    @Override
    public boolean getHasSourceFiles() {
        return Iterables.any(propertyInputs, SKIP_WHEN_EMPTY_ENTRIES);
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
                addFiles(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, ABSOLUTE, FileContentsMode.USE, true, paths);
            }
        });
        return this;
    }

    @Override
    public TaskInputs includeSource(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object... paths) {
        taskMutator.mutate("TaskInputs.includeSource(String, FileOrderMode, FilePathMode, FileContentsMode, Object...)", new Runnable() {
            @Override
            public void run() {
                addFiles(property, orderMode, pathMode, contentsMode, true, paths);
            }
        });
        return this;
    }

    @Override
    public TaskInputs source(final Object path) {
        taskMutator.mutate("TaskInputs.source(Object)", new Runnable() {
            @Override
            public void run() {
                addFiles(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, ABSOLUTE, FileContentsMode.USE, true, path);
            }
        });
        return this;
    }

    @Override
    public TaskInputs includeSource(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object path) {
        taskMutator.mutate("TaskInputs.includeSource(String, FileOrderMode, FilePathMode, FileContentsMode, Object)", new Runnable() {
            @Override
            public void run() {
                addFiles(property, orderMode, pathMode, contentsMode, true, path);
            }
        });
        return this;
    }

    @Override
    public TaskInputs sourceDir(final Object path) {
        taskMutator.mutate("TaskInputs.sourceDir(Object)", new Runnable() {
            @Override
            public void run() {
                addDirs(DEFAULT_PROPERTY, FileOrderMode.UNORDERED, ABSOLUTE, FileContentsMode.USE, true, path);
            }
        });
        return this;
    }

    @Override
    public TaskInputs includeSourceDir(final String property, final FileOrderMode orderMode, final FilePathMode pathMode, final FileContentsMode contentsMode, final Object path) {
        taskMutator.mutate("TaskInputs.includeSourceDir(String, FileOrderMode, FilePathMode, FileContentsMode, Object...)", new Runnable() {
            @Override
            public void run() {
                addDirs(property, orderMode, pathMode, contentsMode, true, path);
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

    public static abstract class TaskPropertyInput implements Comparable<TaskPropertyInput> {
        private final String property;
        private final FileOrderMode orderMode;
        private final FilePathMode pathMode;
        private final FileContentsMode contentsMode;
        private final boolean skipWhenEmpty;
        private final List<Object> paths;
        private FileCollection resolvedCollection;

        public TaskPropertyInput(String property, FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, boolean skipWhenEmpty, Object... paths) {
            this.property = property;
            this.orderMode = orderMode;
            this.pathMode = pathMode;
            this.contentsMode = contentsMode;
            this.skipWhenEmpty = skipWhenEmpty;
            this.paths = ImmutableList.copyOf(paths);
        }

        public void collectFiles(ConfigurableFileCollection fileCollection, FileResolver resolver) {
            if (resolvedCollection != null) {
                fileCollection.from(resolvedCollection);
            } else {
                doCollectFiles(fileCollection, resolver);
            }
        }

        protected abstract void doCollectFiles(ConfigurableFileCollection fileCollection, FileResolver resolver);

        public FileCollection resolve(FileResolver resolver) {
            if (resolvedCollection == null) {
                resolvedCollection = doResolve(resolver);
            }
            return resolvedCollection;
        }

        protected abstract FileCollection doResolve(FileResolver resolver);

        public String getProperty() {
            return property;
        }

        public FileOrderMode getOrderMode() {
            return orderMode;
        }

        public FilePathMode getPathMode() {
            return pathMode;
        }

        public FileContentsMode getContentsMode() {
            return contentsMode;
        }

        public boolean isSkipWhenEmpty() {
            return skipWhenEmpty;
        }

        public List<Object> getPaths() {
            return paths;
        }

        @Override
        public int compareTo(TaskPropertyInput other) {
            return property.compareTo(other.property);
        }
    }

    private static class InputFilesTaskPropertyInput extends TaskPropertyInput {
        public InputFilesTaskPropertyInput(String property, FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, boolean skipWhenEmpty, Object[] paths) {
            super(property, orderMode, pathMode, contentsMode, skipWhenEmpty, paths);
        }

        @Override
        public void doCollectFiles(ConfigurableFileCollection fileCollection, FileResolver resolver) {
            fileCollection.from(getPaths());
        }

        @Override
        protected FileCollection doResolve(FileResolver resolver) {
            return resolver.resolveFiles(getPaths());
        }
    }

    private static class InputDirectoriesTaskPropertyInput extends TaskPropertyInput {

        public InputDirectoriesTaskPropertyInput(String property, FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, boolean skipWhenEmpty, Object... paths) {
            super(property, orderMode, pathMode, contentsMode, skipWhenEmpty, paths);
        }

        @Override
        public void doCollectFiles(ConfigurableFileCollection fileCollection, FileResolver resolver) {
            for (Object path : getPaths()) {
                fileCollection.from(resolver.resolveFilesAsTree(path));
            }
        }

        @Override
        protected FileCollection doResolve(FileResolver resolver) {
            ConfigurableFileCollection files = new DefaultConfigurableFileCollection(resolver, null);
            for (Object path : getPaths()) {
                files.from(resolver.resolveFilesAsTree(path));
            }
            return files;
        }
    }
}
