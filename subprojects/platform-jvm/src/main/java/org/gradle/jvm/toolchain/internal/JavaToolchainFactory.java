/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.internal.file.FileFactory;
import org.gradle.jvm.toolchain.JavaInstallation;
import org.gradle.jvm.toolchain.JavaInstallationRegistry;

import javax.inject.Inject;
import java.io.File;

public class JavaToolchainFactory {

    private final FileFactory fileFactory;
    private final JavaInstallationRegistry installationRegistry;
    private final JavaCompilerFactory compilerFactory;
    private final ToolchainToolFactory toolFactory;

    @Inject
    public JavaToolchainFactory(FileFactory fileFactory, JavaInstallationRegistry installationRegistry, JavaCompilerFactory compilerFactory, ToolchainToolFactory toolFactory) {
        this.fileFactory = fileFactory;
        this.installationRegistry = installationRegistry;
        this.compilerFactory = compilerFactory;
        this.toolFactory = toolFactory;
    }

    public JavaToolchain newInstance(File javaHome) {
        final JavaInstallation installation = installationRegistry.installationForDirectory(fileFactory.dir(javaHome)).get();
        return new JavaToolchain(installation, compilerFactory, toolFactory);
    }

}
