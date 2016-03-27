/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.daemon

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.IgnoreIf
import spock.lang.Issue

@Issue("GRADLE-2460")
class DaemonSystemPropertiesIntegrationTest extends DaemonIntegrationSpec {
    def "standard and sun. client JVM system properties are not carried over to daemon JVM"() {
        given:
        file("build.gradle") << """
task verify << {
    assert System.getProperty("java.vendor") != "hollywood"
    assert System.getProperty("java.vendor") != null
    assert System.getProperty("sun.sunny") == null
}
        """

        expect:
        executer.withBuildJvmOpts("-Djava.vendor=hollywood", "-Dsun.sunny=california").withTasks("verify").run()
    }

    def "other client JVM system properties are carried over to daemon JVM"() {
        given:
        file("build.gradle") << """
task verify << {
    assert System.getProperty("foo.bar") == "baz"
}
        """

        expect:
        executer.withBuildJvmOpts("-Dfoo.bar=baz").withTasks("verify").run()
    }


    def "forks new daemon when file encoding set to different value via commandline"() {
        setup:
        buildScript """
            task verify {
                doFirst {
                    println "verified = " + java.nio.charset.Charset.defaultCharset().name()
                }
            }
        """

        when:
        executer.withArgument("-Dfile.encoding=UTF-8")
        run("verify")

        then:
        daemons.daemons.size() == 1

        when:
        executer.withArgument("-Dfile.encoding=ISO-8859-1")
        run("verify")

        then:
        output.contains("verified = ISO-8859-1")
        daemons.daemons.size() == 2
    }

    // Java 6 does not allow spaces in tmpdir folders
    @IgnoreIf({ !JavaVersion.current().java7Compatible })
    def "forks new daemon when tmpdir is set to different value via commandline"() {
        setup:
        buildScript """
            task verify {
                doFirst {
                    println "verified = \${File.createTempFile('pre', 'post')}"
                }
            }
        """

        def tmpPath1 = tempFolder('folder1')
        when:
        executer.withArgument("-Djava.io.tmpdir=${tmpPath1}")
        run("verify")

        then:
        daemons.daemons.size() == 1
        output.contains("verified = $tmpPath1")

        when:
        def tmpPath2 = tempFolder('tmpPath2')
        executer.withArgument("-Djava.io.tmpdir=${tmpPath2}")
        run "verify"
        then:
        output.contains("verified = $tmpPath2")
        daemons.daemons.size() == 2
    }

    def "forks new daemon when file encoding is set to different value via GRADLE_OPTS"() {
        setup:
        executer.requireGradleHome()
        buildScript """
            println "GRADLE_VERSION: " + gradle.gradleVersion

            task verify {
                doFirst {
                    println "verified = " + java.nio.charset.Charset.defaultCharset().name()
                }
            }
        """

        when:
        executer.withEnvironmentVars(GRADLE_OPTS: "-Dfile.encoding=UTF-8");
        run "verify"

        then:
        String gradleVersion = (output =~ /GRADLE_VERSION: (.*)/)[0][1]
        daemons(gradleVersion).daemons.size() == 1

        when:
        executer.withEnvironmentVars(GRADLE_OPTS: "-Dfile.encoding=ISO-8859-1");
        executer.withArgument("-i")
        run "verify"

        then:
        output.contains("verified = ISO-8859-1")
        daemons(gradleVersion).daemons.size() == 2
    }

    @IgnoreIf({ !JavaVersion.current().java7Compatible })
    def "forks new daemon when tmpdir is set to different value via GRADLE_OPTS"() {
        setup:
        executer.requireGradleHome()
        buildScript """
            println "GRADLE_VERSION: " + gradle.gradleVersion

            task verify {
                doFirst {
                    println "verified = \${File.createTempFile('pre', 'post')}"
                }
            }
        """

        when:
        executer.withEnvironmentVars(GRADLE_OPTS: "\"-Djava.io.tmpdir=${tempFolder('folder1')}\"")
        run "verify"

        then:
        String gradleVersion = (output =~ /GRADLE_VERSION: (.*)/)[0][1]
        daemons(gradleVersion).daemons.size() == 1

        when:
        def tmpPath2 = tempFolder('tmpPath2')
        executer.withEnvironmentVars(GRADLE_OPTS: "\"-Djava.io.tmpdir=${tmpPath2}\"")
        run "verify"

        then:
        output.contains("verified = $tmpPath2")
        daemons(gradleVersion).daemons.size() == 2
    }

    String tempFolder(String folderName) {
        def dir = new TestNameTestDirectoryProvider().createDir(folderName)
        dir.mkdirs();
        dir.absolutePath
    }
}
