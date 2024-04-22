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

package org.gradle.api.internal.artifacts.result;

import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.function.Supplier;

/**
 * Contains the minimal data required to construct a complete {@link org.gradle.api.artifacts.result.ResolutionResult}.
 */
public interface MinimalResolutionResult {

    /**
     * The ID of the root variant of the root component.
     */
    long getRootVariantId();

    /**
     * A function which provides root of the dependency graph.
     */
    Supplier<ResolvedComponentResultInternal> getRootSource();

    /**
     * The request attributes used to initially build the dependency graph.
     */
    ImmutableAttributes getRequestedAttributes();
}
