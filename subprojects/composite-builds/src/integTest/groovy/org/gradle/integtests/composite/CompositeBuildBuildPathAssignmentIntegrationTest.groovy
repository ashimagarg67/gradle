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

package org.gradle.integtests.composite

import org.gradle.initialization.BuildIdentifiedProgressDetails
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.build.BuildTestFixture
import org.gradle.test.fixtures.file.TestDirectoryProvider

class CompositeBuildBuildPathAssignmentIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    BuildOperationsFixture fixture = new BuildOperationsFixture(executer, temporaryFolder)

    def "can have buildLogic build and include build with buildLogic build"() {
        def builds = nestedBuilds {
            includedBuild {
                buildLogic
            }
            includingBuild {
                buildLogic
                includeBuild '../includedBuild'
            }
        }
        def includingBuild = builds.find { it.rootProjectName == 'includingBuild' }

        when:
        succeeds(includingBuild, "help")
        then:
        assignedBuildPaths ==~ [
            ':includedBuild',
            ':includedBuild:buildLogic',
            ':',
            ':buildLogic'
        ]
    }

    def "build paths are selected based on the directory hierarchy"() {
        def builds = nestedBuilds {
            includedBuild {
                buildLogic
                nested {
                    buildLogic
                    includeBuild '../buildLogic'
                }
            }
            includingBuild {
                includeBuild '../includedBuild'
                buildLogic
                nested {
                    includeBuild '../buildLogic'
                    buildLogic
                }
            }
        }
        def includingBuild = builds.find { it.rootProjectName == 'includingBuild' }

        when:
        succeeds(includingBuild, 'help')
        then:
        assignedBuildPaths ==~ [
            ':includedBuild',
            ':includedBuild:buildLogic',
            ':includedBuild:nested',
            ':includedBuild:nested:buildLogic',
            ':',
            ':buildLogic',
            ':nested',
            ':nested:buildLogic'
        ]
    }

    def "buildSrc is relative to its including build"() {
        def builds = nestedBuilds {
            includedBuild {
                nested
            }
            includingBuild {
                nested
            }
        }


        def includingBuild = builds.find { it.rootProjectName == 'includingBuild' }
        builds.each { build ->
            def buildSrc = build.createDir('buildSrc')
            buildSrc.createFile('settings.gradle')
            buildSrc.createFile('build.gradle')
        }

        when:
        succeeds(includingBuild, 'help')
        then:
        assignedBuildPaths ==~ [':nested', ':nested:buildSrc', ':', ':buildSrc']
    }

    private List<Object> getAssignedBuildPaths() {
        fixture.progress(BuildIdentifiedProgressDetails).findAll { it.details.buildPath }*.details*.buildPath
    }

    private void configureBuildConfigurationOutput(List<BuildTestFile> builds) {
        builds.each {
            it.buildFile << """
                    subprojects {
                        // The Java plugin forces the configuration of the included builds
                        apply plugin: 'java'
                    }
                """
        }
    }

    List<BuildTestFile> nestedBuilds(Closure configuration) {
        def builds = new RootNestedBuildSpec(temporaryFolder).with(configuration).nestedBuilds
        configureBuildConfigurationOutput(builds)
        builds
    }

    class RootNestedBuildSpec {
        private final TestDirectoryProvider temporaryFolder
        List<BuildTestFile> nestedBuilds = []

        RootNestedBuildSpec(TestDirectoryProvider temporaryFolder) {
            this.temporaryFolder = temporaryFolder
        }

        def methodMissing(String name, def args) {
            def build = new BuildTestFile(temporaryFolder.testDirectory.file(name), name)
            new BuildTestFixture(build).multiProjectBuild(name, ["project1", "project2"])
            nestedBuilds.add(build)

            def nestedBuildSpec = new NestedBuildSpec(build, nestedBuilds)
            if (args.length == 1 && args[0] instanceof Closure) {
                nestedBuildSpec.with(args[0])
            } else {
                throw new MissingMethodException(name, getClass(), args)
            }
            return nestedBuildSpec
        }
    }

    class NestedBuildSpec {
        private final BuildTestFile build
        private final List<BuildTestFile> nestedBuilds

        NestedBuildSpec(BuildTestFile build, nestedBuilds) {
            this.nestedBuilds = nestedBuilds
            this.build = build
        }

        def includeBuild(String path) {
            build.settingsFile << """
                includeBuild '$path'
            """
        }

        def propertyMissing(String name) {
            createNestedBuild(name)
        }

        def methodMissing(String name, def args) {
            if (args.length == 1 && args[0] instanceof Closure) {
                return createNestedBuild(name, args[0])
            } else {
                throw new MissingMethodException(name, getClass(), args)
            }
        }

        NestedBuildSpec createNestedBuild(String name, Closure configuration = {}) {
            includeBuild(name)
            def nestedBuild = new BuildTestFile(build.file(name), name)
            nestedBuilds.add(nestedBuild)
            new BuildTestFixture(nestedBuild).multiProjectBuild(name, ["project1", "project2"])
            def nestedBuildSpec = new NestedBuildSpec(nestedBuild, nestedBuilds)
            nestedBuildSpec.with(configuration)
            return nestedBuildSpec
        }
    }
}


