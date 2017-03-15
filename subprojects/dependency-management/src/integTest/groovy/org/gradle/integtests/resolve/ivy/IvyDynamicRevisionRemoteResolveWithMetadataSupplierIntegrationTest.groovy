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
package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.server.http.IvyHttpModule

class IvyDynamicRevisionRemoteResolveWithMetadataSupplierIntegrationTest extends AbstractHttpDependencyResolutionTest {
    ResolveTestFixture resolve

    def projectA2
    def projectB1
    def projectB2

    def setup() {
        settingsFile << "rootProject.name = 'test' "

        resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()
        withMetadataSupplier()

        ivyHttpRepo.module("group", "projectA", "1.1").publish()
        projectA2 = ivyHttpRepo.module("group", "projectA", "1.2").publish()
        projectB1 = ivyHttpRepo.module("group", "projectB", "1.1").publish()
        projectB2 = ivyHttpRepo.module("group", "projectB", "2.2").publish()
        ivyHttpRepo.module("group", "projectA", "2.0").publish()
    }

    def "can use a custom metadata provider"() {
        given:
        withPerVersionStatusSupplier()

        when:
        expectGetStatusOf(projectB1, 'release')
        expectGetStatusOf(projectB2, 'integration')
        expectGetDynamicRevision(projectA2)
        expectGetDynamicRevision(projectB1)

        then: "custom metadata rule prevented parsing of ivy descriptor"
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"
        outputContains 'Providing metadata for group:projectB:2.2'
        outputContains 'Providing metadata for group:projectB:1.1'
        !output.contains('Providing metadata for group:projectA:1.1')

        and: "creates a new instance of rule each time"
        !output.contains('Metadata rule call count: 2')
    }

    def "re-executing in subsequent build requires no GET request"() {
        given:
        withPerVersionStatusSupplier()

        when:
        def statusOfB1 = expectGetStatusOf(projectB1, 'release')
        def statusOfB2 = expectGetStatusOf(projectB2, 'integration')
        expectGetDynamicRevision(projectA2)
        expectGetDynamicRevision(projectB1)

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"

        and: "re-execute the same build"
        // the two HEAD request below document the existing behavior, not the expected one
        // In particular once we introduce external resource cache policy there shouldn't be any request at all
        server.expectHead('/repo/group/projectB/1.1/status.txt', statusOfB1)
        server.expectHead('/repo/group/projectB/2.2/status.txt', statusOfB2)
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"

    }

    def "publishing new integration version incurs get status file of new integration version only"() {
        given:
        withPerVersionStatusSupplier()
        when:
        def statusOfB1 = expectGetStatusOf(projectB1, 'release')
        def statusOfB2 = expectGetStatusOf(projectB2, 'integration')
        expectGetDynamicRevision(projectA2)
        expectGetDynamicRevision(projectB1)

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"

        when: "publish a new integration version"
        def projectB3 = ivyHttpRepo.module("group", "projectB", "2.3").publish()
        executer.withArgument('-PrefreshDynamicVersions')

        then:
        server.expectHead('/repo/group/projectB/1.1/status.txt', statusOfB1)
        server.expectHead('/repo/group/projectB/2.2/status.txt', statusOfB2)
        expectListVersions(projectA2)
        expectListVersions(projectB3)
        expectGetStatusOf(projectB3, 'integration')
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"
    }

    def "publishing new release version incurs get status file of new release version only"() {
        given:
        withPerVersionStatusSupplier()
        when:
        expectGetStatusOf(projectB1, 'release')
        expectGetStatusOf(projectB2, 'integration')
        expectGetDynamicRevision(projectA2)
        expectGetDynamicRevision(projectB1)

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"

        when: "publish a new integration version"
        def projectB3 = ivyHttpRepo.module("group", "projectB", "2.3").publish()
        executer.withArgument('-PrefreshDynamicVersions')

        then:
        expectListVersions(projectA2)
        expectGetStatusOf(projectB3, 'release')
        expectGetDynamicRevision(projectB3)
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:2.3"
    }

    def "reuses cached result when using --offline"() {
        given:
        withPerVersionStatusSupplier()
        when:
        expectGetStatusOf(projectB1, 'release')
        expectGetStatusOf(projectB2, 'integration')
        expectGetDynamicRevision(projectA2)
        expectGetDynamicRevision(projectB1)

        then: "custom metadata rule prevented parsing of ivy descriptor"
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"

        when:
        executer.withArgument('--offline')

        then: "will used cached status resources"
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"
    }

