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
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.file.FileResolver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Queue;

public class OutputDirectoryTaskPropertyOutput implements TaskPropertyOutput {
    private final String property;
    private final FileResolver resolver;
    private final Object outputDirectory;
    private File resolvedOutputDirectory;
    private final TaskOutputVisitor visitor;

    public OutputDirectoryTaskPropertyOutput(String property, Object outputDirectory, FileResolver resolver) {
        this.property = property;
        this.resolver = resolver;
        this.outputDirectory = outputDirectory;
        this.visitor = new TaskOutputVisitor() {
            @Override
            public void visitDirectory(String path) throws IOException {
                FileUtils.forceMkdir(new File(getResolvedOutputDirectory(), path));
            }

            @Override
            public void visitFile(String path, InputStream data) throws IOException {
                File resolvedOutputDirectory = getResolvedOutputDirectory();
                FileUtils.forceMkdir(resolvedOutputDirectory);
                File file = new File(resolvedOutputDirectory, path);
                boolean created = file.createNewFile();
                if (!created) {
                    throw new IOException(String.format("Could not create file '%s'", file));
                }
                Files.asByteSink(file).writeFrom(data);
            }
        };
    }

    @Override
    public String getProperty() {
        return property;
    }

    private File getResolvedOutputDirectory() {
        if (resolvedOutputDirectory == null) {
            resolvedOutputDirectory = resolver.resolve(outputDirectory);
        }
        return resolvedOutputDirectory;
    }

    @Override
    public void visitFiles(TaskOutputVisitor visitor) throws IOException {
        ArrayDeque<RelativePath> queue = new ArrayDeque<RelativePath>();
        File outputDir = resolver.resolve(outputDirectory);
        addChildrenToQueue(queue, "", outputDir);
        while (!queue.isEmpty()) {
            RelativePath item = queue.removeFirst();
            String path = item.getPath();
            File file = item.getFile();
            // TODO:LPTR Do some consistent order here
            if (file.isDirectory()) {
                visitor.visitDirectory(path);
                addChildrenToQueue(queue, path + "/", file);
            } else {
                Closer closer = Closer.create();
                try {
                    visitor.visitFile(path, closer.register(new FileInputStream(file)));
                } catch (Exception ex) {
                    throw closer.rethrow(ex);
                } finally {
                    //noinspection ThrowFromFinallyBlock
                    closer.close();
                }
            }
        }
    }

    private static void addChildrenToQueue(Queue<RelativePath> queue, String basePath, File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            // TODO Handle this better
            throw new NullPointerException();
        }
        for (File file : files) {
            StringBuilder path = new StringBuilder(basePath == null ? 0 : basePath.length() + 1 + file.getName().length());
            if (basePath != null) {
                path.append(basePath);
            }
            path.append("/");
            path.append(file.getName());
            queue.add(new RelativePath(path.toString(), file));
        }
    }

    @Override
    public TaskOutputVisitor getVisitor() {
        return visitor;
    }

    @Override
    public void collectFiles(ConfigurableFileCollection files) {
        files.from(outputDirectory);
    }

    private static class RelativePath {
        private final String path;
        private final File file;

        public RelativePath(String path, File file) {
            this.path = path;
            this.file = file;
        }

        public String getPath() {
            return path;
        }

        public File getFile() {
            return file;
        }
    }
}
