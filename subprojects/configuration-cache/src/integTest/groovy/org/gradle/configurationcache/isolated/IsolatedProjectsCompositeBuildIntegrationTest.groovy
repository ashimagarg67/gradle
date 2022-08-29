/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.isolated

class IsolatedProjectsCompositeBuildIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {
    def "can build libraries composed from multiple builds"() {
        settingsFile << """
            includeBuild("libs")
        """
        file("libs/settings.gradle") << """
            include("a")
        """
        file("libs/a/build.gradle") << """
            plugins { id('java-library') }
            group = 'libs'
        """
        file("build.gradle") << """
            plugins { id('java-library') }
            dependencies { implementation 'libs:a:' }
        """

        when:
        configurationCacheRun(":assemble")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":libs", ":libs:a")
        }

        when:
        configurationCacheRun(":assemble")

        then:
        fixture.assertStateLoaded()
    }

    def "getGradle() references are equal across projects in one build"() {
        settingsFile << """
            include("a")
            includeBuild("include")
        """
        file("build.gradle") << """
            def g = gradle
            assert(g.gradle === g)
            allprojects {
                assert(it.gradle === g)
            }
            g.projectsEvaluated {
                assert(it === g)
            }
        """
        file("include/build.gradle") << """
            def root = gradle.root
            assert(gradle != root)
            assert(gradle.parent === root)
            root.allprojects {
                assert(it.gradle === root)
            }
            root.projectsEvaluated {
                assert(it === root)
            }
        """

        when:
        configurationCacheRun(":a:help", ":include:help")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":include")
        }
    }

}
