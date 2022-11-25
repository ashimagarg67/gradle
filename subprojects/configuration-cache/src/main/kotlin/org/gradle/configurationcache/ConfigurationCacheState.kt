/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal.BUILD_SRC
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.provider.Provider
import org.gradle.api.services.internal.BuildServiceProvider
import org.gradle.api.services.internal.BuildServiceRegistryInternal
import org.gradle.api.services.internal.RegisteredBuildServiceProvider
import org.gradle.caching.configuration.BuildCache
import org.gradle.caching.configuration.internal.BuildCacheServiceRegistration
import org.gradle.configuration.BuildOperationFiringProjectsPreparer
import org.gradle.configuration.project.LifecycleProjectEvaluator
import org.gradle.configurationcache.CachedProjectState.Companion.computeCachedState
import org.gradle.configurationcache.CachedProjectState.Companion.configureProjectFromCachedState
import org.gradle.configurationcache.extensions.serviceOf
import org.gradle.configurationcache.extensions.unsafeLazy
import org.gradle.configurationcache.problems.DocumentationSection.NotYetImplementedSourceDependencies
import org.gradle.configurationcache.serialization.DefaultReadContext
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.serialization.codecs.Codecs
import org.gradle.configurationcache.serialization.logNotImplemented
import org.gradle.configurationcache.serialization.readCollection
import org.gradle.configurationcache.serialization.readFile
import org.gradle.configurationcache.serialization.readList
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.readStrings
import org.gradle.configurationcache.serialization.runReadOperation
import org.gradle.configurationcache.serialization.withDebugFrame
import org.gradle.configurationcache.serialization.withGradleIsolate
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.configurationcache.serialization.writeFile
import org.gradle.configurationcache.serialization.writeStrings
import org.gradle.configurationcache.services.EnvironmentChangeTracker
import org.gradle.execution.plan.Node
import org.gradle.initialization.BuildIdentifiedProgressDetails
import org.gradle.initialization.BuildOperationFiringSettingsPreparer
import org.gradle.initialization.BuildOperationSettingsProcessor
import org.gradle.initialization.BuildStructureOperationProject
import org.gradle.initialization.NotifyingBuildLoader
import org.gradle.initialization.ProjectsIdentifiedProgressDetails
import org.gradle.initialization.RootBuildCacheControllerSettingsProcessor
import org.gradle.initialization.SettingsLocation
import org.gradle.internal.Actions
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.CompositeBuildParticipantBuildState
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.build.PublicBuildPath
import org.gradle.internal.build.RootBuildState
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal
import org.gradle.internal.buildoption.FeatureFlags
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.composite.IncludedBuildInternal
import org.gradle.internal.enterprise.core.GradleEnterprisePluginAdapter
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.internal.execution.BuildOutputCleanupRegistry
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.vcs.internal.VcsMappingsStore
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


internal
enum class StateType {
    Work, Model, Entry, BuildFingerprint, ProjectFingerprint, IntermediateModels, ProjectMetadata
}


internal
interface ConfigurationCacheStateFile {
    val exists: Boolean
    fun outputStream(): OutputStream
    fun inputStream(): InputStream
    fun delete()

    // Replace the contents of this state file, by moving the given file to the location of this state file
    fun moveFrom(file: File)
    fun stateFileForIncludedBuild(build: BuildDefinition): ConfigurationCacheStateFile
}


