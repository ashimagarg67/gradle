/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous.jdk7

import org.gradle.launcher.continuous.AbstractContinuousIntegrationTest
import spock.lang.Ignore

import java.nio.file.Files
import java.nio.file.Paths

class SymlinkContinuousIntegrationTest extends AbstractContinuousIntegrationTest {
    @Ignore("TODO - check implementation specific behavior of symlinks across mac/linux/win")
    def "can use symlink for input"() {
        given:
        def baseDir = file("src").createDir()
        def sourceFile = baseDir.file("A")
        sourceFile.text = "original"
        def symlink = baseDir.file("link")
        buildFile << """
    task echo {
        def symlink = file("${symlink.toURI()}")
        inputs.files symlink
        doLast {
            println "text: " + (symlink.exists() ? symlink.text:"missing")
        }
    }
"""
        when: "symlink is used as input and exists"
        Files.createSymbolicLink(Paths.get(symlink.toURI()), Paths.get(sourceFile.toURI()))
        then:
        succeeds("echo")
        executedAndNotSkipped(":echo")
        output.contains("text: original")
        when: "symlink is deleted"
        symlink.delete()
        then:
        succeeds()
        executedAndNotSkipped(":echo")
        output.contains("text: missing")
        when: "symlink is created"
        Files.createSymbolicLink(Paths.get(symlink.toURI()), Paths.get(sourceFile.toURI()))
        then:
        succeeds()
        executedAndNotSkipped(":echo")
        output.contains("text: original")
        when: "changes made to target of symlink"
        sourceFile.text = "changed"
        then:
        // TODO: This might be implementation specific
        noBuildTriggered()
    }

    @Ignore("This behavior seems to be different from the file case")
    def "can use symlinked directory for input"() {
        given:
        def baseDir = file("src").createDir()
        def targetDir = baseDir.file("target").createDir()
        targetDir.files("A", "B")*.createFile()

        def symlink = baseDir.file("link")
        buildFile << """
    task echo {
        def symlink = files("${symlink.toURI()}")
        inputs.files symlink
        doLast {
            println "isEmpty: " + symlink.isEmpty()
        }
    }
"""
        when: "symlink is used as input and exists"
        Files.createSymbolicLink(Paths.get(symlink.toURI()), Paths.get(targetDir.toURI()))
        then:
        succeeds("echo")
        executedAndNotSkipped(":echo")
        output.contains("isEmpty: false")
        when: "symlink is deleted"
        symlink.delete()
        then:
        succeeds()
        executedAndNotSkipped(":echo")
        output.contains("isEmpty: true")
        when: "symlink is created"
        Files.createSymbolicLink(Paths.get(symlink.toURI()), Paths.get(targetDir.toURI()))
        then:
        succeeds()
        executedAndNotSkipped(":echo")
        output.contains("isEmpty: false")
        when: "changes made to target of symlink"
        targetDir.file("C").createFile()
        then:
        // TODO: This might be implementation specific
        noBuildTriggered()
    }
}
