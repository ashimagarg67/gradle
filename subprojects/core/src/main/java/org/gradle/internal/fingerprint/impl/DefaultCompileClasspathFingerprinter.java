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

package org.gradle.internal.fingerprint.impl;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.AbiExtractingClasspathResourceHasher;
import org.gradle.api.internal.changedetection.state.CachingResourceHasher;
import org.gradle.api.internal.changedetection.state.ResourceHasher;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.tasks.CompileClasspathNormalizer;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.fingerprint.CompileClasspathFingerprinter;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.normalization.internal.InputNormalizationStrategy;

public class DefaultCompileClasspathFingerprinter extends AbstractFileCollectionFingerprinter implements CompileClasspathFingerprinter {
    private final ResourceHasher classpathResourceHasher;
    private final ResourceSnapshotterCacheService cacheService;
    private final StringInterner stringInterner;

    public DefaultCompileClasspathFingerprinter(ResourceSnapshotterCacheService cacheService, FileSystemSnapshotter fileSystemSnapshotter, StringInterner stringInterner) {
        super(stringInterner, fileSystemSnapshotter);
        this.cacheService = cacheService;
        this.classpathResourceHasher = new CachingResourceHasher(new AbiExtractingClasspathResourceHasher(), cacheService);
        this.stringInterner = stringInterner;
    }

    @Override
    public CurrentFileCollectionFingerprint fingerprint(FileCollection files, InputNormalizationStrategy inputNormalizationStrategy) {
        return super.fingerprint(
            files,
            ClasspathFingerprintingStrategy.compileClasspath(classpathResourceHasher, cacheService, stringInterner));
    }

    @Override
    public Class<? extends FileNormalizer> getRegisteredType() {
        return CompileClasspathNormalizer.class;
    }
}
