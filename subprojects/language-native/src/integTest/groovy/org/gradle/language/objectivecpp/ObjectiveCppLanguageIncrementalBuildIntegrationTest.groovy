/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.objectivecpp

import org.gradle.language.objectivec.ObjectiveCLanguageIncrementalBuildIntegrationTest
import org.gradle.nativeplatform.fixtures.NativeLanguageRequirement
import org.gradle.nativeplatform.fixtures.RequiresSupportedLanguage
import org.gradle.nativeplatform.fixtures.app.IncrementalHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ObjectiveCppHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.OBJECTIVE_C_SUPPORT)
@RequiresSupportedLanguage(NativeLanguageRequirement.OBJECTIVE_C_PLUS_PLUS)
class ObjectiveCppLanguageIncrementalBuildIntegrationTest  extends ObjectiveCLanguageIncrementalBuildIntegrationTest{
    @Override
    IncrementalHelloWorldApp getHelloWorldApp() {
        return new ObjectiveCppHelloWorldApp()
    }
}
