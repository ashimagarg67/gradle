/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks;

import groovy.lang.Closure;
import org.gradle.api.file.SourceDirectorySet;

/**
 * A {@code MirahSourceSetConvention} defines the properties and methods added to a {@link
 * org.gradle.api.tasks.SourceSet} by the {@code MirahPlugin}.
 */
public interface MirahSourceSet {
    /**
     * Returns the source to be compiled by the Mirah compiler for this source set. This may contain both Java and Mirah
     * source files.
     *
     * @return The Mirah source. Never returns null.
     */
    SourceDirectorySet getMirah();

    /**
     * Configures the Mirah source for this set.
     *
     * <p>The given closure is used to configure the {@link SourceDirectorySet} which contains the Mirah source.
     *
     * @param configureClosure The closure to use to configure the Mirah source.
     * @return this
     */
    MirahSourceSet mirah(Closure configureClosure);

    /**
     * All Mirah source for this source set.
     *
     * @return the Mirah source. Never returns null.
     */
    SourceDirectorySet getAllMirah();
}