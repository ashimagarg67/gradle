/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.mirah.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.internal.tasks.mirah.MirahJavaJointCompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerUtil;
import org.gradle.language.mirah.MirahPlatform;
import org.gradle.platform.base.internal.toolchain.ToolResolver;

import javax.inject.Inject;

/**
 * A platform-aware Mirah compile task.
 */
@Incubating
public class PlatformMirahCompile extends AbstractMirahCompile {

    private MirahPlatform platform;

    @Inject
    public PlatformMirahCompile() {
        super(new BaseMirahCompileOptions());
    }

    public MirahPlatform getPlatform() {
        return platform;
    }

    public void setPlatform(MirahPlatform platform) {
        this.platform = platform;
    }

    @Inject
    protected ToolResolver getToolResolver() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Compiler<MirahJavaJointCompileSpec> getCompiler(MirahJavaJointCompileSpec spec) {
        return CompilerUtil.castCompiler(getToolResolver().resolveCompiler(spec.getClass(), getPlatform()).get());
    }
}
