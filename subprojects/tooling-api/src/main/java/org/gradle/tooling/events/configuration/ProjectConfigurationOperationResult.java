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

package org.gradle.tooling.events.configuration;

import org.gradle.api.Incubating;
import org.gradle.tooling.events.OperationResult;
import org.gradle.tooling.events.PluginIdentifier;

import java.time.Duration;
import java.util.List;

/**
 * Describes the result of running a project configuration operation.
 *
 * @since 5.1
 */
@Incubating
public interface ProjectConfigurationOperationResult extends OperationResult {

    /**
     * Returns the results of plugins applied as part of the configuration of this project.
     *
     * <p>This may include plugins applied to other projects that are part of the current build,
     * e.g. when using {@code subprojects {}} blocks in the build script of the root project.
     */
    List<? extends PluginConfigurationResult> getPluginConfigurationResults();

    /**
     * Describes the result of configuring a plugin.
     *
     * @since 5.1
     */
    @Incubating
    interface PluginConfigurationResult {

        /**
         * Returns the identifier of this plugin.
         */
        PluginIdentifier getPlugin();

        /**
         * Returns the total configuration time of this plugin.
         */
        Duration getDuration();

    }

}
