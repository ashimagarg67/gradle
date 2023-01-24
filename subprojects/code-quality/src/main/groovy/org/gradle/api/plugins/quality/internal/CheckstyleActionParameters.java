/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.quality.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;

public interface CheckstyleActionParameters extends WorkParameters {
    RegularFileProperty getConfig();

    ConfigurableFileCollection getSource();

    Property<Integer> getMaxErrors();

    Property<Integer> getMaxWarnings();

    Property<Boolean> getIgnoreFailures();

    DirectoryProperty getConfigDirectory();

    Property<Boolean> getShowViolations();

    Property<Boolean> getIsXmlRequired();

    Property<Boolean> getIsHtmlRequired();

    Property<Boolean> getIsSarifRequired();

    RegularFileProperty getXmlOuputLocation();

    RegularFileProperty getHtmlOuputLocation();

    RegularFileProperty getSarifOutputLocation();

    DirectoryProperty getTemporaryDir();

    MapProperty<String, Object> getConfigProperties();

    Property<String> getStylesheetString();
}
