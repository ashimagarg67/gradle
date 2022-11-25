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

package org.gradle.api.internal.cache

import org.gradle.api.cache.Cleanup
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.api.cache.TimestampSupplier.olderThanInDays

class DefaultCacheConfigurationsTest extends Specification {
    def cacheConfigurations = TestUtil.objectFactory().newInstance(DefaultCacheConfigurations.class, Stub(GradleUserHomeDirProvider))

    def "cannot modify cache configurations once finalized"() {
        when:
        cacheConfigurations.createdResources.removeUnusedEntriesAfter.set(olderThanInDays(2))
        cacheConfigurations.downloadedResources.removeUnusedEntriesAfter.set(olderThanInDays(2))
        cacheConfigurations.releasedWrappers.removeUnusedEntriesAfter.set(olderThanInDays(2))
        cacheConfigurations.snapshotWrappers.removeUnusedEntriesAfter.set(olderThanInDays(2))
        cacheConfigurations.cleanup.set(Cleanup.DISABLED)

        then:
        noExceptionThrown()

        when:
        cacheConfigurations.finalizeConfigurations()

        and:
        cacheConfigurations.createdResources.removeUnusedEntriesAfter.set(olderThanInDays(1))

        then:
        thrown(IllegalStateException)

        when:
        cacheConfigurations.downloadedResources.removeUnusedEntriesAfter.set(olderThanInDays(1))

        then:
        thrown(IllegalStateException)

        when:
        cacheConfigurations.releasedWrappers.removeUnusedEntriesAfter.set(olderThanInDays(1))

        then:
        thrown(IllegalStateException)

        when:
        cacheConfigurations.snapshotWrappers.removeUnusedEntriesAfter.set(olderThanInDays(1))

        then:
        thrown(IllegalStateException)

        when:
        cacheConfigurations.cleanup.set(Cleanup.DEFAULT)

        then:
        thrown(IllegalStateException)
    }
}
