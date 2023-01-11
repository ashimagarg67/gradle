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

package org.gradle.integtests.resource.gcs.maven

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.resource.gcs.AbstractGcsDependencyResolutionTest
import org.gradle.integtests.resource.gcs.fixtures.MavenGcsModule

class MavenGcsRepoErrorsIntegrationTest extends AbstractGcsDependencyResolutionTest {

    String artifactVersion = "1.85"
    MavenGcsModule module

    @Override
    String getRepositoryPath() {
        return '/maven/release/'
    }

    def setup() {
        module = mavenGcsRepo.module("org.gradle", "test", artifactVersion)
        buildFile << """
configurations { compile }

dependencies{
    compile 'org.gradle:test:$artifactVersion'
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
    }

    @ToBeFixedForConfigurationCache(skip = ToBeFixedForConfigurationCache.Skip.FAILS_TO_CLEANUP)
    def "should fail with a GCS authentication error"() {
        setup:
        buildFile << mavenGcsRepoDsl()
        when:
        module.pom.expectDownloadAuthenticationError()
        then:
        fails 'retrieve'
        and:
        failure.assertHasDescriptionStartingWith("Execution failed for task ':retrieve'.")
            .assertHasCause("Could not resolve all files for configuration ':compile'.")
            .assertHasCause('Could not resolve org.gradle:test:1.85.')
            .assertHasCause("Could not get resource '${module.pom.uri}'.")
            .assertHasCause("401 Unauthorized")
    }

    @ToBeFixedForConfigurationCache(skip = ToBeFixedForConfigurationCache.Skip.FAILS_TO_CLEANUP)
    def "fails when providing PasswordCredentials with decent error"() {
        setup:
        buildFile << """
repositories {
    maven {
        url "${mavenGcsRepo.uri}"
        credentials {
            username "someUserName"
            password "someSecret"
        }
    }
}
"""
        when:
        fails 'retrieve'
        then:
        //TODO would be good to have a reference of the wrong configured repository in the error message
        failure.assertHasDescriptionStartingWith("Execution failed for task ':retrieve'.")
            .assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
            .assertHasCause("Authentication scheme 'all'(Authentication) is not supported by protocol 'gcs'")
    }

    @ToBeFixedForConfigurationCache(skip = ToBeFixedForConfigurationCache.Skip.FAILS_TO_CLEANUP)
    def "should include resource uri when file not found"() {
        setup:
        buildFile << mavenGcsRepoDsl()
        when:
        module.pom.expectDownloadMissing()
        then:
        fails 'retrieve'

        and:
        failure.assertHasDescriptionStartingWith("Execution failed for task ':retrieve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause(
            """Could not find org.gradle:test:1.85.
Searched in the following locations:
  - ${module.pom.uri}
If the artifact you are trying to retrieve can be found in the repository but without metadata in 'Maven POM' format, you need to adjust the 'metadataSources { ... }' of the repository declaration.
Required by:
""")
    }

    @ToBeFixedForConfigurationCache(skip = ToBeFixedForConfigurationCache.Skip.FAILS_TO_CLEANUP)
    def "cannot add invalid authentication types for gcs repo"() {
        given:
        module.publish()

        and:
        buildFile << """
            repositories {
                maven {
                    url "${mavenGcsRepo.uri}"
                    authentication {
                        auth(BasicAuthentication)
                    }
                }
            }
        """

        expect:
        fails 'retrieve'
        and:
        failure.assertHasDescriptionStartingWith("Execution failed for task ':retrieve'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("Authentication scheme 'auth'(BasicAuthentication) is not supported by protocol 'gcs'")
    }
}