internal
class ConfigurationCacheState(
    private val codecs: Codecs,
    private val stateFile: ConfigurationCacheStateFile
) {
    /**
     * Writes the state for the whole build starting from the given root [build] and returns the set
     * of stored included build directories.
     */
    suspend fun DefaultWriteContext.writeRootBuildState(build: VintageGradleBuild) =
        writeRootBuild(build).also {
            writeInt(0x1ecac8e)
        }

    suspend fun DefaultReadContext.readRootBuildState(graph: BuildTreeWorkGraph, loadAfterStoreModeEnabled: Boolean, loadAfterStore: Boolean, createBuild: (File?, String) -> ConfigurationCacheBuild): BuildTreeWorkGraph.FinalizedGraph {
        val buildState = readRootBuild(createBuild, !loadAfterStoreModeEnabled)
        require(readInt() == 0x1ecac8e) {
            "corrupt state file"
        }
        if (loadAfterStoreModeEnabled) {
            if (!loadAfterStore) {
                identifyBuild(buildState)
            }
        } else {
            configureBuild(buildState)
        }

        return calculateRootTaskGraph(buildState, graph)
    }

    private
    fun configureBuild(state: CachedBuildState) {
        val gradle = state.build.gradle
        val buildOperationExecutor = gradle.serviceOf<BuildOperationExecutor>()
        fireConfigureBuild(buildOperationExecutor, gradle) {
            fireLoadProjects(buildOperationExecutor, gradle.serviceOf(), gradle)
            state.children.forEach(::configureBuild)
            fireConfigureProject(buildOperationExecutor, gradle)
        }
    }

    private
    fun identifyBuild(state: CachedBuildState) {
        val gradle = state.build.gradle
        val emitter = gradle.serviceOf<BuildOperationProgressEventEmitter>()
        emitter.emitNowForCurrent(BuildIdentifiedProgressDetails { gradle.identityPath.toString() })
        emitter.emitNowForCurrent(object : ProjectsIdentifiedProgressDetails {
            override fun getBuildPath() = gradle.identityPath.toString()
            override fun getRootProject() = BuildStructureOperationProject.from(gradle)
        })
        state.children.forEach(::identifyBuild)
    }

    private
    fun calculateRootTaskGraph(state: CachedBuildState, graph: BuildTreeWorkGraph): BuildTreeWorkGraph.FinalizedGraph {
        return graph.scheduleWork { builder ->
            builder.withWorkGraph(state.build.state) {
                it.setScheduledNodes(state.workGraph)
            }
            for (child in state.children) {
                addNodesForChildBuilds(child, builder)
            }
        }
    }

    private
    fun addNodesForChildBuilds(state: CachedBuildState, builder: BuildTreeWorkGraph.Builder) {
        builder.withWorkGraph(state.build.state) {
            it.setScheduledNodes(state.workGraph)
        }
        for (child in state.children) {
            addNodesForChildBuilds(child, builder)
        }
    }

    private
    suspend fun DefaultWriteContext.writeRootBuild(build: VintageGradleBuild) {
        require(build.gradle.owner is RootBuildState)
        val gradle = build.gradle
        withDebugFrame({ "Gradle" }) {
            write(gradle.settings.settingsScript.resource.file)
            writeString(gradle.rootProject.name)
            writeBuildTreeState(gradle)
        }
        val buildEventListeners = buildEventListenersOf(gradle)
        writeBuildState(
            build,
            StoredBuildTreeState(
                storedBuilds = storedBuilds(),
                requiredBuildServicesPerBuild = buildEventListeners
                    .groupBy { it.buildIdentifier }
            )
        )
        writeRootEventListenerSubscriptions(gradle, buildEventListeners)
    }

    private
    suspend fun DefaultReadContext.readRootBuild(
        createBuild: (File?, String) -> ConfigurationCacheBuild,
        synthesizeBuildOps: Boolean
    ): CachedBuildState {
        val settingsFile = read() as File?
        val rootProjectName = readString()
        val build = createBuild(settingsFile, rootProjectName)
        val gradle = build.gradle
        readBuildTreeState(gradle)
        val rootBuildState = readBuildState(build, synthesizeBuildOps)
        readRootEventListenerSubscriptions(gradle)
        return rootBuildState
    }

    internal
    suspend fun DefaultWriteContext.writeBuildState(build: VintageGradleBuild, buildTreeState: StoredBuildTreeState) {
        val gradle = build.gradle
        withDebugFrame({ "Gradle" }) {
            writeGradleState(gradle, buildTreeState)
        }
        withDebugFrame({ "Work Graph" }) {
            val scheduledNodes = build.scheduledWork
            val relevantProjects = getRelevantProjectsFor(scheduledNodes, gradle.serviceOf())
            writeRelevantProjectRegistrations(relevantProjects)
            writeProjectStates(gradle, relevantProjects)
            writeRequiredBuildServicesOf(gradle, buildTreeState)
            writeWorkGraphOf(gradle, scheduledNodes)
        }
        withDebugFrame({ "cleanup registrations" }) {
            writeBuildOutputCleanupRegistrations(gradle)
        }
    }

    internal
    suspend fun DefaultReadContext.readBuildState(build: ConfigurationCacheBuild, synthesizeBuildOps: Boolean): CachedBuildState {

        val gradle = build.gradle

        lateinit var children: List<CachedBuildState>

        val readOperation: suspend DefaultReadContext.() -> Unit = {
            children = readGradleState(build, synthesizeBuildOps)
        }

        if (synthesizeBuildOps) {
            withLoadBuildOperation(gradle) {
                fireEvaluateSettings(gradle)
                runReadOperation(readOperation)
            }
        } else {
            runReadOperation(readOperation)
        }

        readRelevantProjectRegistrations(build)

        build.registerProjects()

        initProjectProvider(build::getProject)

        readProjectStates(gradle)
        readRequiredBuildServicesOf(gradle)

        val workGraph = readWorkGraph(gradle)
        readBuildOutputCleanupRegistrations(gradle)
        return CachedBuildState(build, workGraph, children)
    }

    data class CachedBuildState(
        val build: ConfigurationCacheBuild,
        val workGraph: List<Node>,
        val children: List<CachedBuildState>
    )

    private
    fun withLoadBuildOperation(gradle: GradleInternal, preparer: () -> Unit) {
        contract {
            callsInPlace(preparer, InvocationKind.EXACTLY_ONCE)
        }
        fireLoadBuild(preparer, gradle)
    }

    private
    suspend fun DefaultWriteContext.writeWorkGraphOf(gradle: GradleInternal, scheduledNodes: List<Node>) {
        workNodeCodec(gradle).run {
            writeWork(scheduledNodes)
        }
    }

    private
    suspend fun DefaultReadContext.readWorkGraph(gradle: GradleInternal) =
        workNodeCodec(gradle).run {
            readWork()
        }

    private
    fun workNodeCodec(gradle: GradleInternal) =
        codecs.workNodeCodecFor(gradle)

    private
    suspend fun DefaultWriteContext.writeRequiredBuildServicesOf(gradle: GradleInternal, buildTreeState: StoredBuildTreeState) {
        withGradleIsolate(gradle, userTypesCodec) {
            write(buildTreeState.requiredBuildServicesPerBuild[buildIdentifierOf(gradle)])
        }
    }

    private
    suspend fun DefaultReadContext.readRequiredBuildServicesOf(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {
            read()
        }
    }

    private
    suspend fun DefaultWriteContext.writeProjectStates(gradle: GradleInternal, relevantProjects: List<ProjectState>) {
        withGradleIsolate(gradle, userTypesCodec) {
            // Do not serialize trivial states to speed up deserialization.
            val nonTrivialProjectStates = relevantProjects.asSequence()
                .map { project -> project.mutableModel.computeCachedState() }
                .filterNotNull()
                .toList()

            writeCollection(nonTrivialProjectStates)
        }
    }

    private
    suspend fun DefaultReadContext.readProjectStates(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {
            readCollection {
                configureProjectFromCachedState(read() as CachedProjectState)
            }
        }
    }

    private
    suspend fun DefaultWriteContext.writeBuildTreeState(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {
            withDebugFrame({ "environment state" }) {
                writeCachedEnvironmentState(gradle)
                writePreviewFlags(gradle)
            }
            withDebugFrame({ "gradle enterprise" }) {
                writeGradleEnterprisePluginManager(gradle)
            }
            withDebugFrame({ "build cache" }) {
                writeBuildCacheConfiguration(gradle)
            }
        }
    }

    private
    suspend fun DefaultReadContext.readBuildTreeState(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {
            readCachedEnvironmentState(gradle)
            readPreviewFlags(gradle)
            // It is important that the Gradle Enterprise plugin be read before
            // build cache configuration, as it may contribute build cache configuration.
            readGradleEnterprisePluginManager(gradle)
            readBuildCacheConfiguration(gradle)
        }
    }

    private
    suspend fun DefaultWriteContext.writeRootEventListenerSubscriptions(gradle: GradleInternal, listeners: List<Provider<*>>) {
        withGradleIsolate(gradle, userTypesCodec) {
            withDebugFrame({ "listener subscriptions" }) {
                writeBuildEventListenerSubscriptions(listeners)
            }
        }
    }

    private
    suspend fun DefaultReadContext.readRootEventListenerSubscriptions(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {
            readBuildEventListenerSubscriptions(gradle)
        }
    }

    private
    suspend fun DefaultWriteContext.writeGradleState(gradle: GradleInternal, buildTreeState: StoredBuildTreeState) {
        withGradleIsolate(gradle, userTypesCodec) {
            // per build
            writeStartParameterOf(gradle)
            withDebugFrame({ "included builds" }) {
                writeChildBuilds(gradle, buildTreeState)
            }
        }
    }

    private
    suspend fun DefaultReadContext.readGradleState(
        build: ConfigurationCacheBuild,
        synthesizeBuildOps: Boolean
    ): List<CachedBuildState> {
        val gradle = build.gradle
        withGradleIsolate(gradle, userTypesCodec) {
            // per build
            readStartParameterOf(gradle)
            return readChildBuildsOf(build, synthesizeBuildOps)
        }
    }

    private
    fun DefaultWriteContext.writeStartParameterOf(gradle: GradleInternal) {
        val startParameterTaskNames = gradle.startParameter.taskNames
        writeStrings(startParameterTaskNames)
    }

    private
    fun DefaultReadContext.readStartParameterOf(gradle: GradleInternal) {
        // Restore startParameter.taskNames to enable `gradle.startParameter.setTaskNames(...)` idiom in included build scripts
        // See org/gradle/caching/configuration/internal/BuildCacheCompositeConfigurationIntegrationTest.groovy:134
        val startParameterTaskNames = readStrings()
        gradle.startParameter.setTaskNames(startParameterTaskNames)
    }

    private
    suspend fun DefaultWriteContext.writeChildBuilds(gradle: GradleInternal, buildTreeState: StoredBuildTreeState) {
        writeCollection(gradle.includedBuilds()) {
            writeIncludedBuildState(it, buildTreeState)
        }
        if (gradle.serviceOf<VcsMappingsStore>().asResolver().hasRules()) {
            logNotImplemented(
                feature = "source dependencies",
                documentationSection = NotYetImplementedSourceDependencies
            )
            writeBoolean(true)
        } else {
            writeBoolean(false)
        }
    }

    private
    suspend fun DefaultReadContext.readChildBuildsOf(
        parentBuild: ConfigurationCacheBuild,
        synthesizeBuildOps: Boolean
    ): List<CachedBuildState> {
        val includedBuilds = readList {
            readIncludedBuildState(parentBuild, synthesizeBuildOps)
        }
        if (readBoolean()) {
            logNotImplemented(
                feature = "source dependencies",
                documentationSection = NotYetImplementedSourceDependencies
            )
        }
        parentBuild.gradle.setIncludedBuilds(includedBuilds.map { it.first.model })
        return includedBuilds.mapNotNull { it.second }
    }

    private
    suspend fun DefaultWriteContext.writeIncludedBuildState(
        reference: IncludedBuildInternal,
        buildTreeState: StoredBuildTreeState
    ) {
        when (val target = reference.target) {
            is IncludedBuildState -> {
                writeBoolean(true)
                val includedGradle = target.mutableModel
                val buildDefinition = includedGradle.serviceOf<BuildDefinition>()
                writeBuildDefinition(buildDefinition)
                when {
                    buildTreeState.storedBuilds.store(buildDefinition) -> {
                        writeBoolean(true)
                        target.projects.withMutableStateOfAllProjects {
                            includedGradle.serviceOf<ConfigurationCacheIO>().writeIncludedBuildStateTo(
                                stateFileFor(buildDefinition),
                                buildTreeState
                            )
                        }
                    }

                    else -> {
                        writeBoolean(false)
                    }
                }
            }

            is RootBuildState -> {
                writeBoolean(false)
            }

            else -> {
                TODO("Unsupported included build type '${target.javaClass}'")
            }
        }
    }

    private
    suspend fun DefaultReadContext.readIncludedBuildState(
        parentBuild: ConfigurationCacheBuild,
        synthesizeBuildOps: Boolean
    ): Pair<CompositeBuildParticipantBuildState, CachedBuildState?> =
        when {
            readBoolean() -> {
                val buildDefinition = readIncludedBuildDefinition(parentBuild)
                val includedBuild = parentBuild.addIncludedBuild(buildDefinition)
                val stored = readBoolean()
                val cachedBuildState =
                    if (stored) {
                        val confCacheBuild = includedBuild.withState { includedGradle ->
                            includedGradle.serviceOf<ConfigurationCacheHost>().createBuild(null, includedBuild.name)
                        }
                        confCacheBuild.gradle.serviceOf<ConfigurationCacheIO>().readIncludedBuildStateFrom(
                            stateFileFor(buildDefinition),
                            confCacheBuild,
                            synthesizeBuildOps
                        )
                    } else null
                includedBuild to cachedBuildState
            }

            else -> {
                rootBuildStateOf(parentBuild) to null
            }
        }

    private
    fun rootBuildStateOf(parentBuild: ConfigurationCacheBuild): RootBuildState =
        parentBuild.gradle.serviceOf<BuildStateRegistry>().rootBuild

    private
    suspend fun DefaultWriteContext.writeBuildDefinition(buildDefinition: BuildDefinition) {
        buildDefinition.run {
            writeString(name!!)
            writeFile(buildRootDir)
            write(fromBuild)
            writeBoolean(isPluginBuild)
        }
    }

    private
    suspend fun DefaultReadContext.readIncludedBuildDefinition(parentBuild: ConfigurationCacheBuild): BuildDefinition {
        val includedBuildName = readString()
        val includedBuildRootDir = readFile()
        val fromBuild = readNonNull<PublicBuildPath>()
        val pluginBuild = readBoolean()
        return BuildDefinition.fromStartParameterForBuild(
            parentBuild.gradle.startParameter,
            includedBuildName,
            includedBuildRootDir,
            PluginRequests.EMPTY,
            Actions.doNothing(),
            fromBuild,
            pluginBuild
        )
    }

    private
    suspend fun DefaultWriteContext.writeBuildCacheConfiguration(gradle: GradleInternal) {
        gradle.settings.buildCache.let { buildCache ->
            write(buildCache.local)
            write(buildCache.remote)
            write(buildCache.registrations)
        }
    }

    private
    suspend fun DefaultReadContext.readBuildCacheConfiguration(gradle: GradleInternal) {
        gradle.settings.buildCache.let { buildCache ->
            buildCache.local = readNonNull()
            buildCache.remote = read() as BuildCache?
            buildCache.registrations = readNonNull<MutableSet<BuildCacheServiceRegistration>>()
        }
        RootBuildCacheControllerSettingsProcessor.process(gradle)
    }

    private
    suspend fun DefaultWriteContext.writeBuildEventListenerSubscriptions(listeners: List<Provider<*>>) {
        writeCollection(listeners) { listener ->
            when (listener) {
                is RegisteredBuildServiceProvider<*, *> -> {
                    writeBoolean(true)
                    write(listener.buildIdentifier)
                    writeString(listener.name)
                }

                else -> {
                    writeBoolean(false)
                    write(listener)
                }
            }
        }
    }

    private
    suspend fun DefaultReadContext.readBuildEventListenerSubscriptions(gradle: GradleInternal) {
        val eventListenerRegistry by unsafeLazy {
            gradle.serviceOf<BuildEventListenerRegistryInternal>()
        }
        val buildStateRegistry by unsafeLazy {
            gradle.serviceOf<BuildStateRegistry>()
        }
        readCollection {
            when (readBoolean()) {
                true -> {
                    val buildIdentifier = readNonNull<BuildIdentifier>()
                    val serviceName = readString()
                    val provider = buildStateRegistry.buildServiceRegistrationOf(buildIdentifier).getByName(serviceName)
                    eventListenerRegistry.subscribe(provider.service)
                }

                else -> {
                    val provider = readNonNull<Provider<*>>()
                    eventListenerRegistry.subscribe(provider)
                }
            }
        }
    }

    private
    suspend fun DefaultWriteContext.writeBuildOutputCleanupRegistrations(gradle: GradleInternal) {
        val buildOutputCleanupRegistry = gradle.serviceOf<BuildOutputCleanupRegistry>()
        withGradleIsolate(gradle, userTypesCodec) {
            writeCollection(buildOutputCleanupRegistry.registeredOutputs)
        }
    }

    private
    suspend fun DefaultReadContext.readBuildOutputCleanupRegistrations(gradle: GradleInternal) {
        val buildOutputCleanupRegistry = gradle.serviceOf<BuildOutputCleanupRegistry>()
        withGradleIsolate(gradle, userTypesCodec) {
            readCollection {
                val files = readNonNull<FileCollection>()
                buildOutputCleanupRegistry.registerOutputs(files)
            }
        }
    }

    private
    suspend fun DefaultWriteContext.writeCachedEnvironmentState(gradle: GradleInternal) {
        val environmentChangeTracker = gradle.serviceOf<EnvironmentChangeTracker>()
        write(environmentChangeTracker.getCachedState())
    }

    private
    suspend fun DefaultReadContext.readCachedEnvironmentState(gradle: GradleInternal) {
        val environmentChangeTracker = gradle.serviceOf<EnvironmentChangeTracker>()
        val storedState = read() as EnvironmentChangeTracker.CachedEnvironmentState
        environmentChangeTracker.loadFrom(storedState)
    }

    private
    suspend fun DefaultWriteContext.writePreviewFlags(gradle: GradleInternal) {
        val featureFlags = gradle.serviceOf<FeatureFlags>()
        val enabledFeatures = FeaturePreviews.Feature.values().filter { featureFlags.isEnabledWithApi(it) }
        writeCollection(enabledFeatures)
    }

    private
    suspend fun DefaultReadContext.readPreviewFlags(gradle: GradleInternal) {
        val featureFlags = gradle.serviceOf<FeatureFlags>()
        readCollection {
            val enabledFeature = read() as FeaturePreviews.Feature
            featureFlags.enable(enabledFeature)
        }
    }

    private
    suspend fun DefaultWriteContext.writeGradleEnterprisePluginManager(gradle: GradleInternal) {
        val manager = gradle.serviceOf<GradleEnterprisePluginManager>()
        val adapter = manager.adapter
        val writtenAdapter = adapter?.takeIf {
            it.shouldSaveToConfigurationCache()
        }
        write(writtenAdapter)
    }

    private
    suspend fun DefaultReadContext.readGradleEnterprisePluginManager(gradle: GradleInternal) {
        val adapter = read() as GradleEnterprisePluginAdapter?
        if (adapter != null) {
            val manager = gradle.serviceOf<GradleEnterprisePluginManager>()
            if (manager.adapter == null) {
                // Don't replace the existing adapter. The adapter will be present if the current Gradle invocation wrote this entry.
                adapter.onLoadFromConfigurationCache()
                manager.registerAdapter(adapter)
            }
        }
    }

    private
    fun getRelevantProjectsFor(nodes: List<Node>, relevantProjectsRegistry: RelevantProjectsRegistry): List<ProjectState> {
        return fillTheGapsOf(relevantProjectsRegistry.relevantProjects(nodes))
    }

    private
    fun Encoder.writeRelevantProjectRegistrations(relevantProjects: List<ProjectState>) {
        writeCollection(relevantProjects) { project ->
            writeProjectRegistration(project)
        }
    }

    private
    fun Decoder.readRelevantProjectRegistrations(build: ConfigurationCacheBuild) {
        readCollection {
            readProjectRegistration(build)
        }
    }

    private
    fun Encoder.writeProjectRegistration(project: ProjectState) {
        val mutableModel = project.mutableModel
        writeString(mutableModel.path)
        writeFile(mutableModel.projectDir)
        writeFile(mutableModel.layout.buildDirectory.apply { finalizeValue() }.get().asFile)
    }

    private
    fun Decoder.readProjectRegistration(build: ConfigurationCacheBuild) {
        val projectPath = readString()
        val projectDir = readFile()
        val buildDir = readFile()
        build.createProject(projectPath, projectDir, buildDir)
    }

    private
    fun stateFileFor(buildDefinition: BuildDefinition) =
        stateFile.stateFileForIncludedBuild(buildDefinition)

    private
    val userTypesCodec
        get() = codecs.userTypesCodec()

    private
    fun storedBuilds() = object : StoredBuilds {
        val buildRootDirs = hashSetOf<File>()
        override fun store(build: BuildDefinition): Boolean =
            buildRootDirs.add(build.buildRootDir!!)
    }

    private
    fun buildIdentifierOf(gradle: GradleInternal) =
        gradle.owner.buildIdentifier

    private
    fun buildEventListenersOf(gradle: GradleInternal) =
        gradle.serviceOf<BuildEventListenerRegistryInternal>()
            .subscriptions
            .filterIsInstance<RegisteredBuildServiceProvider<*, *>>()
            .filter(::isRelevantBuildEventListener)

    private
    fun isRelevantBuildEventListener(provider: RegisteredBuildServiceProvider<*, *>) =
        provider.buildIdentifier.name != BUILD_SRC

    private
    fun BuildStateRegistry.buildServiceRegistrationOf(buildId: BuildIdentifier) =
        getBuild(buildId).mutableModel.serviceOf<BuildServiceRegistryInternal>().registrations

    private
    fun fireConfigureBuild(buildOperationExecutor: BuildOperationExecutor, gradle: GradleInternal, function: (gradle: GradleInternal) -> Unit) {
        BuildOperationFiringProjectsPreparer(function, buildOperationExecutor).prepareProjects(gradle)
    }

    /**
     * Fire build operation required by build scans to determine the root path.
     **/
    private
    fun fireConfigureProject(buildOperationExecutor: BuildOperationExecutor, gradle: GradleInternal) {
        buildOperationExecutor.run(object : RunnableBuildOperation {
            override fun run(context: BuildOperationContext) = Unit
            override fun description(): BuildOperationDescriptor.Builder =
                LifecycleProjectEvaluator.configureProjectBuildOperationBuilderFor(gradle.rootProject)
        })
    }

    /**
     * Fire _Load projects_ build operation required by build scans to determine the build's project structure (and build load time).
     **/
    private
    fun fireLoadProjects(buildOperationExecutor: BuildOperationExecutor, emitter: BuildOperationProgressEventEmitter, gradle: GradleInternal) {
        NotifyingBuildLoader({ _, _ -> }, buildOperationExecutor, emitter).load(gradle.settings, gradle)
    }

    /**
     * Fires build operation required by build scan to determine startup duration and settings evaluated duration.
     */
    private
    fun fireLoadBuild(preparer: () -> Unit, gradle: GradleInternal) {
        BuildOperationFiringSettingsPreparer(
            { preparer() },
            gradle.serviceOf(),
            gradle.serviceOf(),
            gradle.serviceOf<BuildDefinition>().fromBuild
        ).prepareSettings(gradle)
    }

    /**
     * Fire build operation required by build scans to determine build path (and settings execution time).
     * It may be better to instead point GE at the origin build that produced the cached task graph,
     * or replace this with a different event/op that carries this information and wraps some actual work.
     **/
    private
    fun fireEvaluateSettings(gradle: GradleInternal) {
        BuildOperationSettingsProcessor(
            { _, _, _, _ -> gradle.settings },
            gradle.serviceOf()
        ).process(
            gradle,
            SettingsLocation(gradle.settings.settingsDir, null),
            gradle.classLoaderScope,
            gradle.startParameter.apply {
                useEmptySettings()
            }
        )
    }
}


internal
class StoredBuildTreeState(
    val storedBuilds: StoredBuilds,
    val requiredBuildServicesPerBuild: Map<BuildIdentifier, List<BuildServiceProvider<*, *>>>
)


internal
interface StoredBuilds {
    /**
     * Returns true if this is the first time the given [build] is seen and its state should be stored to the cache.
     * Returns false if the build has already been stored to the cache.
     */
    fun store(build: BuildDefinition): Boolean
}


internal
fun fillTheGapsOf(projects: Collection<ProjectState>): List<ProjectState> {
    val projectsWithoutGaps = ArrayList<ProjectState>(projects.size)
    var index = 0
    projects.forEach { project ->
        var parent = project.parent
        var added = 0
        while (parent !== null && parent !in projectsWithoutGaps) {
            projectsWithoutGaps.add(index, parent)
            added += 1
            parent = parent.parent
        }
        if (project !in projectsWithoutGaps) {
            projectsWithoutGaps.add(project)
            added += 1
        }
        index += added
    }
    return projectsWithoutGaps
}
