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
package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.SkipWhenEmpty;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.concurrent.Callable;

public class InputFilesPropertyAnnotationHandler implements PropertyAnnotationHandler {
    public Class<? extends Annotation> getAnnotationType() {
        return InputFiles.class;
    }

    public void attachActions(final PropertyActionContext context) {
        // TODO The annotation itself should be cached in the context
        AnnotatedElement target = context.getAnnotatedElement(InputFiles.class);
        final InputFiles annotation = target.getAnnotation(InputFiles.class);
        final boolean skipWhenEmpty = target.isAnnotationPresent(SkipWhenEmpty.class);
        context.setConfigureAction(new UpdateAction() {
            public void update(TaskInternal task, Callable<Object> futureValue) {
                if (skipWhenEmpty) {
                    task.getInputs().includeSource(context.getName(), annotation.order(), annotation.paths(), annotation.contents(), futureValue);
                } else {
                    task.getInputs().includeFiles(context.getName(), annotation.order(), annotation.paths(), annotation.contents(), futureValue);
                }
            }
        });
    }
}
