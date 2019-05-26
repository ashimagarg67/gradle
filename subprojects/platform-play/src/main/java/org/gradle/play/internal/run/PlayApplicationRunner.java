/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.run;

import com.google.common.collect.Sets;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.deployment.internal.Deployment;
import org.gradle.internal.fingerprint.classpath.ClasspathFingerprinter;
import org.gradle.internal.hash.HashCode;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.worker.WorkerProcess;
import org.gradle.process.internal.worker.WorkerProcessBuilder;
import org.gradle.process.internal.worker.WorkerProcessFactory;

import java.io.File;
import java.util.Set;

public class PlayApplicationRunner {
    private final WorkerProcessFactory workerFactory;
    private final VersionedPlayRunAdapter adapter;
    private final ClasspathFingerprinter fingerprinter;

    public PlayApplicationRunner(WorkerProcessFactory workerFactory, VersionedPlayRunAdapter adapter, ClasspathFingerprinter fingerprinter) {
        this.workerFactory = workerFactory;
        this.adapter = adapter;
        this.fingerprinter = fingerprinter;
    }

    public PlayApplication start(PlayRunSpec spec, Deployment deployment) {
        WorkerProcess process = createWorkerProcess(spec.getProjectPath(), workerFactory, spec, adapter);
        process.start();

        PlayRunWorkerServerProtocol workerServer = process.getConnection().addOutgoing(PlayRunWorkerServerProtocol.class);
        PlayApplication playApplication = new PlayApplication(new PlayClassloaderMonitorDeploymentDecorator(deployment, spec), workerServer, process);
        process.getConnection().addIncoming(PlayRunWorkerClientProtocol.class, playApplication);
        process.getConnection().connect();
        playApplication.waitForRunning();
        return playApplication;
    }

    private class PlayClassloaderMonitorDeploymentDecorator implements Deployment {
        private final Deployment delegate;
        private final FileCollection applicationClasspath;
        private HashCode classpathHash;

        private PlayClassloaderMonitorDeploymentDecorator(Deployment delegate, PlayRunSpec runSpec) {
            this.delegate = delegate;
            this.applicationClasspath = collectApplicationClasspath(runSpec);
        }

        private FileCollection collectApplicationClasspath(PlayRunSpec runSpec) {
            Set<File> changingClasspath = runSpec.getChangingClasspath();
            File applicationJar = runSpec.getApplicationJar();

            Set<File> applicationClasspath = Sets.newLinkedHashSetWithExpectedSize(changingClasspath.size() + 1);
            applicationClasspath.addAll(changingClasspath);
            applicationClasspath.add(applicationJar);

            return ImmutableFileCollection.of(applicationClasspath);
        }

        @Override
        public Status status() {
            final Status delegateStatus = delegate.status();

            if (!delegateStatus.hasChanged()) {
                return delegateStatus;
            }

            if (applicationClasspathChanged()) {
                return delegateStatus;
            } else {
                return new Status() {
                    @Override
                    public Throwable getFailure() {
                        return delegateStatus.getFailure();
                    }

                    @Override
                    public boolean hasChanged() {
                        return false;
                    }
                };
            }
        }

        private boolean applicationClasspathChanged() {
            HashCode oldClasspathHash = classpathHash;
            classpathHash = fingerprinter.fingerprint(applicationClasspath).getHash();
            return !classpathHash.equals(oldClasspathHash);
        }
    }

    private static WorkerProcess createWorkerProcess(File workingDir, WorkerProcessFactory workerFactory, PlayRunSpec spec, VersionedPlayRunAdapter adapter) {
        WorkerProcessBuilder builder = workerFactory.create(new PlayWorkerServer(spec, adapter));
        builder.setBaseName("Gradle Play Worker");
        builder.sharedPackages("org.gradle.play.internal.run");
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setWorkingDir(workingDir);
        javaCommand.setMinHeapSize(spec.getForkOptions().getMemoryInitialSize());
        javaCommand.setMaxHeapSize(spec.getForkOptions().getMemoryMaximumSize());
        javaCommand.setJvmArgs(spec.getForkOptions().getJvmArgs());
        return builder.build();
    }
}
