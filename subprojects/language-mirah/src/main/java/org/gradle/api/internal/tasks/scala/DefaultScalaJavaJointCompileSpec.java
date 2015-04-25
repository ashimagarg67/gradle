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

package org.gradle.api.internal.tasks.mirah;

import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec;
import org.gradle.language.mirah.tasks.BaseMirahCompileOptions;

import java.io.File;
import java.util.Map;

public class DefaultMirahJavaJointCompileSpec extends DefaultJavaCompileSpec implements MirahJavaJointCompileSpec {
    private BaseMirahCompileOptions options;
    private Iterable<File> mirahClasspath;
    private Iterable<File> zincClasspath;
    private Map<File, File> analysisMap;

    public BaseMirahCompileOptions getMirahCompileOptions() {
        return options;
    }

    public void setMirahCompileOptions(BaseMirahCompileOptions options) {
        this.options = options;
    }

    public Iterable<File> getMirahClasspath() {
        return mirahClasspath;
    }

    public void setMirahClasspath(Iterable<File> mirahClasspath) {
        this.mirahClasspath = mirahClasspath;
    }

    public Iterable<File> getZincClasspath() {
        return zincClasspath;
    }

    public void setZincClasspath(Iterable<File> zincClasspath) {
        this.zincClasspath = zincClasspath;
    }

    public Map<File, File> getAnalysisMap() {
        return analysisMap;
    }

    public void setAnalysisMap(Map<File, File> analysisMap) {
        this.analysisMap = analysisMap;
    }
}
