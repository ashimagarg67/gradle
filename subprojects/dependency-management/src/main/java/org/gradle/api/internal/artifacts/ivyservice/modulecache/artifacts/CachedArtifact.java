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

package org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts;

import org.gradle.internal.resource.cached.CachedItem;

import java.math.BigInteger;
import java.util.List;

public interface CachedArtifact extends CachedItem {
    BigInteger getDescriptorHash();

    List<String> attemptedLocations();

    /**
     * The expected last modified date of the cached file, not the external source.
     *
     * @return The last modified date, -1 if {@link #isMissing()}
     */
    long getCachedFileLastModified();

    /**
     * The expected content length of the cached file, not the external source.
     *
     * @return The content length of the cached file, -1 if {@link #isMissing()}
     */
    boolean isLocalFileUnchanged();
}
