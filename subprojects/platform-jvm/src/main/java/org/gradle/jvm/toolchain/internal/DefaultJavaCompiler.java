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

import org.gradle.api.JavaVersion;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.WorkResult;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.language.base.internal.compile.CompileSpec;

public class DefaultJavaCompiler implements JavaCompiler {

    private final JavaToolchain javaToolchain;
    private final JavaCompilerFactory compilerFactory;

    public DefaultJavaCompiler(JavaToolchain javaToolchain, JavaCompilerFactory compilerFactory) {
        this.javaToolchain = javaToolchain;
        this.compilerFactory = compilerFactory;
    }

    @Input
    public JavaVersion getJavaMajorVersion() {
        return javaToolchain.getJavaMajorVersion();
    }

    @SuppressWarnings("unchecked")
    public <T extends CompileSpec> WorkResult execute(T spec) {
        System.out.println("Toolchain selected: " + javaToolchain.getJavaMajorVersion());
        final Class<T> specType = (Class<T>) spec.getClass();
        return compilerFactory.create(specType).execute(spec);
    }

}
