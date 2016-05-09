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

package org.gradle.api.internal.tasks.cache.serialization;

import com.google.common.hash.HashCode;
import org.gradle.api.internal.tasks.cache.DirState;
import org.gradle.api.internal.tasks.cache.FileState;
import org.gradle.api.internal.tasks.cache.HashedFileState;
import org.gradle.api.internal.tasks.cache.MissingFileState;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

public class FileStateSerializer implements Serializer<FileState> {
    private static final byte HASHED_FILE_ID = 1;
    private static final byte DIR_ID = 2;
    private static final byte MISSING_FILE_ID = 3;

    @Override
    public FileState read(Decoder decoder) throws Exception {
        byte type = decoder.readByte();
        switch (type) {
            case HASHED_FILE_ID:
                long timestamp = decoder.readSmallLong();
                byte[] hashCode = decoder.readBinary();
                return new HashedFileState(timestamp,  HashCode.fromBytes(hashCode));
            case DIR_ID:
                return DirState.INSTANCE;
            case MISSING_FILE_ID:
                return MissingFileState.INSTANCE;
            default:
                throw new IllegalStateException("Invalid FileState type: " + type);
        }
    }

    @Override
    public void write(Encoder encoder, FileState value) throws Exception {
        if (value instanceof HashedFileState) {
            HashedFileState hashed = (HashedFileState) value;
            encoder.writeByte(HASHED_FILE_ID);
            encoder.writeSmallLong(hashed.getTimestamp());
            encoder.writeBinary(hashed.getHashCode().asBytes());
        } else if (value instanceof DirState) {
            encoder.writeByte(DIR_ID);
        } else if (value instanceof MissingFileState) {
            encoder.writeByte(MISSING_FILE_ID);
        } else {
            throw new IllegalStateException("Unsupported FileState type: " + value.getClass().getName());
        }
    }
}
