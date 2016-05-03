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

package org.gradle.api.internal.changedetection.taskcache;

import com.google.common.io.ByteSink;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import org.gradle.api.internal.tasks.TaskOutputVisitor;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.tasks.TaskPropertyOutput;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipTaskOutputPacker implements TaskOutputPacker {
    @Override
    public TaskOutputWriter createWriter(final TaskOutputsInternal taskOutputs) throws IOException {
        return new TaskOutputWriter() {
            @Override
            public void writeTo(ByteSink output) throws IOException {
                Closer closer = Closer.create();
                OutputStream outputStream = closer.register(output.openBufferedStream());
                try {
                    final ZipOutputStream zipOutput = new ZipOutputStream(outputStream);
                    for (TaskPropertyOutput propertyOutput : taskOutputs.getPropertyOutputs()) {
                        final String property = propertyOutput.getProperty();
                        final String propertyRoot = "property-" + property + "/";
                        zipOutput.putNextEntry(new ZipEntry(propertyRoot));
                        propertyOutput.visitFiles(new TaskOutputVisitor() {
                            @Override
                            public void visitDirectory(String path) throws IOException {
                                zipOutput.putNextEntry(new ZipEntry(propertyRoot + path + "/"));
                            }

                            @Override
                            public void visitFile(String path, InputStream data) throws IOException {
                                zipOutput.putNextEntry(new ZipEntry(propertyRoot + path));
                                ByteStreams.copy(data, zipOutput);
                            }
                        });
                    }
                    zipOutput.close();
                } catch (Exception e) {
                    throw closer.rethrow(e);
                } finally {
                    //noinspection ThrowFromFinallyBlock
                    closer.close();
                }
            }
        };
    }

    private static final Pattern PROPERTY_PATH = Pattern.compile("property-([^/]+)/(.*)");

    @Override
    public void unpack(TaskOutputsInternal taskOutputs, TaskOutputReader result) throws IOException {
        Closer closer = Closer.create();
        InputStream input = closer.register(result.read().openBufferedStream());
        try {
            ZipInputStream zipInput = new ZipInputStream(input);
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                String name = entry.getName();
                Matcher matcher = PROPERTY_PATH.matcher(name);
                if (!matcher.matches()) {
                    // TODO:LPTR What to do here?
                    continue;
                }
                String property = matcher.group(1);
                TaskPropertyOutput output = taskOutputs.getPropertyOutput(property);

                String path = matcher.group(2);
                if (path.isEmpty()) {
                    continue;
                }
                if (entry.isDirectory()) {
                    output.getVisitor().visitDirectory(path);
                } else {
                    output.getVisitor().visitFile(path, zipInput);
                }
            }
        } catch (Exception e) {
            throw closer.rethrow(e);
        } finally {
            //noinspection ThrowFromFinallyBlock
            closer.close();
        }
    }
}
