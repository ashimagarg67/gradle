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
package org.gradle.nativeplatform.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Cast;
import org.gradle.internal.operations.logging.BuildOperationLogger;
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.tasks.SimpleStaleClassCleaner;
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

@Incubating
abstract class AbstractLinkTask extends DefaultTask implements ObjectFilesToBinary {

    private NativeToolChainInternal toolChain;
    private NativePlatformInternal targetPlatform;
    private File outputFile;
    private List<String> linkerArgs;
    private FileCollection source;
    private FileCollection libs;

    @Inject
    public AbstractLinkTask() {
        libs = getProject().files();
        source = getProject().files();
        getInputs().property("outputType", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return NativeToolChainInternal.Identifier.identify(toolChain, targetPlatform);
            }
        });
    }

    /**
     * The tool chain used for linking.
     */
    public NativeToolChain getToolChain() {
        return toolChain;
    }

    public void setToolChain(NativeToolChain toolChain) {
        this.toolChain = (NativeToolChainInternal) toolChain;
    }

    /**
     * The platform that the linked binary will run on.
     */
    public NativePlatform getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(NativePlatform targetPlatform) {
        this.targetPlatform = (NativePlatformInternal) targetPlatform;
    }

    /**
     * Include the destination directory as an output, to pick up auxiliary files produced alongside the main output file
     */
    @OutputDirectory
    public File getDestinationDir() {
        return getOutputFile().getParentFile();
    }

    /**
     * The file where the linked binary will be located.
     */
    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * Additional arguments passed to the linker.
     */
    @Input
    public List<String> getLinkerArgs() {
        return linkerArgs;
    }

    public void setLinkerArgs(List<String> linkerArgs) {
        this.linkerArgs = linkerArgs;
    }

    /**
     * The source object files to be passed to the linker.
     */
    @InputFiles
    public FileCollection getSource() {
        return source;
    }

    public void setSource(FileCollection source) {
        this.source = source;
    }

    /**
     * The library files to be passed to the linker.
     */
    @InputFiles
    public FileCollection getLibs() {
        return libs;
    }

    public void setLibs(FileCollection libs) {
        this.libs = libs;
    }

    /**
     * Adds a set of object files to be linked. The provided source object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    public void source(Object source) {
        ((ConfigurableFileCollection) this.source).from(source);
    }

    /**
     * Adds a set of library files to be linked. The provided libs object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    public void lib(Object libs) {
        ((ConfigurableFileCollection) this.libs).from(libs);
    }

    @Inject
    public BuildOperationLoggerFactory getOperationLoggerFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void link() {
        SimpleStaleClassCleaner cleaner = new SimpleStaleClassCleaner(getOutputs());
        cleaner.setDestinationDir(getDestinationDir());
        cleaner.execute();

        if (getSource().isEmpty()) {
            setDidWork(false);
            return;

        }


        LinkerSpec spec = createLinkerSpec();
        spec.setTargetPlatform(getTargetPlatform());
        spec.setTempDir(getTemporaryDir());
        spec.setOutputFile(getOutputFile());

        spec.objectFiles(getSource());
        spec.libraries(getLibs());
        spec.args(getLinkerArgs());

        BuildOperationLogger operationLogger = getOperationLoggerFactory().newOperationLogger(getName(), getTemporaryDir());
        spec.setOperationLogger(operationLogger);

        Compiler<LinkerSpec> compiler = Cast.uncheckedCast(toolChain.select(targetPlatform).newCompiler(spec.getClass()));
        compiler = BuildOperationLoggingCompilerDecorator.wrap(compiler);
        WorkResult result = compiler.execute(spec);
        setDidWork(result.getDidWork());
    }

    protected abstract LinkerSpec createLinkerSpec();

}
