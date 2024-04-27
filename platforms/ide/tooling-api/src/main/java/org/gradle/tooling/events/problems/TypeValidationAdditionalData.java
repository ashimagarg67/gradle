/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.events.problems;

import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;

import javax.annotation.Nullable;

/**
 * TODO documentation. expectation on the supertype should be explicitily described.
 *
 * @since 8.9
 */
@Incubating
@NonNullApi
public interface TypeValidationAdditionalData extends AdditionalData {

    /**
     * TODO documentation.
     *
     * @since 8.9
     */
    @Nullable
    String getPluginId();

    /**
     * TODO documentation.
     *
     * @since 8.9
     */
    @Nullable
    String getIsIrrelevantInErrorMessage();

    /**
     * TODO documentation.
     *
     * @since 8.9
     */
    @Nullable
    String getPropertyName();

    /**
     * TODO documentation.
     *
     * @since 8.9
     */
    @Nullable
    String getParentPropertyName();

    /**
     * TODO documentation.
     *
     * @since 8.9
     */
    @Nullable
    String getTypeName();
}
