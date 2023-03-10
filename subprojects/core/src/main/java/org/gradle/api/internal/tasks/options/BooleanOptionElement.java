/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.options;

import org.gradle.api.tasks.options.Option;
import org.gradle.internal.typeconversion.TypeConversionException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A flag, does not take an argument.
 *
 * If a command line option is provided, the {@link org.gradle.execution.commandline.CommandLineTaskConfigurer} automatically creates an opposite option.
 * For example, {@code "--no-foo"} is created for the provided option {@code "--foo"} or {@code "--bar"} for the provided option {@code "--no-bar"}.
 *
 * Options whose names starts with "--no" are 'disable options' and set the option value to false.
 */
public class BooleanOptionElement extends AbstractOptionElement {
    private static final String DISABLE_DESC_PREFIX = "Disables option --";
    private static final String OPPOSITE_DESC_PREFIX = "Opposite option of --";
    private static final String DISABLE_NAME_PREFIX = "no-";
    private final boolean isOpposite;
    private final PropertySetter setter;
    private BooleanOptionElement opposite = null;

    public BooleanOptionElement(String optionName, Option option, PropertySetter setter) {
        super(optionName, option, Void.TYPE, setter.getDeclaringClass());
        this.isOpposite = false;
        this.setter = setter;
    }

    private BooleanOptionElement(String optionName, String optionDescription, PropertySetter setter, BooleanOptionElement opposite) {
        super(optionDescription, optionName, Void.TYPE);
        this.isOpposite = true;
        this.opposite = opposite;
        this.setter = setter;
    }

    public static BooleanOptionElement oppositeOf(BooleanOptionElement optionElement) {
        String optionName = optionElement.getOptionName();
        BooleanOptionElement opposite;
        if (optionElement.isDisableOption()) {
            opposite = new BooleanOptionElement(removeDisablePrefix(optionName), OPPOSITE_DESC_PREFIX + optionName, optionElement.setter, optionElement);
        } else {
            opposite = new BooleanOptionElement(DISABLE_NAME_PREFIX + optionName, DISABLE_DESC_PREFIX + optionName, optionElement.setter, optionElement);
        }
        optionElement.setOpposite(opposite);
        return opposite;
    }

    public boolean isDisableOption() {
        return this.getOptionName().startsWith(DISABLE_NAME_PREFIX);
    }

    public boolean isOpposite() {
        return this.isOpposite;
    }

    public BooleanOptionElement getOpposite() {
        return this.opposite;
    }

    @Override
    public Set<String> getAvailableValues() {
        return Collections.emptySet();
    }

    @Override
    public void apply(Object object, List<String> parameterValues) throws TypeConversionException {
        if (isDisableOption()) {
            setter.setValue(object, Boolean.FALSE);
        } else {
            setter.setValue(object, Boolean.TRUE);
        }
    }
    private void setOpposite(BooleanOptionElement opposite) {
        this.opposite = opposite;
    }

    private static String removeDisablePrefix(String optionName) {
        return optionName.substring(3);
    }
}
