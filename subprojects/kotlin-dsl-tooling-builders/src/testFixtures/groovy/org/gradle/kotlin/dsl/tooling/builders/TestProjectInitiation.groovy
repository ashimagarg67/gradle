/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.builders

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.TestFile

import java.util.zip.ZipOutputStream

import static org.gradle.util.internal.TextUtil.escapeString

@CompileStatic
trait TestProjectInitiation {

    abstract TestFile file(Object... path)

    String defaultSettingsScript = ""

    String repositoriesBlock = """
        repositories {
            ${RepoScriptBlockUtil.gradlePluginRepositoryDefinition(GradleDsl.KOTLIN)}
        }
    """.stripIndent()


    BuildSpec withMultiProjectBuildWithBuildSrc() {
        withBuildSrc()
        def someJar = withEmptyJar("classes_some.jar")
        def settingsJar = withEmptyJar("classes_settings.jar")
        def rootJar = withEmptyJar("classes_root.jar")
        def aJar = withEmptyJar("classes_a.jar")
        def bJar = withEmptyJar("classes_b.jar")
        def precompiledJar = withEmptyJar("classes_b_precompiled.jar")

        def some = withFile("some.gradle.kts", """
            buildscript {
                dependencies {
                    classpath(files("${escapeString(someJar)}"))
                }
            }
        """)
        def settings = withSettings("""
            buildscript {
                dependencies {
                    classpath(files("${escapeString(settingsJar)}"))
                }
            }
            apply(from = "some.gradle.kts")
            include("a", "b")
        """)
        def root = withBuildScript("""
            buildscript {
                dependencies {
                    classpath(files("${escapeString(rootJar)}"))
                }
            }
            apply(from = "some.gradle.kts")
        """)
        def a = withBuildScriptIn("a", """
            buildscript {
                dependencies {
                    classpath(files("${escapeString(aJar)}"))
                }
            }
            apply(from = "../some.gradle.kts")
        """)
        def b = withBuildScriptIn("b", """
            plugins {
                `kotlin-dsl`
            }
            buildscript {
                dependencies {
                    classpath(files("${escapeString(bJar)}"))
                }
            }
            apply(from = "../some.gradle.kts")

            $repositoriesBlock

            dependencies {
                implementation(files("${escapeString(precompiledJar)}"))
            }
        """)
        def precompiled = withFile("b/src/main/kotlin/precompiled/precompiled.gradle.kts", "")
        return new BuildSpec(
            scripts: [
                settings: settings,
                root: root,
                a: a,
                b: b,
                precompiled: precompiled
            ],
            appliedScripts: [
                some: some
            ],
            jars: [
                some: someJar,
                settings: settingsJar,
                root: rootJar,
                a: aJar,
                b: bJar,
                precompiled: precompiledJar
            ]
        )
    }

    TestFile withDefaultSettings() {
        return withSettings(defaultSettingsScript)
    }

    TestFile withSettings(String script) {
        return withSettingsIn(".", script)
    }

    TestFile withDefaultSettingsIn(String baseDir) {
        return withSettingsIn(baseDir, defaultSettingsScript)
    }

    TestFile withSettingsIn(String baseDir, String script) {
        return withFile("$baseDir/settings.gradle.kts", script)
    }

    TestFile withBuildScript(String script = "") {
        return withBuildScriptIn(".", script)
    }

    TestFile withBuildScriptIn(String baseDir, String script = "") {
        return withFile("$baseDir/build.gradle.kts", script)
    }

    TestFile withFile(String path, String content = "") {
        return file(path).tap { text = content.stripIndent() }
    }

    TestFile withEmptyJar(String path) {
        return this.file(path).tap { jarFile ->
            jarFile.parentFile.mkdirs()
            new ZipOutputStream(jarFile.newOutputStream()).close()
        }
    }

    void withBuildSrc() {
        this.withFile("buildSrc/src/main/groovy/build/Foo.groovy", """
            package build
            class Foo {}
        """)
    }

    void withKotlinBuildSrc() {
        withDefaultSettingsIn("buildSrc")
        withBuildScriptIn("buildSrc", """
            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock
        """)
    }

    ProjectSourceRoots[] withMultiProjectKotlinBuildSrc() {
        withDefaultSettingsIn("buildSrc").append("""
            include(":a", ":b", ":c")
        """)
        withFile("buildSrc/build.gradle.kts", """
            plugins {
                java
                `kotlin-dsl` apply false
            }

            val kotlinDslProjects = listOf(project.project(":a"), project.project(":b"))

            kotlinDslProjects.forEach {
                it.apply(plugin = "org.gradle.kotlin.kotlin-dsl")
            }

            dependencies {
                kotlinDslProjects.forEach {
                    "runtimeOnly"(project(it.path))
                }
            }

            allprojects {
                $repositoriesBlock
            }
        """)
        withFile("buildSrc/b/build.gradle.kts", """dependencies { implementation(project(":c")) }""")
        withFile("buildSrc/c/build.gradle.kts", "plugins { java }")

        return [
            withMainSourceSetJavaIn("buildSrc"),
            withMainSourceSetJavaKotlinIn("buildSrc/a"),
            withMainSourceSetJavaKotlinIn("buildSrc/b"),
            withMainSourceSetJavaIn("buildSrc/c")
        ] as ProjectSourceRoots[]
    }

    ProjectSourceRoots withMainSourceSetJavaIn(String projectDir) {
        return new ProjectSourceRoots(file(projectDir), ["main"], ["java"])
    }

    ProjectSourceRoots withMainSourceSetJavaKotlinIn(String projectDir) {
        return new ProjectSourceRoots(file(projectDir), ["main"], ["java", "kotlin"])
    }

}
