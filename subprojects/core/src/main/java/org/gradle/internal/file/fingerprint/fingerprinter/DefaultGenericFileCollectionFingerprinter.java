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

package org.gradle.internal.file.fingerprint.fingerprinter;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.PathNormalizationStrategy;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.tasks.GenericFileNormalizer;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.file.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.file.mirror.FileSystemSnapshotter;
import org.gradle.normalization.internal.InputNormalizationStrategy;

public class DefaultGenericFileCollectionFingerprinter extends AbstractFileCollectionFingerprinter implements GenericFileCollectionFingerprinter {
    public DefaultGenericFileCollectionFingerprinter(StringInterner stringInterner, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemSnapshotter fileSystemSnapshotter) {
        super(stringInterner, directoryFileTreeFactory, fileSystemSnapshotter);
    }

    @Override
    public Class<? extends FileNormalizer> getRegisteredType() {
        return GenericFileNormalizer.class;
    }

    @Override
    public FileCollectionFingerprint fingerprint(FileCollection files, PathNormalizationStrategy pathNormalizationStrategy, InputNormalizationStrategy inputNormalizationStrategy) {
        FingerprintingStrategy strategy = determineFingerprintStrategy(pathNormalizationStrategy);
        return super.fingerprint(files, strategy);
    }

    private FingerprintingStrategy determineFingerprintStrategy(PathNormalizationStrategy pathNormalizationStrategy) {
        switch (pathNormalizationStrategy) {
            case ABSOLUTE:
                return new AbsolutePathFingerprintingStrategy(true);
            case OUTPUT:
                return new AbsolutePathFingerprintingStrategy(false);
            case RELATIVE:
                return new RelativePathFingerprintingStrategy();
            case NAME_ONLY:
                return new NameOnlyFingerprintingStrategy();
            case NONE:
                return new IgnoredPathFingerprintingStrategy();
            default:
                throw new IllegalArgumentException("Unknown normalization strategy " + pathNormalizationStrategy);
        }
    }
}
