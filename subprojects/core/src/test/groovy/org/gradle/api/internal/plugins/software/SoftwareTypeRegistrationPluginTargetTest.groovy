/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.plugins.software

import com.google.common.reflect.TypeToken
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.plugins.PluginTarget
import org.gradle.api.internal.tasks.properties.InspectionScheme
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.properties.annotations.PropertyMetadata
import org.gradle.internal.properties.annotations.TypeMetadata
import org.gradle.internal.properties.annotations.TypeMetadataStore
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata
import org.gradle.plugin.software.internal.SoftwareTypeRegistry
import spock.lang.Specification

class SoftwareTypeRegistrationPluginTargetTest extends Specification {
    def settings = Mock(SettingsInternal)
    def delegate = Mock(PluginTarget)
    def softwareTypeRegistry = Mock(SoftwareTypeRegistry)
    def inspectionScheme = Mock(InspectionScheme)
    def pluginTarget = new SoftwareTypeRegistrationPluginTarget(settings, delegate, softwareTypeRegistry, inspectionScheme)
    def plugin = Mock(Plugin)
    def metadataStore = Mock(TypeMetadataStore)
    def pluginTypeMetadata = Mock(TypeMetadata)
    def typeAnnotationMetadata = Mock(TypeAnnotationMetadata)
    def registersSoftwareTypes = Mock(RegistersSoftwareTypes)
    def softwareTypePluginMetadata = Mock(TypeMetadata)
    def propertyMetadata = Mock(PropertyMetadata)
    def fooTypeMetadata = Mock(TypeMetadata)
    def extensions = Mock(ExtensionContainerInternal)
    def softwareType = Mock(SoftwareType)

    def "adds software types and registers conventions for plugin that registers software types"() {
        when:
        pluginTarget.applyImperative(null, plugin)

        then: // setup property metadata
        3 * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(plugin.class) >> pluginTypeMetadata
        1 * pluginTypeMetadata.getTypeAnnotationMetadata() >> typeAnnotationMetadata
        1 * typeAnnotationMetadata.getAnnotation(RegistersSoftwareTypes.class) >> Optional.of(registersSoftwareTypes)
        1 * registersSoftwareTypes.value() >> [SoftwareTypePlugin.class]
        2 * metadataStore.getTypeMetadata(SoftwareTypePlugin.class) >> softwareTypePluginMetadata
        2 * softwareTypePluginMetadata.getPropertiesMetadata() >> [propertyMetadata]

        and: // returns property metadata with an annotation
        2 * propertyMetadata.getAnnotation(SoftwareType.class) >> Optional.of(softwareType)
        1 * softwareTypeRegistry.register(SoftwareTypePlugin.class)
        1 * propertyMetadata.getPropertyType() >> SoftwareType.class
        1 * propertyMetadata.getDeclaredType() >> TypeToken.of(Foo.class)
        1 * metadataStore.getTypeMetadata(Foo.class) >> fooTypeMetadata
        1 * fooTypeMetadata.getPropertiesMetadata() >> []
        1 * settings.getExtensions() >> extensions
        1 * softwareType.name() >> "foo"
        1 * softwareType.modelPublicType() >> Foo.class
        1 * extensions.create("foo", Foo.class)

        and:
        1 * delegate.applyImperative(null, plugin)
    }

    def "throws exception when plugins are registered that do not expose software types"() {
        when:
        pluginTarget.applyImperative(null, plugin)

        then: // setup property metadata
        2 * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(plugin.class) >> pluginTypeMetadata
        1 * pluginTypeMetadata.getTypeAnnotationMetadata() >> typeAnnotationMetadata
        1 * pluginTypeMetadata.getType() >> plugin.class
        1 * typeAnnotationMetadata.getAnnotation(RegistersSoftwareTypes.class) >> Optional.of(registersSoftwareTypes)
        1 * registersSoftwareTypes.value() >> [SoftwareTypePlugin.class]
        1 * metadataStore.getTypeMetadata(SoftwareTypePlugin.class) >> softwareTypePluginMetadata
        1 * softwareTypePluginMetadata.getPropertiesMetadata() >> [propertyMetadata]

        and: // returns metadata with no annotation present
        1 * propertyMetadata.getAnnotation(SoftwareType.class) >> Optional.empty()

        and:
        def e = thrown(DefaultMultiCauseException)
        e.hasCause(InvalidUserDataException)
        e.causes[0].message.contains("This class was registered as a software type plugin, but it does not expose any software types.")
    }

    def "calls delegate for plugins that do not register software types"() {
        when:
        pluginTarget.applyImperative(null, plugin)

        then:
        1 * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(plugin.class) >> pluginTypeMetadata
        1 * pluginTypeMetadata.getTypeAnnotationMetadata() >> typeAnnotationMetadata
        1 * typeAnnotationMetadata.getAnnotation(RegistersSoftwareTypes.class) >> Optional.empty()

        and:
        1 * delegate.applyImperative(null, plugin)
    }

    def "passes rule targets to delegate only"() {
        when:
        pluginTarget.applyRules(null, Rule.class)

        then:
        1 * delegate.applyRules(null, Rule.class)
        0 * _
    }

    abstract static class SoftwareTypePlugin implements Plugin<Project> { }
    abstract static class Foo {}
    private static class Rule {}
}
