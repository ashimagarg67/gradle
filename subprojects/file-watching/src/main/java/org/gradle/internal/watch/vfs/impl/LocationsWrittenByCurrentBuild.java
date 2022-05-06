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

package org.gradle.internal.watch.vfs.impl;

import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.vfs.FileSystemAccess;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class LocationsWrittenByCurrentBuild implements FileSystemAccess.WriteListener {
    private final AtomicReference<FileHierarchySet> producedByCurrentBuild = new AtomicReference<>(FileHierarchySet.empty());
    private final AtomicReference<VersionHierarchy> versionHierarchy;
    private final AtomicLong currentVersion = new AtomicLong(0);
    private volatile boolean buildRunning;

    public LocationsWrittenByCurrentBuild(CaseSensitivity caseSensitivity) {
        this.versionHierarchy = new AtomicReference<>(VersionHierarchy.empty(currentVersion.incrementAndGet(), caseSensitivity));
    }

    @Override
    public void locationsWritten(Iterable<String> locations) {
        if (buildRunning) {
            producedByCurrentBuild.updateAndGet(currentValue -> {
                FileHierarchySet newValue = currentValue;
                for (String location : locations) {
                    newValue = newValue.plus(location);
                }
                return newValue;
            });
            versionHierarchy.updateAndGet(currentValue -> {
                VersionHierarchy newValue = currentValue;
                long newVersion = currentVersion.incrementAndGet();
                for (String location : locations) {
                    newValue = newValue.increaseVersion(location, newVersion);
                }
                return newValue;
            });
        }
    }

    public boolean wasLocationWritten(String location) {
        return producedByCurrentBuild.get().contains(location);
    }

    @Override
    public long getVersionFor(String location) {
        return versionHierarchy.get().getVersionFor(location);
    }

    public void buildStarted() {
        producedByCurrentBuild.set(FileHierarchySet.empty());
        buildRunning = true;
    }

    public void buildFinished() {
        buildRunning = false;
        producedByCurrentBuild.set(FileHierarchySet.empty());
    }
}
