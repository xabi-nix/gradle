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

import com.google.common.io.Closer;
import com.google.common.io.Files;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class OutputFileTaskPropertyOutput implements TaskPropertyOutput {
    private final String property;
    private final Object outputFile;
    private final FileResolver resolver;
    private File resolvedOutputFile;
    private final TaskOutputVisitor visitor;

    public OutputFileTaskPropertyOutput(String property, Object outputFile, FileResolver resolver) {
        this.property = property;
        this.outputFile = outputFile;
        this.resolver = resolver;
        this.visitor = new TaskOutputVisitor() {
            @Override
            public void visitDirectory(String path) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void visitFile(String path, InputStream data) throws IOException {
                File outputFile = getResolvedOutputFile();
                File outputParent = outputFile.getParentFile();
                if (!outputParent.isDirectory()) {
                    if (outputParent.isFile()) {
                        throw new IOException(String.format("Cannot create directory '%s', file already exists", outputParent));
                    } else {
                        GFileUtils.mkdirs(outputParent);
                    }
                }
                boolean created = outputFile.createNewFile();
                if (!created) {
                    throw new IOException(String.format("Could not create file '%s'", outputFile));
                }
                Files.asByteSink(outputFile).writeFrom(data);
            }
        };
    }

    @Override
    public String getProperty() {
        return property;
    }

    @Override
    public void collectFiles(ConfigurableFileCollection files) {
        files.from(outputFile);
    }

    @Override
    public void visitFiles(TaskOutputVisitor visitor) throws IOException {
        Closer closer = Closer.create();
        try {
            visitor.visitFile("the-file", closer.register(new FileInputStream(getResolvedOutputFile())));
        } catch (Exception ex) {
            throw closer.rethrow(ex);
        } finally {
            //noinspection ThrowFromFinallyBlock
            closer.close();
        }
    }

    @Override
    public TaskOutputVisitor getVisitor() {
        return visitor;
    }

    private File getResolvedOutputFile() {
        if (resolvedOutputFile == null) {
            resolvedOutputFile = resolver.resolve(outputFile);
        }
        return resolvedOutputFile;
    }
}
