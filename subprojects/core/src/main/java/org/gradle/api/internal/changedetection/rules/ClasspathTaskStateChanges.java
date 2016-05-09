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

package org.gradle.api.internal.changedetection.rules;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.project.ProjectInternal;

import java.util.List;

class ClasspathTaskStateChanges extends SimpleTaskStateChanges {
    private final TaskExecution previousExecution;
    private final TaskExecution currentExecution;
    private final TaskInternal task;

    public ClasspathTaskStateChanges(TaskExecution previousExecution, TaskExecution currentExecution, TaskInternal task) {
        currentExecution.setTaskClass(task.getClass().getName());
        currentExecution.setTaskClassLoaderHash(((ProjectInternal) task.getProject()).getClasspathHash());
        this.currentExecution = currentExecution;
        this.previousExecution = previousExecution;
        this.task = task;
    }

    @Override
    protected void addAllChanges(List<TaskStateChange> changes) {
        if (!currentExecution.getTaskClass().equals(previousExecution.getTaskClass())) {
            changes.add(new DescriptiveChange("Task '%s' has changed type from '%s' to '%s'.",
                task.getPath(), previousExecution.getTaskClass(), task.getClass().getName()));
        }
        if (!currentExecution.getTaskClassLoaderHash().equals(previousExecution.getTaskClassLoaderHash())) {
            changes.add(new DescriptiveChange("Classpath of task '%s' has changed.",
                task.getPath(), previousExecution.getTaskClass()));
        }
    }
}
