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

/**
 * A {@code VerificationTask} is a task which performs some verification of the artifacts produced by a build.
 */
public interface VerificationTask {
    /**
     * Specifies whether the build should break when the verifications performed by this task fail.
     *
     * @param ignoreFailures false to break the build on failure, true to ignore the failures. The default is false.
     * @return this
     */
    VerificationTask setIgnoreFailures(boolean ignoreFailures);

    /**
     * Specifies whether the build should break when the verifications performed by this task fail.
     *
     * @return false, when the build should break on failure, true when the failures should be ignored.
     */
    boolean isIgnoreFailures();

    /**
     * Specifies whether the build should display violations on the console or not.
     *
     * @param displayViolations false to suppress console output, true to display violations on the console. The default is true.
     * @return this
     */
    VerificationTask setDisplayViolations(boolean displayViolations);

    /**
     * Specifies whether the build should display violations on the console or not.
     *
     * @return false, to suppress console output, true, to display violations on the console. The default is true.
     */
    boolean isDisplayViolations();

}
