/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugins.ide.internal.resolver;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectInternal.DetachedResolver;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.gradle.util.internal.GroovyDependencyUtil.groovyGroupName;

public class DefaultGradleApiSourcesResolver implements GradleApiSourcesResolver {

    private static final String GRADLE_LIBS_REPO_URL = "https://repo.gradle.org/gradle/list/libs-releases";
    private static final String GRADLE_LIBS_REPO_OVERRIDE_VAR = "GRADLE_LIBS_REPO_OVERRIDE";
    private static final String GRADLE_LIBS_REPO_OVERRIDE_PROPERTY = "org.gradle.internal.gradle.libs.repo.override";
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("(groovy(-.+?)?)-(\\d.+?)\\.jar");

    private final DependencyHandler dependencyHandler;

    public DefaultGradleApiSourcesResolver(DependencyHandler dependencyHandler) {
        this.dependencyHandler = dependencyHandler;
    }

    public static DefaultGradleApiSourcesResolver newSourcesResolverWithDefaultRepoConfig(ProjectInternal projectInternal) {
        return newSourcesResolver(
            projectInternal,
            a -> {
                a.setName("Gradle Libs");
                a.setUrl(gradleLibsRepoUrl(
                    GRADLE_LIBS_REPO_URL,
                    () -> Objects.toString(projectInternal.findProperty(GRADLE_LIBS_REPO_OVERRIDE_PROPERTY), null),
                    () -> System.getenv(GRADLE_LIBS_REPO_OVERRIDE_VAR)
                ));
            }
        );
    }

    @SafeVarargs
    @VisibleForTesting
    static String gradleLibsRepoUrl(String defaultRepoUrl, Supplier<String>... repoUrlOverrideSources) {
        for (Supplier<String> repoUrlOverrideSource : repoUrlOverrideSources) {
            String repoUrlOverride = repoUrlOverrideSource.get();
            if (repoUrlOverride != null) {
                return repoUrlOverride;
            }
        }
        return defaultRepoUrl;
    }

    public static DefaultGradleApiSourcesResolver newSourcesResolver(ProjectInternal projectInternal, Action<? super MavenArtifactRepository> repoConfigAction) {
        DetachedResolver resolver = projectInternal.newDetachedResolver();
        resolver.getRepositories().maven(repoConfigAction);
        return new DefaultGradleApiSourcesResolver(resolver.getDependencies());
    }

    @Override
    public File resolveLocalGroovySources(String jarName) {
        Matcher matcher = FILE_NAME_PATTERN.matcher(jarName);
        if (!matcher.matches()) {
            return null;
        }
        VersionNumber version = VersionNumber.parse(matcher.group(3));
        final String artifactName = matcher.group(1);
        return downloadLocalGroovySources(artifactName, version);
    }

    private File downloadLocalGroovySources(String artifact, VersionNumber version) {
        ArtifactResolutionResult result = dependencyHandler.createArtifactResolutionQuery()
            .forModule(groovyGroupName(version), artifact, version.toString())
            .withArtifacts(JvmLibrary.class, Collections.singletonList(SourcesArtifact.class))
            .execute();

        for (ComponentArtifactsResult artifactsResult : result.getResolvedComponents()) {
            for (ArtifactResult artifactResult : artifactsResult.getArtifacts(SourcesArtifact.class)) {
                if (artifactResult instanceof ResolvedArtifactResult) {
                    return ((ResolvedArtifactResult) artifactResult).getFile();
                }
            }
        }
        return null;
    }
}
