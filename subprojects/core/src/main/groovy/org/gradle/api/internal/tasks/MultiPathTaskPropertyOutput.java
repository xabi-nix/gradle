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

import com.google.common.collect.Lists;
import org.gradle.api.file.ConfigurableFileCollection;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class MultiPathTaskPropertyOutput implements TaskPropertyOutput {
    private final String property;
    private final List<Object> paths;

    public MultiPathTaskPropertyOutput(String property, Object... paths) {
        this.property = property;
        this.paths = Lists.newArrayList(paths);
    }

    @Override
    public String getProperty() {
        return property;
    }

    public void addPaths(Object... additionalPaths) {
        Collections.addAll(paths, additionalPaths);
    }

    @Override
    public void collectFiles(ConfigurableFileCollection files) {
        files.from(paths);
    }

    @Override
    public void visitFiles(TaskOutputVisitor visitor) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskOutputVisitor getVisitor() {
        throw new UnsupportedOperationException();
    }
}
