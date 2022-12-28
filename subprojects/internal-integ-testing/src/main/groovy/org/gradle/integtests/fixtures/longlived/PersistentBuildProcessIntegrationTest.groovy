/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.fixtures.longlived

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.IntegTestPreconditions
import org.gradle.test.fixtures.condition.Requires

/**
 * Base class for test that test the behaviour of Gradle when running subsequent builds in the same build process.
 */
@Requires(IntegTestPreconditions.IsLongLivingProcess)
class PersistentBuildProcessIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.requireIsolatedDaemons()
    }
}
