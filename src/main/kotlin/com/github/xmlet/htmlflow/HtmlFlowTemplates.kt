package com.github.xmlet.htmlflow

import htmlflow.HtmlView
import htmlflow.HtmlViewAsync
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.ContentType.Companion.TEXT_HTML
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.lens.Header.CONTENT_TYPE
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer
import org.http4k.template.Templates
import org.http4k.template.ViewModel
import org.http4k.websocket.WsMessage
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.UnsupportedOperationException
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * HtmlFlow template implementation that scans classpath for HtmlView instances
 * and provides template rendering capabilities.
 *
 * This implementation only supports classpath-based template discovery and does not
 * support caching or hot reloading.
 *
 * Alternatively, use [HtmlView.renderer] or [HtmlViewAsync.renderer] to create
 * a [TemplateRenderer] directly from an [HtmlView] instance.
 */
class HtmlFlowTemplates : Templates {
    override fun Caching(baseTemplateDir: String): TemplateRenderer {
        throw UnsupportedOperationException(
            "HtmlFlow: Template directory caching is not supported." +
                "Use CachingClasspath() instead.",
        )
    }

    override fun CachingClasspath(baseClasspathPackage: String): TemplateRenderer {
        val viewRegistry = scanForHtmlViews(baseClasspathPackage)
        return createTemplateRenderer(viewRegistry)
    }

    override fun HotReload(baseTemplateDir: String): TemplateRenderer {
        throw UnsupportedOperationException(
            "HtmlFlow: Hot reload from template directories is not supported." +
                " Use CachingClasspath() instead.",
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HtmlFlowTemplates::class.java)

        /**
         * Thread-safe cache for view registries, keyed by scanned package name.
         * This prevents redundant classpath scanning for the same packages.
         */
        private val cachedViewRegistries = ConcurrentHashMap<String, Map<Class<*>, ViewInfo>>()

        /**
         * Represents information about a discovered HTML view.
         *
         * @property view The actual view instance (HtmlView or HtmlViewAsync)
         * @property location Human-readable location description for debugging
         * @property viewModelType The type of ViewModel this view expects
         */
        class ViewInfo(
            val view: Any,
            val location: String,
            val viewModelType: Class<*>,
        )

        /**
         * Packages to skip during classpath scanning for performance optimization.
         * These are known system/library packages that won't contain HTML views.
         */
        private val EXCLUDED_PACKAGES =
            setOf(
                // Kotlin stdlib
                "kotlin",
                "kotlinx",
                // Java stdlib
                "java",
                "javax",
                "jdk",
                "sun",
                "com.sun",
                "com.oracle",
                // Common libraries that won't have HTML views
                "org.slf4j",
                "ch.qos.logback",
                "org.apache.logging",
                "org.junit",
                "org.mockito",
                "org.springframework",
                "org.jetbrains.annotations",
            )
    }

    /**
     * Scans the specified package for HTML views, using caching to avoid redundant scans.
     *
     * @param basePackage The root package to scan (empty string scans all packages)
     *
     * @return Registry mapping ViewModel types to their corresponding view information
     */
    private fun scanForHtmlViews(basePackage: String = ""): Map<Class<*>, ViewInfo> {
        return cachedViewRegistries.computeIfAbsent(basePackage) { packageName ->
            logger.info("Scanning package '$packageName' for HTML views...")
            val registry = performPackageScan(packageName)
            logger.info(
                "Successfully scanned package '$packageName' - found ${registry.size} HTML views",
            )
            registry
        }
    }

    /**
     * Performs the actual classpath scanning for the given package.
     */
    private fun performPackageScan(basePackage: String): Map<Class<*>, ViewInfo> {
        val viewRegistry = mutableMapOf<Class<*>, ViewInfo>()
        val classLoaders = getAvailableClassLoaders()
        val packagePath = basePackage.replace('.', '/')

        var resourcesFound = false

        for ((index, classLoader) in classLoaders.withIndex()) {
            try {
                val resources = classLoader.getResources(packagePath)
                while (resources.hasMoreElements()) {
                    resourcesFound = true
                    val resource = resources.nextElement()
                    processClasspathResource(resource, basePackage, viewRegistry)
                }
                if (resourcesFound) break
            } catch (e: Exception) {
                if (index == classLoaders.lastIndex) {
                    throw e
                }
                logger.debug("Failed to scan with classloader ${classLoader::class.simpleName}", e)
            }
        }

        if (!resourcesFound) {
            logger.warn("No resources found for package: $basePackage")
        }

        return viewRegistry.toMap()
    }

