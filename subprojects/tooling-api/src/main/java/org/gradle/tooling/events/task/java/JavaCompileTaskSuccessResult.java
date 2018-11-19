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

package org.gradle.tooling.events.task.java;

import org.gradle.api.Incubating;
import org.gradle.tooling.events.task.TaskSuccessResult;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;

/**
 * Describes the successful result of a {@code JavaCompile} task.
 *
 * @since 5.1
 */
@Incubating
public interface JavaCompileTaskSuccessResult extends TaskSuccessResult {

    /**
     * Returns results of used annotation processors, if available.
     *
     * <p>Details are only available if an instrumented compiler was used.
     *
     * @return details about used annotation processors; {@code null} if unknown.
     */
    @Nullable
    List<AnnotationProcessorResult> getAnnotationProcessorResults();

    /**
     * The results of an annotation processor used during compilation.
     *
     * @since 5.1
     */
    @Incubating
    interface AnnotationProcessorResult {

        /**
         * Returns the fully-qualified class name of this annotation processor.
         */
        String getClassName();

        /**
         * Returns the type of this annotation processor.
         *
         * <p>Can be used to determine whether this processor was incremental.
         */
        Type getType();

        /**
         * Returns the total execution time of this annotation processor.
         */
        Duration getDuration();

        /**
         * Type of annotation processor.
         *
         * @since 5.1
         */
        @Incubating
        enum Type {
            ISOLATING,
            AGGREGATING,
            UNKNOWN
        }
    }

}
