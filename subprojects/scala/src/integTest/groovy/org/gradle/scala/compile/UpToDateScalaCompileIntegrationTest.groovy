/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.scala.compile


import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.util.Requires

import static org.gradle.api.JavaVersion.VERSION_11
import static org.gradle.api.JavaVersion.VERSION_1_8
import static org.gradle.integtests.fixtures.jvm.JavaToolchainBuildOperationsHelper.assertToolchainUsages
import static org.gradle.integtests.fixtures.jvm.JavaToolchainBuildOperationsHelper.toolchainEvents

class UpToDateScalaCompileIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file('src/main/scala/Person.scala') << "class Person(name: String)"
    }

    def "compile is out of date when changing the #changedVersion version"() {
        buildScript(scalaProjectBuildScript(defaultZincVersion, defaultScalaVersion))

        when:
        run 'compileScala'

        then:
        executedAndNotSkipped ':compileScala'

        when:
        run 'compileScala'

        then:
        skipped ':compileScala'

        when:
        buildScript(scalaProjectBuildScript(newZincVersion, newScalaVersion))
        run 'compileScala'

        then:
        executedAndNotSkipped ':compileScala'

        where:
        newScalaVersion | newZincVersion
        '2.11.12'       | '1.6.0'
        '2.12.6'        | '1.6.1'
        defaultScalaVersion = '2.11.12'
        defaultZincVersion = ScalaBasePlugin.DEFAULT_ZINC_VERSION
        changedVersion = defaultScalaVersion != newScalaVersion ? 'scala' : 'zinc'
    }

    @Requires(adhoc = { AvailableJavaHomes.getJdk(VERSION_1_8) && AvailableJavaHomes.getJdk(VERSION_11) })
    def "compile is out of date when changing the java version"() {
        def jdk8 = AvailableJavaHomes.getJdk(VERSION_1_8)
        def jdk11 = AvailableJavaHomes.getJdk(VERSION_11)

        buildScript(scalaProjectBuildScript(ScalaBasePlugin.DEFAULT_ZINC_VERSION, '2.12.6'))
        when:
        executer.withJavaHome(jdk8.javaHome)
        run 'compileScala'

        then:
        executedAndNotSkipped(':compileScala')

        when:
        executer.withJavaHome(jdk8.javaHome)
        run 'compileScala'
        then:
        skipped ':compileScala'

        when:
        executer.withJavaHome(jdk11.javaHome)
        run 'compileScala', '--info'
        then:
        executedAndNotSkipped(':compileScala')
    }

    def scalaProjectBuildScript(String zincVersion, String scalaVersion) {
        return """
            apply plugin: 'scala'

            ${mavenCentralRepository()}

            dependencies {
                implementation "org.scala-lang:scala-library:${scalaVersion}"
            }

            scala {
                zincVersion = "${zincVersion}"
            }

            sourceCompatibility = '1.7'
            targetCompatibility = '1.7'
        """.stripIndent()
    }

    def "compilation emits toolchain usage events"() {
        def operations = new BuildOperationsFixture(executer, temporaryFolder)
        def jdkMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.getDifferentJdk { it.languageVersion.isJava8Compatible() })

        buildScript """
            apply plugin: 'scala'

            ${mavenCentralRepository()}

            dependencies {
                implementation "org.scala-lang:scala-library:2.13.8" // must be above 2.13.1
            }

            scala {
                zincVersion = "1.7.1"
            }
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdkMetadata.languageVersion.majorVersion})
                }
            }

            tasks.withType(ScalaCompile) {
                scalaCompileOptions.with {
                    additionalParameters = (additionalParameters ?: []) + "-target:8"
                }
            }
        """

        when:
        executer
            .withArgument("-Porg.gradle.java.installations.paths=${jdkMetadata.javaHome.toAbsolutePath()}")
        run 'compileScala'
        def events = toolchainEvents(operations, ':compileScala')

        then:
        executedAndNotSkipped ':compileScala'
        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")

        when:
        executer
            .withArgument("-Porg.gradle.java.installations.paths=${jdkMetadata.javaHome.toAbsolutePath()}")
        run 'compileScala'
        events = toolchainEvents(operations, ':compileScala')

        then:
        skipped ':compileScala'
        assertToolchainUsages(events, jdkMetadata, "JavaLauncher")
    }
}
