/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.filter;


import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import static org.apache.commons.lang.StringUtils.splitPreserveAllTokens;
import static org.apache.commons.lang.StringUtils.substringAfterLast;

/**
 * This class has two public APIs:
 *
 * <ul>
 * <li>Judge whether a test class might be included. For example, class 'org.gradle.Test' can't be included by pattern 'org.apache.Test'
 * <li>Judge whether a test method is matched exactly.
 * </ul>
 *
 * In both cases, if the pattern starts with an upper-case letter, it will be used to match simple class name;
 * otherwise, it will be used to match full qualified class name.
 */
public class TestSelectionMatcher {
    private final List<TestPattern> buildScriptIncludePatterns;
    private final List<TestPattern> commandLineIncludePatterns;

    public TestSelectionMatcher(Collection<String> includedTests, Collection<String> includedTestsCommandLine) {
        buildScriptIncludePatterns = preparePatternList(includedTests);
        commandLineIncludePatterns = preparePatternList(includedTestsCommandLine);
    }

    private List<TestPattern> preparePatternList(Collection<String> includedTests) {
        List<TestPattern> includePatterns = new ArrayList<TestPattern>(includedTests.size());
        for (String includedTest : includedTests) {
            includePatterns.add(new TestPattern(includedTest));
        }
        return includePatterns;
    }

    public boolean matchesTest(String className, String methodName) {
        return matchesPattern(buildScriptIncludePatterns, className, methodName)
            && matchesPattern(commandLineIncludePatterns, className, methodName);
    }

    public boolean mayIncludeClass(String fullQualifiedClassName) {
        return mayIncludeClass(buildScriptIncludePatterns, fullQualifiedClassName)
            && mayIncludeClass(commandLineIncludePatterns, fullQualifiedClassName);
    }

    private boolean mayIncludeClass(List<TestPattern> includePatterns, String fullQualifiedName) {
        if (includePatterns.isEmpty()) {
            return true;
        }
        for (TestPattern pattern : includePatterns) {
            if (pattern.mayIncludeClass(fullQualifiedName)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(List<TestPattern> includePatterns, String fullQualifiedName, String methodName) {
        if (includePatterns.isEmpty()) {
            return true;
        }
        for (TestPattern pattern : includePatterns) {
            if (pattern.matchesClassAndMethod(fullQualifiedName, methodName)) {
                return true;
            }
            if (pattern.matchesClass(fullQualifiedName)) {
                return true;
            }
        }
        return false;
    }

    private static class TestPattern {
        private Pattern pattern;
        private String[] segments;
        private LastElementMatcher lastElementMatcher;
        private ClassNameSelector classNameSelector;

        private TestPattern(String pattern) {
            this.pattern = preparePattern(pattern);
            this.classNameSelector = patternStartsWithUpperCase(pattern) ? new SimpleClassNameSelector() : new FullQualifiedClassNameSelector();
            int firstWildcardIndex = pattern.indexOf('*');
            if (firstWildcardIndex == -1) {
                segments = splitPreserveAllTokens(pattern, '.');
                lastElementMatcher = new NoWildcardMatcher();
            } else {
                segments = splitPreserveAllTokens(pattern.substring(0, firstWildcardIndex), '.');
                lastElementMatcher = new WildcardMatcher();
            }
        }

        private static Pattern preparePattern(String input) {
            StringBuilder pattern = new StringBuilder();
            String[] split = StringUtils.splitPreserveAllTokens(input, '*');
            for (String s : split) {
                if (s.equals("")) {
                    pattern.append(".*"); //replace wildcard '*' with '.*'
                } else {
                    if (pattern.length() > 0) {
                        pattern.append(".*"); //replace wildcard '*' with '.*'
                    }
                    pattern.append(Pattern.quote(s)); //quote everything else
                }
            }
            return Pattern.compile(pattern.toString());
        }

        private boolean mayIncludeClass(String fullQualifiedName) {
            if (patternStartsWithWildcard()) {
                return true;
            }
            String[] classNameArray = classNameSelector.determineTargetClassName(fullQualifiedName).split("\\.");
            if (classNameIsShorterThanPattern(classNameArray)) {
                return false;
            }
            for (int i = 0; i < segments.length; ++i) {
                if (lastClassNameElementMatchesPenultimatePatternElement(classNameArray, i)) {
                    return true;
                } else if (lastClassNameElementMatchesLastPatternElement(classNameArray, i)) {
                    return true;
                } else if (!classNameArray[i].equals(segments[i])) {
                    return false;
                }
            }
            return false;
        }

        private boolean matchesClass(String fullQualifiedName) {
            return pattern.matcher(classNameSelector.determineTargetClassName(fullQualifiedName)).matches();
        }

        private boolean matchesClassAndMethod(String fullQualifiedName, String methodName) {
            if (methodName == null) {
                return false;
            }
            return pattern.matcher(classNameSelector.determineTargetClassName(fullQualifiedName) + "." + methodName).matches();
        }

        private boolean lastClassNameElementMatchesPenultimatePatternElement(String[] className, int index) {
            return index == segments.length - 2 && index == className.length - 1 && className[index].equals(segments[index]);
        }

        private boolean lastClassNameElementMatchesLastPatternElement(String[] className, int index) {
            return index == segments.length - 1 && lastElementMatcher.match(className[index], segments[index]);
        }

        private boolean patternStartsWithWildcard() {
            return segments.length == 0;
        }

        private boolean classNameIsShorterThanPattern(String[] classNameArray) {
            return classNameArray.length < segments.length - 1;
        }

        private boolean patternStartsWithUpperCase(String pattern) {
            return pattern.length() > 0 && Character.isUpperCase(pattern.charAt(0));
        }
    }

    private static String getSimpleName(String fullQualifiedName) {
        String simpleName = substringAfterLast(fullQualifiedName, ".");
        if ("".equals(simpleName)) {
            return fullQualifiedName;
        }
        return simpleName;
    }

    private interface LastElementMatcher {
        boolean match(String classElement, String patternElement);
    }

    private static class NoWildcardMatcher implements LastElementMatcher {
        @Override
        public boolean match(String classElement, String patternElement) {
            return classElement.equals(patternElement);
        }
    }

    private static class WildcardMatcher implements LastElementMatcher {
        @Override
        public boolean match(String classElement, String patternElement) {
            return classElement.startsWith(patternElement);
        }
    }

    private interface ClassNameSelector {
        String determineTargetClassName(String fullQualifiedName);
    }

    private static class FullQualifiedClassNameSelector implements ClassNameSelector {
        @Override
        public String determineTargetClassName(String fullQualifiedName) {
            return fullQualifiedName;
        }
    }

    private static class SimpleClassNameSelector implements ClassNameSelector {
        @Override
        public String determineTargetClassName(String fullQualifiedName) {
            return getSimpleName(fullQualifiedName);
        }
    }
}
