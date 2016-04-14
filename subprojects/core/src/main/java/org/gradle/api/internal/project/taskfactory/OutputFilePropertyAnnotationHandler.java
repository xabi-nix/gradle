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

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.FileContentsMode;
import org.gradle.api.tasks.FileOrderMode;
import org.gradle.api.tasks.FilePathMode;
import org.gradle.internal.FileUtils;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.concurrent.Callable;

import static org.gradle.util.GUtil.uncheckedCall;

public class OutputFilePropertyAnnotationHandler<A extends Annotation> implements PropertyAnnotationHandler {

    private final Class<A> annotationType;
    private final Transformer<Iterable<File>, Object> valueTransformer;
    private final FileAnnotationExtractor<A> annotationExtractor;

    public OutputFilePropertyAnnotationHandler(Class<A> annotationType, Transformer<Iterable<File>, Object> valueTransformer, FileAnnotationExtractor<A> annotationExtractor) {
        this.annotationType = annotationType;
        this.valueTransformer = valueTransformer;
        this.annotationExtractor = annotationExtractor;
    }

    public Class<A> getAnnotationType() {
        return annotationType;
    }

    private final ValidationAction outputDirValidation = new ValidationAction() {
        public void validate(String propertyName, Object value, Collection<String> messages) {
            for (File file : valueTransformer.transform(value)) {
                if (file.exists() && file.isDirectory()) {
                    messages.add(String.format("Cannot write to file '%s' specified for property '%s' as it is a directory.", file, propertyName));
                }

                for (File candidate = file.getParentFile(); candidate != null && !candidate.isDirectory(); candidate = candidate.getParentFile()) {
                    if (candidate.exists() && !candidate.isDirectory()) {
                        messages.add(String.format("Cannot write to file '%s' specified for property '%s', as ancestor '%s' is not a directory.", file, propertyName, candidate));
                        break;
                    }
                }
            }
        }
    };

    public void attachActions(final PropertyActionContext context) {
        final A annotation = context.getAnnotation(annotationType);
        context.setValidationAction(outputDirValidation);
        context.setConfigureAction(new UpdateAction() {
            public void update(TaskInternal task, final Callable<Object> futureValue) {
                FileOrderMode orderMode = annotationExtractor.getOrderMode(annotation);
                FilePathMode pathMode = annotationExtractor.getPathMode(annotation);
                FileContentsMode contentsMode = annotationExtractor.getContentsMode(annotation);
                task.getOutputs().files(context.getName(), orderMode, pathMode, contentsMode, futureValue);
                task.prependParallelSafeAction(new Action<Task>() {
                    public void execute(Task task) {
                        Iterable<File> files = valueTransformer.transform(uncheckedCall(futureValue));
                        for (File file : files) {
                            file = FileUtils.canonicalize(file);
                            GFileUtils.mkdirs(file.getParentFile());
                        }
                    }
                });
            }
        });
    }
}