    private fun getAvailableClassLoaders(): List<ClassLoader> {
        return listOfNotNull(
            Thread.currentThread().contextClassLoader,
            this::class.java.classLoader,
            ClassLoader.getSystemClassLoader(),
        ).distinct()
    }

    /**
     * Processes a classpath resource (either file system directory or JAR entry).
     */
    private fun processClasspathResource(
        resource: URL,
        basePackage: String,
        viewRegistry: MutableMap<Class<*>, ViewInfo>,
    ) {
        when (resource.protocol) {
            "file" -> processFileSystemResource(resource, basePackage, viewRegistry)
            "jar" -> processJarResource(resource, basePackage, viewRegistry)
            else -> logger.debug("Unsupported protocol: ${resource.protocol}")
        }
    }

    /**
     * Processes resources from the file system.
     */
    private fun processFileSystemResource(
        resource: URL,
        basePackage: String,
        viewRegistry: MutableMap<Class<*>, ViewInfo>,
    ) {
        val packageDirectory = File(resource.toURI())
        if (packageDirectory.exists() && packageDirectory.isDirectory) {
            scanDirectory(packageDirectory, basePackage, viewRegistry)
        }
    }

    /**
     * Processes resources from JAR files, with package exclusion.
     */
    private fun processJarResource(
        resource: URL,
        basePackage: String,
        viewRegistry: MutableMap<Class<*>, ViewInfo>,
    ) {
        val jarPath = resource.path.substringBefore("!")
        val jarFile = JarFile(URI(jarPath).path)
        val packagePath = basePackage.replace('.', '/')

        jarFile.entries().asSequence()
            .filter { entry -> isRelevantClassEntry(entry, packagePath) }
            .forEach { entry ->
                val className = convertEntryNameToClassName(entry.name)
                if (!shouldExcludeClass(className)) {
                    loadAndScanClass(className, viewRegistry)
                }
            }
    }

    /**
     * Recursively scans a directory for class files.
     */
    private fun scanDirectory(
        directory: File,
        packageName: String,
        viewRegistry: MutableMap<Class<*>, ViewInfo>,
    ) {
        if (shouldExcludePackage(packageName)) {
            logger.debug("Skipping excluded package directory: $packageName")
            return
        }

        val files = directory.listFiles() ?: return

        files.forEach { file ->
            when {
                file.isDirectory -> {
                    val subPackage = buildSubPackageName(packageName, file.name)
                    scanDirectory(file, subPackage, viewRegistry)
                }

                isRelevantClassFile(file) -> {
                    val className = buildClassName(packageName, file.nameWithoutExtension)
                    loadAndScanClass(className, viewRegistry)
                }
            }
        }
    }

    /**
     * Method to load and scan a class.
     */
    private fun loadAndScanClass(
        className: String,
        viewRegistry: MutableMap<Class<*>, ViewInfo>,
    ) {
        if (shouldExcludeClass(className)) {
            logger.debug("Skipping excluded class: $className")
            return
        }

        try {
            val clazz = Class.forName(className)
            scanClassForHtmlViews(clazz, viewRegistry)

            if (!className.endsWith("Kt")) {
                tryLoadAndScanKotlinFileClass(className, viewRegistry)
            }
        } catch (_: ClassNotFoundException) {
            logger.debug("Class not found: $className")
        } catch (_: NoClassDefFoundError) {
            logger.debug("Dependencies missing for class: $className")
        } catch (e: Exception) {
            logger.debug("Failed to load class: $className", e)
            throw e
        }
    }

    /**
     * Attempts to load and scan the Kotlin file class for top-level properties.
     * For example, if we have "com.example.Main", this will try "com.example.MainKt".
     */
    private fun tryLoadAndScanKotlinFileClass(
        originalClassName: String,
        viewRegistry: MutableMap<Class<*>, ViewInfo>,
    ) {
        try {
            val kotlinFileClassName = "${originalClassName}Kt"
            val kotlinFileClass = Class.forName(kotlinFileClassName)
            scanClassForHtmlViews(kotlinFileClass, viewRegistry)
        } catch (_: ClassNotFoundException) {
        } catch (e: Exception) {
            logger.debug("Failed to scan Kotlin file class for: $originalClassName", e)
        }
    }

