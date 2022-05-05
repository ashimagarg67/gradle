/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.devel;

import com.google.common.base.Objects;
import org.gradle.api.Incubating;
import org.gradle.api.Named;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

/**
 * Describes a Gradle plugin under development.
 *
 * @see org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
 * @since 2.14
 */
public class PluginDeclaration implements Named, Serializable { // TODO: Shouldn't be serializable, remove the interface in Gradle 8.0.

    private final String name;
    private String id;
    private String implementationClass;
    private String displayName;
    private String description;
    private Collection<String> tags;

    public PluginDeclaration(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getImplementationClass() {
        return implementationClass;
    }

    public void setImplementationClass(String implementationClass) {
        this.implementationClass = implementationClass;
    }

    /**
     * Returns the display name for this plugin declaration.
     *
     * <p>The display name is used when publishing this plugin to repositories
     * that support human-readable artifact names.
     *
     * @since 4.10
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the display name for this plugin declaration.
     *
     * <p>The display name is used when publishing this plugin to repositories
     * that support human-readable artifact names.
     *
     * @since 4.10
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the description for this plugin declaration.
     *
     * <p>The description is used when publishing this plugin to repositories
     * that support providing descriptions for artifacts.
     *
     * @since 4.10
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description for this plugin declaration.
     *
     * <p>The description is used when publishing this plugin to repositories
     * that support providing descriptions for artifacts.
     *
     * @since 4.10
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the tags for this plugin declaration.
     *
     * <p>Tags are used when publishing this plugin to repositories that support tagging plugins,
     * for example the <a href="http://plugins.gradle.org">Gradle Plugin Portal</a>.
     *
     * @since 7.5
     */
    @Incubating
    public Collection<String> getTags() {
        if (tags == null) {
            return Collections.emptyList();
        }
        return tags;
    }

    /**
     * Set the tags for this plugin declaration. Tags describe the categories this plugin covers.
     *
     * <p>Tags are used when publishing this plugin to repositories that support tagging plugins,
     * for example the <a href="http://plugins.gradle.org">Gradle Plugin Portal</a>.
     *
     * @since 7.5
     */
    @Incubating
    public void setTags(Collection<String> tags) {
        this.tags = tags;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PluginDeclaration) {
            PluginDeclaration other = (PluginDeclaration) obj;
            return Objects.equal(name, other.name)
                    && Objects.equal(id, other.id)
                    && Objects.equal(implementationClass, other.implementationClass)
                    && Objects.equal(tags, other.tags);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, id, implementationClass, tags);
    }
}
