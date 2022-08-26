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

package org.gradle.api.internal.tasks

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.enterprise.core.GradleEnterprisePluginAdapter
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationTestFor

class SnapshotTaskInputsOperationIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        expectReindentedValidationMessage()
    }

    def "task output caching key is exposed when build cache is enabled"() {
        given:
        executer.withBuildCacheEnabled()

        when:
        buildFile << customTaskCode('foo', 'bar')
        succeeds('customTask')

        then:
        def result = operations.first(SnapshotTaskInputsBuildOperationType).result

        then:
        result.hash != null
        result.inputValueHashes.keySet() == ['input1', 'input2'] as Set
        result.outputPropertyNames == ['outputFile1', 'outputFile2']
    }

    def "task output caching key is exposed when scan plugin is applied"() {
        given:
        settingsFile << """
            services.get($GradleEnterprisePluginManager.name).registerAdapter([buildFinished: {}, shouldSaveToConfigurationCache: { false }, executionPhaseStarted: {}] as $GradleEnterprisePluginAdapter.name)
        """

        buildFile << customTaskCode('foo', 'bar')

        when:
        succeeds('customTask')

        then:
        def result = operations.first(SnapshotTaskInputsBuildOperationType).result

        then:
        result.hash != null
        result.inputValueHashes.keySet() == ['input1', 'input2'] as Set
        result.outputPropertyNames == ['outputFile1', 'outputFile2']
    }

    def "task output caching key is not exposed by default"() {
        when:
        buildFile << customTaskCode('foo', 'bar')
        succeeds('customTask')

        then:
        !operations.hasOperation(SnapshotTaskInputsBuildOperationType)
    }

    def "handles task with no outputs"() {
        when:
        buildScript """
            task noOutputs {
                doLast {}
            }
        """
        succeeds('noOutputs', "--build-cache")

        then:
        def result = operations.first(SnapshotTaskInputsBuildOperationType).result
        result.hash == null
        result.classLoaderHash == null
        result.actionClassLoaderHashes == null
        result.actionClassNames == null
        result.inputValueHashes == null
        result.outputPropertyNames == null
    }

    def "handles task with no inputs"() {
        when:
        buildScript """
            task noInputs {
                outputs.file "foo.txt"
                doLast {}
            }
        """
        succeeds('noInputs', "--build-cache")

        then:
        def result = operations.first(SnapshotTaskInputsBuildOperationType).result
        result.hash != null
        result.classLoaderHash != null
        result.actionClassLoaderHashes != null
        result.actionClassNames != null
        result.inputValueHashes == null
        result.outputPropertyNames != null
    }

    def "not sent for task with no actions"() {
        when:
        buildScript """
            task noActions {
            }
        """
        succeeds('noActions', "--build-cache")

        then:
        !operations.hasOperation(SnapshotTaskInputsBuildOperationType)
    }

    @ValidationTestFor(
        ValidationProblemId.UNKNOWN_IMPLEMENTATION
    )
    def "handles invalid implementation classloader"() {
        given:
        buildScript """
            def classLoader = new GroovyClassLoader(this.class.classLoader)
            def clazz = classLoader.parseClass(\"\"\"${customTaskImpl()}\"\"\")
            task customTask(type: clazz){
                input1 = 'foo'
                input2 = 'bar'
            }
        """

        when:
        fails('customTask', '--build-cache')

        then:
        failureDescriptionStartsWith("Some problems were found with the configuration of task ':customTask' (type 'CustomTask').")
        failureDescriptionContains(implementationUnknown {
            implementationOfTask(':customTask')
            unknownClassloader('CustomTask_Decorated')
        })
        failureDescriptionContains(implementationUnknown {
            additionalTaskAction(':customTask')
            unknownClassloader('CustomTask_Decorated')
        })
        def result = operations.first(SnapshotTaskInputsBuildOperationType).result
        result.hash == null
        result.classLoaderHash == null
        result.actionClassLoaderHashes == null
        result.actionClassNames == null
        result.inputValueHashes == null
        result.outputPropertyNames == null
    }

    @ValidationTestFor(
        ValidationProblemId.UNKNOWN_IMPLEMENTATION
    )
    def "handles invalid action classloader"() {
        given:
        buildScript """
            ${customTaskCode('foo', 'bar')}
            def classLoader = new GroovyClassLoader(this.class.classLoader)
            def c = classLoader.parseClass '''
                class A implements $Action.name {
                    void execute(task) {}
                }
            '''
            customTask.doLast(c.getConstructor().newInstance())
        """

        when:
        fails('customTask', '--build-cache')

        then:
        failureDescriptionStartsWith("A problem was found with the configuration of task ':customTask' (type 'CustomTask').")
        failureDescriptionContains(implementationUnknown {
            additionalTaskAction(':customTask')
            unknownClassloader('A')
        })
        def result = operations.first(SnapshotTaskInputsBuildOperationType).result
        result.hash == null
        result.classLoaderHash == null
        result.actionClassLoaderHashes == null
        result.actionClassNames == null
        result.inputValueHashes == null
        result.outputPropertyNames == null
    }

    def "exposes file inputs, ignoring empty directories"() {
        given:
        withBuildCache()
        settingsFile << "include 'a', 'b'"
        createDir("a") {
            file("build.gradle") << "plugins { id 'java' }"
            dir("src/main/java") {
                file("A.java") << "class A {}"
                file("B.java") << "class B {}"
                dir("a") {
                    file("A.java") << "package a; class A {}"
                    dir("a") {
                        file("A.java") << "package a.a; class A {}"
                    }
                }
                dir("empty") {
                    dir("empty")
                }
                dir("nonempty") {
                    dir("nonempty") {
                        dir("empty")
                        file("Z.java") << "package nonempty.nonempty; class Z {}"
                    }
                }
            }
        }

        createDir("b") {
            file("build.gradle") << """
                plugins { id 'java' }
                dependencies { implementation project(":a") }
                sourceSets.main.java.srcDir "other"
            """
            dir("src/main/java") {
                file("Thing.java") << "class Thing {}"
            }
            dir("other") {
                file("Other.java") << "class Other {}"
            }
        }

        when:
        succeeds("b:jar")

        then:
        def result = snapshotResults(":a:compileJava")
        def aCompileJava = result.inputFileProperties
        aCompileJava.size() == 5

        // Not in just-values property
        aCompileJava.keySet().every { !result.inputValueHashes.containsKey(it) }

        with(aCompileJava.classpath) {
            hash != null
            roots.empty
            normalization == "COMPILE_CLASSPATH"
            attributes == ['DIRECTORY_SENSITIVITY_DEFAULT', 'FINGERPRINTING_STRATEGY_COMPILE_CLASSPATH', 'LINE_ENDING_SENSITIVITY_DEFAULT']
        }

        with(aCompileJava["options.sourcepath"] as Map<String, ?>) {
            hash != null
            roots.empty
            normalization == "RELATIVE_PATH"
            attributes == ['DIRECTORY_SENSITIVITY_IGNORE_DIRECTORIES', 'FINGERPRINTING_STRATEGY_RELATIVE_PATH', 'LINE_ENDING_SENSITIVITY_DEFAULT']
        }

        with(aCompileJava["options.annotationProcessorPath"] as Map<String, ?>) {
            hash != null
            roots.empty
            normalization == "CLASSPATH"
            attributes == ['DIRECTORY_SENSITIVITY_DEFAULT', 'FINGERPRINTING_STRATEGY_CLASSPATH', 'LINE_ENDING_SENSITIVITY_DEFAULT']
        }

        with(aCompileJava.stableSources) {
            hash != null
            normalization == "RELATIVE_PATH"
            attributes == ['DIRECTORY_SENSITIVITY_IGNORE_DIRECTORIES', 'FINGERPRINTING_STRATEGY_RELATIVE_PATH', 'LINE_ENDING_SENSITIVITY_NORMALIZE_LINE_ENDINGS']
            roots.size() == 1
            with(roots[0]) {
                path == file("a/src/main/java").absolutePath
                children.size() == 4
                with(children[0]) {
                    path == "a"
                    children.size() == 2
                    with(children[0]) {
                        path == "a"
                        children.size() == 1
                        with(children[0]) {
                            path == "A.java"
                            hash != null
                        }
                    }
                    with(children[1]) {
                        path == "A.java"
                        hash != null
                    }
                }
                with(children[1]) {
                    path == "A.java"
                    hash != null
                }
                with(children[2]) {
                    path == "B.java"
                    hash != null
                }
                with(children[3]) {
                    path == "nonempty"
                    hash == null
                    children.size() == 1
                    with(children[0]) {
                        path == "nonempty"
                        hash == null
                        children.size() == 1
                        with(children[0]) {
                            path == "Z.java"
                            hash != null
                            children == null
                        }
                    }
                }
            }
        }

        def bCompileJava = snapshotResults(":b:compileJava").inputFileProperties
        with(bCompileJava.classpath) {
            hash != null
            roots.size() == 1
            with(roots[0]) {
                path == file("a/build/libs/a.jar").absolutePath
                !containsKey("children")
            }
        }
        with(bCompileJava.stableSources) {
            hash != null
            roots.size() == 2
            with(roots[0]) {
                path == file("b/src/main/java").absolutePath
                children.size() == 1
                children[0].path == "Thing.java"
            }
            with(roots[1]) {
                path == file("b/other").absolutePath
                children.size() == 1
                children[0].path == "Other.java"
            }
        }

        def bJar = snapshotResults(":b:jar").inputFileProperties
        with(bJar["rootSpec\$1"]) {
            hash != null
            roots.size() == 1
            with(roots[0]) {
                path == file("b/build/classes/java/main").absolutePath
                children.size() == 2
                children[0].path == "Other.class"
                children[1].path == "Thing.class"
            }
        }
    }

    def "exposes file inputs, not ignoring empty directories"() {
        given:
        withBuildCache()
        settingsFile << "include 'a'"
        createDir("a") {
            file("build.gradle") << """
                task foo {
                    inputs.dir('src').ignoreEmptyDirectories(false).withPropertyName('src')
                    outputs.file('output.txt')
                    doLast {
                        file('output.txt') << 'do stuff'
                    }
                }
            """
            dir("src") {
                file("A.txt") << "fooA"
                file("B.txt") << "fooB"
                dir("a") {
                    file("A.txt") << "fooA"
                    dir("a") {
                        file("A.txt") << "fooA"
                    }
                }
                dir("empty") {
                    dir("empty")
                }
            }
        }

        when:
        succeeds("a:foo")

        then:
        def result = snapshotResults(":a:foo")
        def aFoo = result.inputFileProperties
        with(aFoo.src) {
            hash != null
            normalization == "ABSOLUTE_PATH"
            attributes == ['DIRECTORY_SENSITIVITY_DEFAULT', 'FINGERPRINTING_STRATEGY_ABSOLUTE_PATH', 'LINE_ENDING_SENSITIVITY_DEFAULT']
            roots.size() == 1
            with(roots[0]) {
                path == file("a/src").absolutePath
                children.size() == 4
                with(children[0]) {
                    path == "a"
                    children.size() == 2
                    with(children[0]) {
                        path == "a"
                        children.size() == 1
                        with(children[0]) {
                            path == "A.txt"
                            hash != null
                        }
                    }
                    with(children[1]) {
                        path == "A.txt"
                        hash != null
                    }
                }
                with(children[1]) {
                    path == "A.txt"
                    hash != null
                }
                with(children[2]) {
                    path == "B.txt"
                    hash != null
                }
                with(children[3]) {
                    path == "empty"
                    children.size() == 1
                    with(children[0]) {
                        path == "empty"
                        children.empty
                    }
                }
            }
        }
    }

    def "single root file are represented as roots"() {
        given:
        withBuildCache()
        file('inputFile').text = 'inputFile'
        buildScript """
            task copy(type:Copy) {
               from 'inputFile'
               into 'destDir'
            }
        """
        when:
        succeeds("copy")

        then:
        def copy = snapshotResults(":copy").inputFileProperties

        with(copy['rootSpec$1']) {
            hash != null
            roots.size() == 1
            with(roots[0]) {
                hash != null
                path == file("inputFile").absolutePath
                !containsKey("children")
            }
            normalization == "RELATIVE_PATH"
            attributes == ['DIRECTORY_SENSITIVITY_DEFAULT', 'FINGERPRINTING_STRATEGY_RELATIVE_PATH', 'LINE_ENDING_SENSITIVITY_DEFAULT']
        }
    }

    @ValidationTestFor(
        ValidationProblemId.UNKNOWN_IMPLEMENTATION
    )
    def "handles invalid nested bean classloader"() {
        given:
        buildScript """
            ${customTaskCode('foo', 'bar')}
            def classLoader = new GroovyClassLoader(this.class.classLoader)
            def c = classLoader.parseClass '''
                class A {
                    @$Input.name
                    String input = 'nested'
                }
            '''
            customTask.bean = c.newInstance()
        """

        when:
        fails('customTask', '--build-cache')

        then:
        failureDescriptionStartsWith("A problem was found with the configuration of task ':customTask' (type 'CustomTask').")
        failureDescriptionContains(implementationUnknown {
            nestedProperty('bean')
            unknownClassloader('A')
        })
        def result = operations.first(SnapshotTaskInputsBuildOperationType).result
        result.hash == null
        result.classLoaderHash == null
        result.actionClassLoaderHashes == null
        result.actionClassNames == null
        result.inputValueHashes == null
        result.outputPropertyNames == null
    }

    private static String customTaskCode(String input1, String input2) {
        """
            ${customTaskImpl()}
            task customTask(type: CustomTask){
                input1 = '$input1'
                input2 = '$input2'
            }
        """
    }

    private static String customTaskImpl() {
        """
            @$CacheableTask.name
            class CustomTask extends $DefaultTask.name {

                @$Input.name
                String input2

                @$Input.name
                String input1

                @$OutputFile.name
                File outputFile2 = new File(temporaryDir, "output2.txt")

                @$OutputFile.name
                File outputFile1 = new File(temporaryDir, "output1.txt")

                @$TaskAction.name
                void generate() {
                    outputFile1.text = "done1"
                    outputFile2.text = "done2"
                }

                @$Nested.name
                @$Optional.name
                Object bean
            }

        """
    }

    Map<String, ?> snapshotResults(String taskPath) {
        def aCompileJavaTask = operations.first(ExecuteTaskBuildOperationType) {
            it.details.taskPath == taskPath
        }
        def results = operations.children(aCompileJavaTask, SnapshotTaskInputsBuildOperationType)
        assert results.size() == 1
        results.first().result
    }

}
