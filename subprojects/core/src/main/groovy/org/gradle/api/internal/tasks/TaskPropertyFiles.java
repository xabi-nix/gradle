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

package org.gradle.api.internal.tasks;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.changedetection.taskcache.CacheKeyBuilder;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.tasks.FileContentsMode;
import org.gradle.api.tasks.FileOrderMode;
import org.gradle.api.tasks.FilePathMode;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class TaskPropertyFiles {
    public static final String DEFAULT_PROPERTY = "$default";
    private final String description;
    private final FileResolver resolver;
    private final List<Entry> entries = Lists.newArrayList();

    public TaskPropertyFiles(String description, FileResolver resolver) {
        this.description = description;
        this.resolver = resolver;
    }

    public void files(String property, FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, boolean skipWhenEmpty, Object... paths) {
        addEntry(new FilesEntry(property, orderMode, pathMode, contentsMode, skipWhenEmpty, paths));
    }

    public void dirs(String property, FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, boolean skipWhenEmpty, Object... paths) {
        addEntry(new DirsEntry(property, orderMode, pathMode, contentsMode, skipWhenEmpty, paths));
    }

    private void addEntry(Entry entry) {
        entries.add(entry);
    }

    public boolean hasEntries(Predicate<? super Entry> predicate) {
        return Iterables.any(entries, predicate);
    }

    public ConfigurableFileCollection collectFiles(Predicate<? super Entry> predicate) {
        ConfigurableFileCollection files = new DefaultConfigurableFileCollection(description, resolver, null);
        for (Entry entry : entries) {
            if (predicate.apply(entry)) {
                entry.collectFiles(files, resolver);
            }
        }
        return files;
    }

    public boolean shouldSkipBecauseEmpty() {
        boolean foundSkipWhenEmpty = false;
        for (Entry entry : entries) {
            if (!entry.skipWhenEmpty) {
                continue;
            }
            foundSkipWhenEmpty = true;
            if (!entry.resolve(resolver).isEmpty()) {
                return false;
            }
        }
        return foundSkipWhenEmpty;
    }

    public void appendToCacheKey(CacheKeyBuilder keyBuilder, boolean useFileContents) {
        Collections.sort(entries);
        String previousProperty = null;
        for (Entry entry : entries) {
            String property = entry.getProperty();
            if (!property.equals(previousProperty)) {
                // TODO Add some delimiter
                keyBuilder.put(property);
            }
            FileCollection files = entry.resolve(resolver);
            // TODO Handle ordering
            for (File file : files) {
                switch (entry.getPathMode()) {
                    case ABSOLUTE:
                        // TODO Add some delimiter
                        keyBuilder.put(file.getAbsolutePath());
                        break;
                    case HIERARCHY_ONLY:
                        // TODO Add some different delimiter
                        // TODO Figure out place in hierarchy properly
                        keyBuilder.put(file.getName());
                        break;
                    case IGNORE:
                        break;
                }

                if (useFileContents && entry.getContentsMode() == FileContentsMode.USE) {
                    // TODO Add some delimiter
                    // TODO Use pre-calculated, locally cached hash instead
                    keyBuilder.put(Files.asByteSource(file));
                    break;
                }
            }
        }
    }

    public static abstract class Entry implements Comparable<Entry> {
        private final String property;
        private final FileOrderMode orderMode;
        private final FilePathMode pathMode;
        private final FileContentsMode contentsMode;
        private final boolean skipWhenEmpty;
        private final List<Object> paths;
        private FileCollection resolvedCollection;

        public Entry(String property, FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, boolean skipWhenEmpty, Object... paths) {
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
        public int compareTo(Entry other) {
            return property.compareTo(other.property);
        }
    }

    private static class FilesEntry extends Entry {
        public FilesEntry(String property, FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, boolean skipWhenEmpty, Object[] paths) {
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

    private static class DirsEntry extends Entry {

        public DirsEntry(String property, FileOrderMode orderMode, FilePathMode pathMode, FileContentsMode contentsMode, boolean skipWhenEmpty, Object... paths) {
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
