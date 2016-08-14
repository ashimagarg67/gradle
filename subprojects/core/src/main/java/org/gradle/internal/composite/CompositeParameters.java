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

package org.gradle.internal.composite;

import java.io.Serializable;
import java.util.List;

public class CompositeParameters implements Serializable {
    private final List<IncludedBuild> builds;

    public CompositeParameters(List<IncludedBuild> builds) {
        this.builds = builds;
    }

    public IncludedBuild getTargetBuild() {
        // TODO:DAZ This is not a great contract: should be explicit
        return builds.get(0);
    }

    public List<IncludedBuild> getBuilds() {
        return builds;
    }
}
