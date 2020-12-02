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

package org.gradle.api.tasks.util.internal;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableList;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;
import org.gradle.cache.internal.HeapProportionalCacheSizer;

import java.util.Collection;

public class CachingPatternSpecFactory extends PatternSpecFactory {
    private static final int RESULTS_CACHE_MAX_SIZE = 100000;
    private static final int INSTANCES_MAX_SIZE = 30000;
    private final HeapProportionalCacheSizer cacheSizer;
    private final Cache<SpecKey, Spec<FileTreeElement>> specInstanceCache;

    public CachingPatternSpecFactory() {
        cacheSizer = new HeapProportionalCacheSizer();
        specInstanceCache = Caffeine.newBuilder().executor(Runnable::run).maximumSize(cacheSizer.scaleCacheSize(INSTANCES_MAX_SIZE)).build();
    }

    @Override
    protected Spec<FileTreeElement> createSpec(final Collection<String> patterns, final boolean include, final boolean caseSensitive) {
        final SpecKey key = new SpecKey(ImmutableList.copyOf(patterns), include, caseSensitive);
        return specInstanceCache.get(key, k -> {
            Spec<FileTreeElement> spec = CachingPatternSpecFactory.super.createSpec(patterns, include, caseSensitive);
            return new CachingSpec(spec);
        });
    }

    private class CachingSpec implements Spec<FileTreeElement> {
        private final Spec<FileTreeElement> spec;
        private final Cache<RelativePath, Boolean> resultCache = Caffeine.newBuilder().executor(Runnable::run).maximumSize(cacheSizer.scaleCacheSize(RESULTS_CACHE_MAX_SIZE)).build();

        CachingSpec(Spec<FileTreeElement> spec) {
            this.spec = spec;
        }

        @Override
        public boolean isSatisfiedBy(final FileTreeElement element) {
            return resultCache.get(element.getRelativePath(), key -> spec.isSatisfiedBy(element));
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("spec", spec)
                .toString();
        }
    }

    private static class SpecKey {
        private final ImmutableList<String> patterns;
        private final boolean include;
        private final boolean caseSensitive;
        private final int hashCode;

        private SpecKey(ImmutableList<String> patterns, boolean include, boolean caseSensitive) {
            this.patterns = patterns;
            this.include = include;
            this.caseSensitive = caseSensitive;
            this.hashCode = Objects.hashCode(patterns, include, caseSensitive);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SpecKey that = (SpecKey) o;

            return Objects.equal(this.patterns, that.patterns)
                && Objects.equal(this.include, that.include)
                && Objects.equal(this.caseSensitive, that.caseSensitive);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("patterns", patterns)
                .add("include", include)
                .add("caseSensitive", caseSensitive)
                .toString();
        }
    }
}