    /**
     * Scans a loaded class for HTML view fields and methods.
     */
    private fun scanClassForHtmlViews(
        clazz: Class<*>,
        viewRegistry: MutableMap<Class<*>, ViewInfo>,
    ) {
        if (shouldSkipClass(clazz)) return

        try {
            val objectInstance = createInstanceForScanning(clazz)
            scanClassMembers(clazz, objectInstance, viewRegistry)
        } catch (e: Exception) {
            logger.debug("Failed to scan class: ${clazz.name}", e)
            throw e
        }
    }

    /**
     * Enhanced method to handle Kotlin companion objects and top-level properties.
     */
    private fun createInstanceForScanning(clazz: Class<*>): Any? {
        // For Kotlin file classes (top-level properties), we don't need an instance
        if (clazz.name.endsWith("Kt")) {
            return null
        }

        return tryKotlinObjectInstance(clazz)
            ?: tryKotlinCompanionObject(clazz)
            ?: trySingletonInstance(clazz)
            ?: tryDefaultConstructor(clazz)
    }

    private fun tryKotlinObjectInstance(clazz: Class<*>): Any? {
        return try {
            clazz.getDeclaredField("INSTANCE").get(null)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Tries to get a Kotlin companion object instance.
     */
    private fun tryKotlinCompanionObject(clazz: Class<*>): Any? {
        return try {
            val companionClass = clazz.declaredClasses.find { it.simpleName == "Companion" }
            companionClass?.getDeclaredField("INSTANCE")?.get(null)
        } catch (_: Exception) {
            null
        }
    }

    private fun trySingletonInstance(clazz: Class<*>): Any? {
        return try {
            clazz.getDeclaredMethod("getInstance").invoke(null)
        } catch (_: Exception) {
            null
        }
    }

    private fun tryDefaultConstructor(clazz: Class<*>): Any? {
        return try {
            clazz.getDeclaredConstructor().newInstance()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Scans class members (fields, methods, and top-level properties) for HTML views.
     */
    private fun scanClassMembers(
        clazz: Class<*>,
        objectInstance: Any?,
        viewRegistry: MutableMap<Class<*>, ViewInfo>,
    ) {
        scanMethodsForViews(clazz, objectInstance, viewRegistry)
        scanFieldsForViews(clazz, objectInstance, viewRegistry)
        scanTopLevelPropertiesForViews(clazz, viewRegistry)
    }

    /**
     * Scans class methods for HTML views.
     */
    private fun scanMethodsForViews(
        clazz: Class<*>,
        objectInstance: Any?,
        viewRegistry: MutableMap<Class<*>, ViewInfo>,
    ) {
        clazz.declaredMethods
            .filter { method -> isRelevantViewMethod(method) }
            .forEach { method -> processViewMethod(method, objectInstance, viewRegistry) }
    }

    /**
     * Scans class fields for HTML views.
     */
    private fun scanFieldsForViews(
        clazz: Class<*>,
        objectInstance: Any?,
        viewRegistry: MutableMap<Class<*>, ViewInfo>,
    ) {
        clazz.declaredFields
            .filter { field -> isRelevantViewField(field) && !Modifier.isStatic(field.modifiers) }
            .forEach { field -> processViewField(field, objectInstance, viewRegistry) }
    }

    /**
     * Scans for top-level Kotlin properties that contain HTML views.
     * Top-level properties in Kotlin are compiled as static fields in a companion class
     * with names ending in "Kt".
     */
    private fun scanTopLevelPropertiesForViews(
        clazz: Class<*>,
        viewRegistry: MutableMap<Class<*>, ViewInfo>,
    ) {
        // Skip if this is not a Kotlin file class
        if (!clazz.name.endsWith("Kt")) return

        try {
            // Top-level properties are compiled as static fields
            clazz.declaredFields
                .filter { field ->
                    Modifier.isStatic(field.modifiers) &&
                        !field.isSynthetic &&
                        isRelevantViewField(field)
                }
                .forEach { field ->
                    processTopLevelViewProperty(field, viewRegistry)
                }
        } catch (e: Exception) {
            logger.debug("Failed to scan top-level properties in class: ${clazz.name}", e)
        }
    }

    /**
     * Checks if a JAR entry represents a relevant class file.
     */
    private fun isRelevantClassEntry(
        entry: JarEntry,
        packagePath: String,
    ): Boolean {
        return !entry.isDirectory &&
            entry.name.startsWith(packagePath) &&
            entry.name.endsWith(".class")
    }

    /**
     * Converts JAR entry name to fully qualified class name.
     */
    private fun convertEntryNameToClassName(entryName: String): String {
        return entryName.removeSuffix(".class").replace('/', '.')
    }

    /**
     * Checks if a package should be excluded from scanning.
     */
    private fun shouldExcludePackage(packageName: String): Boolean {
        return EXCLUDED_PACKAGES.any { excludedPackage ->
            packageName.startsWith(excludedPackage)
        }
    }

    /**
     * Checks if a class name should be excluded from scanning.
     */
    private fun shouldExcludeClass(className: String): Boolean {
        return EXCLUDED_PACKAGES.any { excludedPackage ->
            className.startsWith(excludedPackage)
        }
    }

    /**
     * Processes a method that returns an HTML view.
     */
    private fun processViewMethod(
        method: Method,
        objectInstance: Any?,
        viewRegistry: MutableMap<Class<*>, ViewInfo>,
    ) {
        method.isAccessible = true
        val view = method.invoke(objectInstance) ?: return
        val location = "${method.declaringClass.name}.${method.name}()"
        registerView(view, method.genericReturnType, viewRegistry, location)
    }

    /**
     * Processes a field that contains an HTML view.
     */
    private fun processViewField(
        field: Field,
        objectInstance: Any?,
        viewRegistry: MutableMap<Class<*>, ViewInfo>,
    ) {
        try {
            field.isAccessible = true
            val view = field.get(objectInstance) ?: return
            val location = "${field.declaringClass.name}.${field.name}"
            registerView(view, field.genericType, viewRegistry, location)
        } catch (e: Exception) {
            logger.debug("Failed to process view field: ${field.name}", e)
        }
    }

    /**
     * Processes a top-level property that contains an HTML view.
     */
    private fun processTopLevelViewProperty(
        field: Field,
        viewRegistry: MutableMap<Class<*>, ViewInfo>,
    ) {
        try {
            field.isAccessible = true
            val view = field.get(null) ?: return // Static field, so pass null as instance
            val location = "${field.declaringClass.name}.${field.name} (top-level property)"
            registerView(view, field.genericType, viewRegistry, location)
        } catch (e: Exception) {
            logger.debug("Failed to process top-level view property: ${field.name}", e)
        }
    }

    /**
     * Checks if a file is a relevant class file (not inner classes).
     */
    private fun isRelevantClassFile(file: File): Boolean {
        return file.name.endsWith(".class") && !file.name.contains('$')
    }

    /**
     * Builds a sub-package name from parent package and directory name.
     */
    private fun buildSubPackageName(
        parentPackage: String,
        directoryName: String,
    ): String {
        return if (parentPackage.isEmpty()) directoryName else "$parentPackage.$directoryName"
    }

    /**
     * Builds a fully qualified class name.
     */
    private fun buildClassName(
        packageName: String,
        simpleClassName: String,
    ): String {
        return if (packageName.isEmpty()) simpleClassName else "$packageName.$simpleClassName"
    }

    /**
     * Determines if a class should be skipped during scanning.
     */
    private fun shouldSkipClass(clazz: Class<*>): Boolean {
        return clazz.isInterface || clazz.isAnnotation || clazz.isEnum
    }

    /**
     * Determines if a method is a relevant HTML view method.
     */
    private fun isRelevantViewMethod(method: Method): Boolean {
        return !method.isSynthetic &&
            method.parameterCount == 0 &&
            (isHtmlViewMethod(method) || isHtmlViewAsyncMethod(method))
    }

    private fun isHtmlViewMethod(method: Method): Boolean = HtmlView::class.java.isAssignableFrom(method.returnType)

    private fun isHtmlViewAsyncMethod(method: Method): Boolean =
        HtmlViewAsync::class.java.isAssignableFrom(method.returnType)

    /**
     * Determines if a field is a relevant HTML view field.
     */
    private fun isRelevantViewField(field: Field): Boolean {
        return !field.isSynthetic &&
            (isHtmlViewField(field) || isHtmlViewAsyncField(field))
    }

    private fun isHtmlViewField(field: Field): Boolean = HtmlView::class.java.isAssignableFrom(field.type)

    private fun isHtmlViewAsyncField(field: Field): Boolean = HtmlViewAsync::class.java.isAssignableFrom(field.type)

    /**
     * Registers a discovered view in the registry.
     *
     * @throws IllegalStateException if multiple views are found for the same ViewModel type
     */
    private fun registerView(
        view: Any,
        genericType: Type,
        viewRegistry: MutableMap<Class<*>, ViewInfo>,
        viewLocation: String,
    ) {
        val viewModelType = extractViewModelType(genericType) ?: return

        checkForDuplicateViewRegistration(viewRegistry, viewModelType, viewLocation)

        viewRegistry[viewModelType] = ViewInfo(view, viewLocation, viewModelType)
        logger.debug("Registered view for type ${viewModelType.simpleName} at $viewLocation")
    }

    /**
     * Checks for duplicate view registrations and throws an exception if found.
     */
    private fun checkForDuplicateViewRegistration(
        viewRegistry: Map<Class<*>, ViewInfo>,
        viewModelType: Class<*>,
        newLocation: String,
    ) {
        val existing = viewRegistry[viewModelType]
        if (existing != null) {
            throw IllegalStateException(
                "Multiple views found for ViewModel type '${viewModelType.simpleName}'. " +
                    "Existing: ${existing.location}, New: $newLocation",
            )
        }
    }

    /**
     * Extracts the ViewModel type from a generic type (e.g., HtmlView<MyViewModel>).
     */
    private fun extractViewModelType(genericType: Type): Class<*>? {
        return when (genericType) {
            is ParameterizedType -> extractFromParameterizedType(genericType)
            is Class<*> -> extractFromClassType(genericType)
            else -> null
        }
    }

    private fun extractFromParameterizedType(parameterizedType: ParameterizedType): Class<*>? {
        val typeArguments = parameterizedType.actualTypeArguments
        return if (typeArguments.isNotEmpty()) {
            typeArguments[0] as? Class<*>
        } else {
            null
        }
    }

    private fun extractFromClassType(classType: Class<*>): Class<*>? {
        val superType = classType.genericSuperclass
        return if (superType is ParameterizedType) {
            extractViewModelType(superType)
        } else {
            null
        }
    }

    /**
     * Creates a template renderer from the view registry.
     */
    private fun createTemplateRenderer(viewRegistry: Map<Class<*>, ViewInfo>): TemplateRenderer {
        return object : TemplateRenderer {
            override fun invoke(viewModel: ViewModel): String {
                val viewInfo = findCompatibleView(viewModel, viewRegistry)
                return renderViewWithModel(viewInfo.view, viewModel, viewInfo.location)
            }
        }
    }

    /**
     * Finds a compatible view for the given ViewModel.
     * Uses inheritance hierarchy and interface matching for flexibility.
     */
    private fun findCompatibleView(
        viewModel: ViewModel,
        viewRegistry: Map<Class<*>, ViewInfo>,
    ): ViewInfo {
        val viewModelClass = viewModel::class.java

        // Direct match
        viewRegistry[viewModelClass]?.let { return it }

        // Check inheritance hierarchy
        findViewInInheritanceChain(viewModelClass, viewRegistry)?.let { return it }

        // Check interfaces
        findViewInInterfaces(viewModelClass, viewRegistry)?.let { return it }

        // Check assignable types
        findAssignableView(viewModelClass, viewRegistry)?.let { return it }

        // No compatible view found
        throw IllegalArgumentException(
            "No compatible HtmlView found for ViewModel type: ${viewModelClass.simpleName}. " +
                "Available views: ${viewRegistry.keys.joinToString { it.simpleName }}",
        )
    }

    private fun findViewInInheritanceChain(
        viewModelClass: Class<*>,
        viewRegistry: Map<Class<*>, ViewInfo>,
    ): ViewInfo? {
        var currentClass: Class<*>? = viewModelClass.superclass
        while (currentClass != null) {
            viewRegistry[currentClass]?.let { return it }
            currentClass = currentClass.superclass
        }
        return null
    }

    private fun findViewInInterfaces(
        viewModelClass: Class<*>,
        viewRegistry: Map<Class<*>, ViewInfo>,
    ): ViewInfo? {
        for (interfaceClass in viewModelClass.interfaces) {
            viewRegistry[interfaceClass]?.let { return it }
        }
        return null
    }

    private fun findAssignableView(
        viewModelClass: Class<*>,
        viewRegistry: Map<Class<*>, ViewInfo>,
    ): ViewInfo? {
        return viewRegistry.entries
            .find { (type, _) -> type.isAssignableFrom(viewModelClass) }
            ?.value
    }

    /**
     * Renders a view with the provided ViewModel.
     * Handles both synchronous and asynchronous views.
     */
    private fun renderViewWithModel(
        view: Any,
        viewModel: ViewModel,
        location: String,
    ): String {
        return try {
            when (view) {
                is HtmlView<*> -> renderSynchronousView(view, viewModel)
                is HtmlViewAsync<*> -> renderAsynchronousView(view, viewModel)
                else -> throw IllegalArgumentException(
                    "Unsupported view type: ${view::class.simpleName} at $location",
                )
            }
        } catch (e: Exception) {
            throw RuntimeException(
                "Failed to render view at $location for model ${viewModel::class.simpleName}",
                e,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun renderSynchronousView(
        view: HtmlView<*>,
        viewModel: ViewModel,
    ): String {
        return (view as HtmlView<ViewModel>).render(viewModel)
    }

    @Suppress("UNCHECKED_CAST")
    private fun renderAsynchronousView(
        view: HtmlViewAsync<*>,
        viewModel: ViewModel,
    ): String {
        // Note: HTTP4k doesn't support async rendering, so we block and wait for the result
        return (view as HtmlViewAsync<ViewModel>).renderAsync(viewModel).get()
    }
}

/**
 * Creates a [TemplateRenderer] from an [HtmlView].
 *
 * The resulting renderer will perform synchronous rendering and includes
 * proper type checking and error handling.
 *
 * @throws IllegalArgumentException if the provided ViewModel is not compatible
 */
inline fun <reified T : ViewModel> HtmlView<T>.renderer(): TemplateRenderer {
    return { viewModel: ViewModel ->
        try {
            @Suppress("UNCHECKED_CAST")
            this.render(viewModel as T)
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(
                "ViewModel type mismatch for view ${this::class.simpleName}. " +
                    "Expected: ${T::class.simpleName}, Got: ${viewModel::class.simpleName}",
                e,
            )
        }
    }
}

/**
 * Creates a [TemplateRenderer] from an [HtmlViewAsync].
 *
 * The resulting renderer will block and wait for asynchronous rendering to complete.
 *
 * @throws IllegalArgumentException if the provided ViewModel is not compatible
 *
 */
inline fun <reified T : ViewModel> HtmlViewAsync<T>.renderer(): TemplateRenderer {
    return { viewModel: ViewModel ->
        try {
            @Suppress("UNCHECKED_CAST")
            this.renderAsync(viewModel as T).get()
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(
                "ViewModel type mismatch for async view ${this::class.simpleName}. " +
                    "Expected: ${T::class.simpleName}, Got: ${viewModel::class.simpleName}",
                e,
            )
        }
    }
}

// Suggested strongly typed template renderer interface for HTTP4K.
//
// This interface provides compile-time type safety for template rendering by accepting
// a specific ViewModel type parameter instead of the generic ViewModel interface.
//
// Why TypedTemplateRenderer is needed:
//
// The standard HTTP4K TemplateRenderer has the signature `(ViewModel) -> String`, which means:
// - Views must accept the generic ViewModel interface as a parameter
// - No compile-time type safety when passing specific ViewModel implementations
// - Runtime casting is required within view implementations
//
// TypedTemplateRenderer solves this by:
// - Accepting a specific ViewModel type T that extends ViewModel
// - Providing compile-time type checking
// - Eliminating the need for runtime casting in view code
interface TypedTemplateRenderer<T : ViewModel> {
    operator fun invoke(viewModel: T): String
}

fun <T : ViewModel> Body.Companion.viewModel(
    renderer: TypedTemplateRenderer<T>,
    contentType: ContentType,
) = string(contentType).map<T>({
    throw UnsupportedOperationException("Cannot parse a ViewModel")
}, renderer::invoke)

fun <T : ViewModel> WsMessage.Companion.viewModel(renderer: TypedTemplateRenderer<T>) =
    string().map<T>({
        throw UnsupportedOperationException("Cannot parse a ViewModel")
    }, renderer::invoke)

/**
 * Convenience method for generating a Response from a view model.
 */
fun <T : ViewModel> TypedTemplateRenderer<T>.renderToResponse(
    viewModel: T,
    status: Status = OK,
    contentType: ContentType = TEXT_HTML,
): Response = Response(status).with(CONTENT_TYPE of contentType).body(invoke(viewModel))

fun <T : ViewModel> HtmlView<T>.rendererTyped(): TypedTemplateRenderer<T> {
    return object : TypedTemplateRenderer<T> {
        override fun invoke(viewModel: T): String {
            return this@rendererTyped.render(viewModel)
        }
    }
}

fun <T : ViewModel> HtmlViewAsync<T>.rendererTyped(): TypedTemplateRenderer<T> {
    return object : TypedTemplateRenderer<T> {
        override fun invoke(viewModel: T): String {
            return this@rendererTyped.renderAsync(viewModel).get()
        }
    }
}
