/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.performance.fixture

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.test.fixtures.file.TestDirectoryProvider

@CompileStatic
class GradleExecuterBackedSession implements GradleSession {

    final GradleInvocationSpec invocation

    private final TestDirectoryProvider testDirectoryProvider

    private final IntegrationTestBuildContext integrationTestBuildContext

    private GradleExecuter executer
    private GradleExecuter executerForStopping

    GradleExecuterBackedSession(GradleInvocationSpec invocation, TestDirectoryProvider testDirectoryProvider, IntegrationTestBuildContext integrationTestBuildContext) {
        this.testDirectoryProvider = testDirectoryProvider
        this.invocation = invocation
        this.integrationTestBuildContext = integrationTestBuildContext
    }

    @Override
    void prepare() {
        cleanup()
    }

    Action<MeasuredOperation> runner(BuildExperimentInvocationInfo invocationInfo, InvocationCustomizer invocationCustomizer) {
        def runner = createExecuter(invocationInfo, invocationCustomizer)
        return { MeasuredOperation measuredOperation ->
            runner.withDurationMeasurement(new DurationMeasurementImpl(measuredOperation))
            try {
                if (invocation.expectFailure) {
                    runner.runWithFailure()
                } else {
                    runner.run()
                }
            } catch (Exception e) {
                measuredOperation.setException(e)
            }
        } as Action<MeasuredOperation>
    }

    @Override
    void cleanup() {
        if (executerForStopping != null) {
            try {
                if (executerForStopping.isUseDaemon()) {
                    executerForStopping.withTasks().withArguments(['--stop'])
                    executerForStopping.run()
                    executerForStopping.stop()
                }
                executerForStopping = null
            } finally {
                executer?.stop()
                executer = null
            }
        }
    }

    private GradleExecuter createExecuter(BuildExperimentInvocationInfo invocationInfo, InvocationCustomizer invocationCustomizer) {
        def invocation = invocationCustomizer ? invocationCustomizer.customize(invocationInfo, this.invocation) : this.invocation

        def createNewExecuter = {
            invocation.gradleDistribution.executer(testDirectoryProvider, integrationTestBuildContext)
        }
        if (executer == null) {
            executer = createNewExecuter()
        } else {
            executer.reset()
        }

        executer.
            requireOwnGradleUserHomeDir().
            withReuseUserHomeServices(true).
            requireGradleDistribution().
            requireIsolatedDaemons().
            withFullDeprecationStackTraceDisabled().
            withStackTraceChecksDisabled().
            withArgument('-u').
            inDirectory(invocation.workingDirectory).
            withTasks(invocation.tasksToRun).
            withOutputCapturing(false)

        executer.withBuildJvmOpts(invocation.jvmOpts)

        invocation.args.each { executer.withArgument(it) }

        if (invocation.useDaemon) {
            executer.requireDaemon()
        }

        if (executer.isUseDaemon()) {
            executer.withCommandLineGradleOpts(PerformanceTestJvmOptions.createDaemonClientJvmOptions())
        } else {
            // optimize for fast startup time when there is no daemon
            // also enable Class Data Sharing (cds) when it's a non-daemon JVM
            executer.withBuildJvmOpts(['-Xverify:none', '-Xshare:auto'])
        }

        // must make a copy of argument for executer to use for stopping since arguments must match when stopping the daemons
        // executer instance's reset method gets called after execution and the arguments aren't preserved there
        if (executerForStopping == null) {
            executerForStopping = createNewExecuter()
            executer.copyTo(executerForStopping)
        }

        executer
    }
}
