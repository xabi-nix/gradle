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

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.changedetection.taskcache.CacheKeyBuilder;
import org.gradle.api.internal.file.FileVisitorWithMissingFiles;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class ContentOnlyFileCollectionState implements FileCollectionState {
    private final FileStateProvider fileStateProvider;
    private final FileTree files;
    private List<FileState> fileStates;

    public ContentOnlyFileCollectionState(FileStateProvider fileStateProvider, FileTree files) {
        this.fileStateProvider = fileStateProvider;
        this.files = files;
    }

    @Override
    public boolean reportDifferences(FileCollectionState previousState, TaskState.TaskStateComparisonReporter reporter) {
        if (!(previousState instanceof ContentOnlyFileCollectionState)) {
            return reporter.reportDifference("wasn't content-only");
        }

        ContentOnlyFileCollectionState previous = (ContentOnlyFileCollectionState) previousState;
        Iterator<FileState> currentFileStates = getFileStates().iterator();
        Iterator<FileState> previousFileStates = previous.getFileStates().iterator();
        int fileNumber = 0;
        while (true) {
            fileNumber++;
            boolean currentLeft = currentFileStates.hasNext();
            boolean previousLeft = previousFileStates.hasNext();
            if (currentLeft) {
                if (previousLeft) {
                    FileState currentFileState = currentFileStates.next();
                    FileState previousFileState = previousFileStates.next();
                    if (!currentFileState.isContentAndMetadataUpToDate(previousFileState)) {
                        if (!reporter.reportDifference("contents of file #", fileNumber, " have changed")) {
                            return false;
                        }
                    }
                } else {
                    return reporter.reportDifference("there are more files in the collection");
                }
            } else {
                return !previousLeft || reporter.reportDifference("there are fewer files in the collection");
            }
        }
    }

    @Override
    public void appendToCacheKey(CacheKeyBuilder keyBuilder) {
        for (FileState state : getFileStates()) {
            state.appendToCacheKey(keyBuilder);
        }
    }

    private List<FileState> getFileStates() {
        if (fileStates == null) {
            final ImmutableList.Builder<FileState> builder = ImmutableList.builder();
            files.visit(new FileVisitorWithMissingFiles() {
                @Override
                public void visitFile(FileVisitDetails fileDetails) {
                    FileState fileState;
                    try {
                        fileState = fileStateProvider.getFileState(fileDetails.getFile());
                    } catch (IOException e) {
                        throw Throwables.propagate(e);
                    }
                    builder.add(fileState);
                }

                @Override
                public void visitDir(FileVisitDetails dirDetails) {
                    builder.add(DirState.INSTANCE);
                }

                @Override
                public void visitMissingFile(FileVisitDetails fileDetails) {
                    builder.add(MissingFileState.INSTANCE);
                }
            });
            fileStates = builder.build();
        }
        return fileStates;
    }
}
