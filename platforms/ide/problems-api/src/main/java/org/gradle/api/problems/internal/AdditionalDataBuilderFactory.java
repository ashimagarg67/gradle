/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.problems.internal;

import com.google.common.base.Preconditions;

public class AdditionalDataBuilderFactory {

    @SuppressWarnings("unchecked")
    public static <S extends AdditionalData<? extends AdditionalDataSpec>> AdditionalDataBuilder<S> builderFor(Class<? extends S> type) {
        Preconditions.checkNotNull(type);
        if (TypeValidationData.class.isAssignableFrom(type)) {
            return (AdditionalDataBuilder<S>) DefaultTypeValidationData.builder();
        } else if (DeprecationData.class.isAssignableFrom(type)) {
            return (AdditionalDataBuilder<S>) DefaultDeprecationData.builder();
        } else if (GenericData.class.isAssignableFrom(type)) {
            return (AdditionalDataBuilder<S>) DefaultGenericData.builder();
        } else {
            throw new IllegalArgumentException("Unsupported type: " + type);
        }
    }

    @SuppressWarnings("unchecked")
    public static <S extends AdditionalData<? extends AdditionalDataSpec>> AdditionalDataBuilder<S>  builderFor(S instance) {
        Preconditions.checkNotNull(instance);
        if (TypeValidationData.class.isInstance(instance)) {
            return (AdditionalDataBuilder<S>) DefaultTypeValidationData.builder((TypeValidationData) instance);
        } else if (DeprecationData.class.isInstance(instance)) {
            return (AdditionalDataBuilder<S>) DefaultDeprecationData.builder((DeprecationData) instance);
        } else if (GenericData.class.isInstance(instance)) {
            return (AdditionalDataBuilder<S>) DefaultGenericData.builder((GenericData) instance);
        } else {
            throw new IllegalArgumentException("Unsupported instance: " + instance);
        }
    }
}