    def "can recover from --offline mode"() {
        given:
        withPerVersionStatusSupplier()
        when:
        executer.withArgument('--offline')

        then:
        fails 'checkDeps'

        when:
        expectGetStatusOf(projectB1, 'release')
        expectGetStatusOf(projectB2, 'integration')
        expectGetDynamicRevision(projectA2)
        expectGetDynamicRevision(projectB1)

        then: "recovers from previous --offline mode"
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"
    }

    def "can recover from remote failure"() {
        given:
        withPerVersionStatusSupplier()
        when:
        expectListVersions(projectA2)
        projectA2.ivy.expectGet()
        expectGetStatusOf(projectB2, 'integration', true)
        expectListVersions(projectB1)

        then:
        fails 'checkDeps'

        when:
        server.resetExpectations()
        expectGetStatusOf(projectB1, 'release')
        expectGetStatusOf(projectB2, 'integration')
        projectA2.jar.expectGet()
        projectB1.ivy.expectGet()
        projectB1.jar.expectGet()

        then: "recovers from previous failure to get status file"
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"
    }

    def "handles errors in a custom metadata provider"() {
        given:
        buildFile << """
          class MP implements ComponentMetadataSupplier {
          
            void execute(ComponentMetadataSupplierDetails details) {
                throw new NullPointerException("meh: error from custom rule")
            }
          }
"""

        when:
        expectListVersions(projectA2)
        projectA2.ivy.expectGet()
        expectListVersions(projectB1)

        then:
        fails 'checkDeps'

        errorOutput.contains('Could not resolve group:projectB:latest.release')
        failure.assertHasCause('meh: error from custom rule')
    }

    def "custom metadata provider doesn't have to do something"() {
        given:
        buildFile << """
          class MP implements ComponentMetadataSupplier {
          
            void execute(ComponentMetadataSupplierDetails details) {
                // does nothing
            }
          }
"""
        def projectB3 = ivyHttpRepo.module("group", "projectB", "3.3").withStatus('release').publish()

        when:
        expectGetDynamicRevision(projectA2)
        expectGetDynamicRevision(projectB3)

        then:
        checkResolve "group:projectA:1.+": "group:projectA:1.2",
            "group:projectB:latest.release": "group:projectB:3.3"
    }

    def "can use a single remote request to get status of multiple components"() {
        given:
        buildFile << """
          class MP implements ComponentMetadataSupplier {
          
            final RepositoryResourceAccessor repositoryResourceAccessor
            
            @Inject
            MP(RepositoryResourceAccessor accessor) { repositoryResourceAccessor = accessor }
            
            int calls
            Map<String, String> status = [:]
          
            void execute(ComponentMetadataSupplierDetails details) {
                def id = details.id
                println "Providing metadata for \$id"
                repositoryResourceAccessor.withResource("status.txt") {
                    if (status.isEmpty()) {
                        println "Parsing status file call count: \${++calls}"
                        it.withReader { reader ->
                            reader.eachLine { line ->
                                if (line) {
                                   def (module, st) = line.split(';')
                                   status[module] = st
                                }
                            }
                        }
                        println status
                    }
                }
                details.result.status = status[id.toString()]
            }
          }
"""
        when:
        def statusFile = temporaryFolder.createFile("versions.status")
        statusFile << '''group:projectA:1.1;release
group:projectA:1.2;release
group:projectB:1.1;release
group:projectB:2.2;integration
'''
        server.expectGet("/repo/status.txt", statusFile)
        expectGetDynamicRevision(projectA2)
        expectGetDynamicRevision(projectB1)

        then: "custom metadata rule prevented parsing of ivy descriptor"
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"
        outputContains 'Providing metadata for group:projectB:2.2'
        outputContains 'Providing metadata for group:projectB:1.1'
        !output.contains('Providing metadata for group:projectA:1.1')

        and: "remote status file parsed only once"
        outputContains 'Parsing status file call count: 1'
        !output.contains('Parsing status file call count: 2')

        when: "resolving the same dependencies"
        // the following 2 HEAD requests document the current behavior, not necessarily what
        // we want in the end. There are 2 HEAD requests because the file was cached in a previous
        // build, and we're getting the resource twice (once for each module) in this build
        server.expectHead("/repo/status.txt", statusFile)
        server.expectHead("/repo/status.txt", statusFile)

        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"

        then: "should parse the result from cache"
        output.contains('Parsing status file call count: 1')

        when: "force refresh dependencies"
        executer.withArgument("-PrefreshDynamicVersions")
        statusFile.text = '''group:projectA:1.1;release
group:projectA:1.2;release
group:projectB:1.1;release
group:projectB:2.2;release
'''
        // Similarly the HEAD request here is due to revalidating the cached resource
        server.expectHead("/repo/status.txt", statusFile)
        server.expectGet("/repo/status.txt", statusFile)
        expectListVersions(projectA2)
        expectGetDynamicRevision(projectB2)

        then: "shouldn't use the cached resource"
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:2.2"
        outputContains 'Providing metadata for group:projectB:2.2'
        !output.contains('Providing metadata for group:projectB:1.1')
        !output.contains('Providing metadata for group:projectA:1.1')

    }

