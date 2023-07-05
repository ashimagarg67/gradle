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

package org.gradle.tooling.internal.protocol;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.events.OperationType;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@NonNullApi
public class OperationMapping {

    private static final Map<String, OperationType> OPERATION_TYPE_MAPPING_NAME_TO_TYPE = createMapping();

    @Nonnull
    private static Map<String, OperationType> createMapping() {
        HashMap<String, OperationType> map = new HashMap<>();
//        return ImmutableMap.<String, OperationType>builderWithExpectedSize(OperationType.values().length)
            map.put(InternalBuildProgressListener.TEST_EXECUTION, OperationType.TEST);
        map.put(InternalBuildProgressListener.TASK_EXECUTION, OperationType.TASK);
        map .put(InternalBuildProgressListener.WORK_ITEM_EXECUTION, OperationType.WORK_ITEM);
        map .put(InternalBuildProgressListener.PROJECT_CONFIGURATION_EXECUTION, OperationType.PROJECT_CONFIGURATION);
        map .put(InternalBuildProgressListener.TRANSFORM_EXECUTION, OperationType.TRANSFORM);
        map .put(InternalBuildProgressListener.BUILD_EXECUTION, OperationType.GENERIC);
        map .put(InternalBuildProgressListener.TEST_OUTPUT, OperationType.TEST_OUTPUT);
        map .put(InternalBuildProgressListener.FILE_DOWNLOAD, OperationType.FILE_DOWNLOAD);
        map .put(InternalBuildProgressListener.BUILD_PHASE, OperationType.BUILD_PHASE);
        map .put(InternalBuildProgressListener.PROBLEMS, OperationType.PROBLEMS);
//            .build();
        return map;
    }

    private static final Map<OperationType, String> OPERATION_TYPE_MAPPING_TYPE_TO_NAME = createReverseMap();

    private static Map<OperationType, String> createReverseMap() {
//        ImmutableMap.Builder<OperationType, String> reverseMapBuilder = ImmutableMap.builderWithExpectedSize(OperationType.values().length);
        HashMap<OperationType, String> map = new HashMap<>();
        for (Map.Entry<String, OperationType> entry : OPERATION_TYPE_MAPPING_NAME_TO_TYPE.entrySet()) {
            map.put(entry.getValue(), entry.getKey());
        }
        return map;
    }

    public static String getOperationName(OperationType operationType) {
        return OPERATION_TYPE_MAPPING_TYPE_TO_NAME.get(operationType);
    }

    public static OperationType getOperationType(String operationName) {
        return OPERATION_TYPE_MAPPING_NAME_TO_TYPE.get(operationName);
    }

    public static boolean hasOperationType(String operationName) {
        return OPERATION_TYPE_MAPPING_NAME_TO_TYPE.containsKey(operationName);
    }

    public static Collection<OperationType> getOperationTypes() {
        return OPERATION_TYPE_MAPPING_NAME_TO_TYPE.values();
    }
}