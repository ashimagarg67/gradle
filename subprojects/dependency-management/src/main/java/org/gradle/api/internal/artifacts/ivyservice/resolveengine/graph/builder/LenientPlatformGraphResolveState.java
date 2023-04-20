/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.DefaultComponentGraphResolveState;

import javax.annotation.Nullable;

public class LenientPlatformGraphResolveState extends DefaultComponentGraphResolveState<LenientPlatformResolveMetadata, LenientPlatformResolveMetadata> {
    private final ComponentIdGenerator componentIdGenerator;
    private final AttributeDesugaring attributeDesugaring;

    public static LenientPlatformGraphResolveState of(
        ComponentIdGenerator componentIdGenerator,
        ModuleComponentIdentifier moduleComponentIdentifier,
        ModuleVersionIdentifier moduleVersionIdentifier,
        VirtualPlatformState platformState,
        NodeState platformNode,
        ResolveState resolveState
    ) {
        LenientPlatformResolveMetadata metadata = new LenientPlatformResolveMetadata(moduleComponentIdentifier, moduleVersionIdentifier, platformState, platformNode, resolveState);
        return new LenientPlatformGraphResolveState(componentIdGenerator.nextComponentId(), metadata, componentIdGenerator, resolveState.getAttributeDesugaring());
    }

    private LenientPlatformGraphResolveState(long instanceId, LenientPlatformResolveMetadata metadata, ComponentIdGenerator componentIdGenerator, AttributeDesugaring attributeDesugaring) {
        super(instanceId, metadata, metadata, attributeDesugaring);
        this.componentIdGenerator = componentIdGenerator;
        this.attributeDesugaring = attributeDesugaring;
    }

    @Nullable
    @Override
    public ComponentGraphResolveState maybeAsLenientPlatform(ModuleComponentIdentifier componentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier) {
        return new LenientPlatformGraphResolveState(componentIdGenerator.nextComponentId(), getMetadata().withVersion(componentIdentifier, moduleVersionIdentifier), componentIdGenerator, attributeDesugaring);
    }
}
