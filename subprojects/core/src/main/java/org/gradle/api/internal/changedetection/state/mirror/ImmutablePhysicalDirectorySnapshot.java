/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror;

import com.google.common.collect.ImmutableList;

public class ImmutablePhysicalDirectorySnapshot extends AbstractPhysicalDirectorySnapshot {
    private final ImmutableList<PhysicalSnapshot> children;

    public ImmutablePhysicalDirectorySnapshot(String path, String name, ImmutableList<PhysicalSnapshot> children) {
        super(path, name);
        this.children = children;
    }

    @Override
    protected ImmutableList<PhysicalSnapshot> getChildren() {
        return children;
    }

    @Override
    public PhysicalSnapshot add(String[] segments, int offset, PhysicalSnapshot snapshot) {
        throw new UnsupportedOperationException("Cannot add children to immutable directory snapshot");
    }
}