    def "refresh-dependencies triggers revalidating external resources"() {
        given:
        withPerVersionStatusSupplier()

        when:
        def projectB1Status = expectGetStatusOf(projectB1, 'release')
        def projectB2Status = expectGetStatusOf(projectB2, 'integration')
        expectGetDynamicRevision(projectA2)
        expectGetDynamicRevision(projectB1)

        then: "custom metadata rule prevented parsing of ivy descriptor"
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"

        when:
        executer.withArgument('--refresh-dependencies')

        then:
        expectListVersions(projectA2)
        expectListVersions(projectB1)
        server.expectHead('/repo/group/projectB/2.2/status.txt', projectB2Status)
        server.expectHead('/repo/group/projectB/1.1/status.txt', projectB1Status)
        projectA2.ivy.expectHead()
        projectA2.jar.expectHead()
        projectB1.ivy.expectHead()
        projectB1.jar.expectHead()
        checkResolve "group:projectA:1.+": "group:projectA:1.2", "group:projectB:latest.release": "group:projectB:1.1"
    }

    private void withPerVersionStatusSupplier() {
        buildFile << """
          class MP implements ComponentMetadataSupplier {
          
            final RepositoryResourceAccessor repositoryResourceAccessor
            
            @Inject
            MP(RepositoryResourceAccessor accessor) { repositoryResourceAccessor = accessor }
          
            int count
          
            void execute(ComponentMetadataSupplierDetails details) {
                def id = details.id
                println "Providing metadata for \$id"
                repositoryResourceAccessor.withResource("\${id.group}/\${id.module}/\${id.version}/status.txt") {
                    details.result.status = new String(it.bytes)
                }
                println "Metadata rule call count: \${++count}"
            }
          }
"""
    }

    private void withMetadataSupplier() {
        buildFile << """
          import javax.inject.Inject
     
          repositories {
              ivy {
                  name 'repo'
                  url '${ivyHttpRepo.uri}'
                  metadataSupplier(MP)
              }
          }
          
          if (project.hasProperty('refreshDynamicVersions')) {
                configurations.all {
                    resolutionStrategy.cacheDynamicVersionsFor 0, "seconds"
                }
          }
          
          configurations {
             compile
          }

          dependencies {
              compile group: "group", name: "projectA", version: "1.+"
              compile group: "group", name: "projectB", version: "latest.release"
          }
          """
    }

    def checkResolve(Map edges) {
        assert succeeds('checkDeps')
        resolve.expectGraph {
            root(":", ":test:") {
                edges.each { from, to ->
                    edge(from, to)
                }
            }
        }
        true
    }

    private void expectGetDynamicRevision(IvyHttpModule module) {
        expectListVersions(module)
        module.ivy.expectGet()
        module.jar.expectGet()
    }

    private void expectListVersions(IvyHttpModule module) {
        module.repository.directoryList(module.organisation, module.module).expectGet()
    }

    private File expectGetStatusOf(IvyHttpModule module, String status = 'release', boolean broken=false) {
        def file = temporaryFolder.createFile("cheap-${module.version}.status")
        file << status
        if (!broken) {
            server.expectGet("/repo/${module.organisation}/${module.module}/${module.version}/status.txt", file)
        } else {
            server.expectGetBroken("/repo/${module.organisation}/${module.module}/${module.version}/status.txt")
        }
        file
    }
}
