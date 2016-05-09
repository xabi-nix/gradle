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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.FileContentsMode;
import org.gradle.api.tasks.FileOrderMode;
import org.gradle.api.tasks.FilePathMode;

import java.util.Map;

public class DefaultTaskStateProvider implements TaskStateProvider {
    private final FileResolver fileResolver;
    private final FileStateProvider fileStateProvider;

    public DefaultTaskStateProvider(FileResolver fileResolver, FileStateProvider fileStateProvider) {
        this.fileResolver = fileResolver;
        this.fileStateProvider = fileStateProvider;
    }

    @Override
    public FileBasedTaskState getTaskState(TaskInternal task) {
        TaskClasspathState classpathState = new TaskClasspathState(((ProjectInternal) task.getProject()).getClasspathHash());
        TaskPropertiesState inputPropertiesState = new TaskPropertiesState(task.getInputs().getProperties());
        TaskFilesState inputFilesState = new TaskFilesState(convertInputFiles(task.getInputs().getPropertyFiles()));
        TaskFilesState outputFilesState = new TaskFilesState(convertOutputFiles(task.getOutputs().getPropertyFiles()));
        return new DefaultFileBasedTaskState(classpathState, inputPropertiesState, inputFilesState, outputFilesState);
    }

    private Map<String, FileCollectionState> convertInputFiles(Map<String, TaskInputsInternal.TaskPropertyInputFiles> propertyFiles) {
        ImmutableSortedMap.Builder<String, FileCollectionState> builder = ImmutableSortedMap.naturalOrder();
        for (Map.Entry<String, TaskInputsInternal.TaskPropertyInputFiles> entry : propertyFiles.entrySet()) {
            String property = entry.getKey();
            TaskInputsInternal.TaskPropertyInputFiles inputFiles = entry.getValue();
            FileTree files = inputFiles.resolve(fileResolver).getAsFileTree();
            FilePathMode pathMode = inputFiles.getPathMode();
            FileOrderMode orderMode = inputFiles.getOrderMode();
            FileContentsMode contentsMode = inputFiles.getContentsMode();
            FileCollectionState state;
            if (pathMode == FilePathMode.IGNORE) {
                if (orderMode != FileOrderMode.UNORDERED) {
                    throw new IllegalArgumentException(String.format("Input file property '%s' cannot ignore file paths and be ordered at the same time", property));
                }
                if (contentsMode != FileContentsMode.USE) {
                    throw new IllegalArgumentException(String.format("Input file property '%s' cannot ignore both file paths and contents at the same time", property));
                }
                state = new ContentOnlyFileCollectionState(fileStateProvider, files);
            } else {
                state = new PathBasedFileCollectionState(fileStateProvider, files, pathMode, orderMode, contentsMode);
            }
            builder.put(property, state);
        }
        return builder.build();
    }

    private Map<String, FileCollectionState> convertOutputFiles(Map<String, TaskOutputsInternal.TaskPropertyOutputFiles> propertyFiles) {
        ImmutableSortedMap.Builder<String, FileCollectionState> builder = ImmutableSortedMap.naturalOrder();
        for (Map.Entry<String, TaskOutputsInternal.TaskPropertyOutputFiles> entry : propertyFiles.entrySet()) {
            String property = entry.getKey();
            TaskOutputsInternal.TaskPropertyOutputFiles outputFiles = entry.getValue();
            DefaultConfigurableFileCollection files = new DefaultConfigurableFileCollection(fileResolver, null, outputFiles.getPaths());
            FileCollectionState state = new PathBasedFileCollectionState(fileStateProvider, files.getAsFileTree(), FilePathMode.HIERARCHY_ONLY, FileOrderMode.UNORDERED, FileContentsMode.USE);
            builder.put(property, state);
        }
        return builder.build();
    }
}
