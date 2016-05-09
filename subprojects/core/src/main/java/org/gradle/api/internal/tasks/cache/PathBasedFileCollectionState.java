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
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.changedetection.taskcache.CacheKeyBuilder;
import org.gradle.api.internal.file.FileVisitorWithMissingFiles;
import org.gradle.api.tasks.FileContentsMode;
import org.gradle.api.tasks.FileOrderMode;
import org.gradle.api.tasks.FilePathMode;

import java.io.IOException;
import java.util.Map;

public class PathBasedFileCollectionState implements FileCollectionState {

    private final FileStateProvider fileStateProvider;
    private final FileTree files;
    private final Function<FileVisitDetails, String> pathExtractor;
    private final FileOrderMode orderMode;
    // TODO:LPTR Handle contents mode
    private final FileContentsMode contentsMode;
    private Map<String, FileState> fileStates;

    public PathBasedFileCollectionState(FileStateProvider fileStateProvider, FileTree files, FilePathMode pathMode, FileOrderMode orderMode, FileContentsMode contentsMode) {
        this.fileStateProvider = fileStateProvider;
        this.files = files;
        this.pathExtractor = getExtractor(pathMode);
        this.orderMode = orderMode;
        this.contentsMode = contentsMode;
    }

    private static Function<FileVisitDetails, String> getExtractor(FilePathMode pathMode) {
        // TODO:LPTR Normalize paths
        // TODO:LPTR Handle relative paths properly, i.e. make them relative to the root of the root file tree or something
        switch (pathMode) {
            case ABSOLUTE:
                return new Function<FileVisitDetails, String>() {
                    @Override
                    public String apply(FileVisitDetails details) {
                        return details.getFile().getAbsolutePath();
                    }
                };
            case HIERARCHY_ONLY:
                return new Function<FileVisitDetails, String>() {
                    @Override
                    public String apply(FileVisitDetails details) {
                        return details.getRelativePath().getPathString();
                    }
                };
            case IGNORE:
                throw new IllegalArgumentException("Cannot ignore paths");
            default:
                throw new AssertionError();
        }
    }

    @Override
    public boolean reportDifferences(FileCollectionState previousState, TaskState.TaskStateComparisonReporter reporter) {
        if (!(previousState instanceof PathBasedFileCollectionState)) {
            return reporter.reportDifference("wasn't using paths");
        }
        PathBasedFileCollectionState previous = (PathBasedFileCollectionState) previousState;
        if (orderMode != previous.orderMode) {
            return reporter.reportDifference("order mode has changed");
        }
        DiffListener diffListener = new DiffListener(reporter);
        if (orderMode == FileOrderMode.ORDERED) {
            return DiffUtil.diff(previous.getFileStates(), getFileStates(), diffListener);
        } else {
            return DiffUtil.diffUnordered(previous.getFileStates(), getFileStates(), diffListener);
        }
    }

    @Override
    public void appendToCacheKey(CacheKeyBuilder keyBuilder) {
        for (Map.Entry<String, FileState> entry : getFileStates().entrySet()) {
            keyBuilder.put(entry.getKey());
            entry.getValue().appendToCacheKey(keyBuilder);
        }
    }

    private Map<String, FileState> getFileStates() {
        if (fileStates == null) {
            final ImmutableMap.Builder<String, FileState> builder;
            switch (orderMode) {
                case ORDERED:
                    builder = ImmutableSortedMap.naturalOrder();
                    break;
                case UNORDERED:
                    builder = ImmutableMap.builder();
                    break;
                default:
                    throw new AssertionError();
            }
            files.visit(new FileVisitorWithMissingFiles() {
                @Override
                public void visitFile(FileVisitDetails fileDetails) {
                    FileState fileState;
                    try {
                        fileState = fileStateProvider.getFileState(fileDetails.getFile());
                    } catch (IOException e) {
                        throw Throwables.propagate(e);
                    }
                    addState(fileDetails, fileState);
                }

                @Override
                public void visitDir(FileVisitDetails dirDetails) {
                    addState(dirDetails, DirState.INSTANCE);
                }

                @Override
                public void visitMissingFile(FileVisitDetails fileDetails) {
                    addState(fileDetails, MissingFileState.INSTANCE);
                }

                private void addState(FileVisitDetails details, FileState fileState) {
                    String path = pathExtractor.apply(details);
                    builder.put(path, fileState);
                }
            });
            fileStates = builder.build();
        }
        return fileStates;
    }

    private static class DiffListener implements DiffUtil.OrderingAwareListener<String> {
        private final TaskState.TaskStateComparisonReporter reporter;

        public DiffListener(TaskState.TaskStateComparisonReporter reporter) {
            this.reporter = reporter;
        }

        @Override
        public boolean added(String key) {
            return reporter.reportDifference("file '", key, "' has been added");
        }

        @Override
        public boolean removed(String key) {
            return reporter.reportDifference("file '", key, "' has been removed");
        }

        @Override
        public boolean changed(String key) {
            return reporter.reportDifference("contents of file '", key, "' has changed");
        }

        @Override
        public boolean reordered(String key) {
            return reporter.reportDifference("position of file '", key, "' has changed");
        }
    }
}
