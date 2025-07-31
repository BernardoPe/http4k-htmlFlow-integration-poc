package com.github.xmlet.htmlflow

import htmlflow.HtmlView
import htmlflow.HtmlViewAsync
import org.http4k.template.TemplateRenderer
import org.http4k.template.Templates
import org.http4k.template.ViewModel
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import kotlin.jvm.java


class HtmlFlowTemplates : Templates {

    override fun Caching(baseTemplateDir: String): TemplateRenderer {
        throw UnsupportedOperationException(
            "HtmlFlow: HtmlFlowTemplates does not support Caching with template directories"
        )
    }

    override fun CachingClasspath(baseClasspathPackage: String): TemplateRenderer {
        val viewMap = scanForHtmlViews(baseClasspathPackage)
        return createRenderer(viewMap)
    }

    override fun HotReload(baseTemplateDir: String): TemplateRenderer {
        throw UnsupportedOperationException(
            "HtmlFlow: HtmlFlowTemplates does not support HotReload with template directories"
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HtmlFlowTemplates::class.java)

        /**
         * Thread-safe cache for HtmlView instances, keyed by scanned package name.
         */
        private val cachedViewMaps = ConcurrentHashMap<String, Map<Class<*>, ViewInfo>>()

        /**
         * Represents view information including the view instance and its location.
         */
        data class ViewInfo(
            val view: Any,
            val location: String,
            val viewModelType: Class<*>
        )
    }

    private fun scanForHtmlViews(basePackage: String = ""): Map<Class<*>, ViewInfo> {
        return cachedViewMaps.computeIfAbsent(basePackage) { pkg ->
            try {
                val viewMap = mutableMapOf<Class<*>, ViewInfo>()
                scanPackageForViews(pkg, viewMap)
                logger.info("Scanned package '$pkg' and found ${viewMap.size} HTML views")
                viewMap.toMap()
            } catch (e: Exception) {
                logger.error("Failed to scan package '$pkg' for HTML views", e)
                emptyMap()
            }
        }
    }

    private fun scanPackageForViews(basePackage: String, viewMap: MutableMap<Class<*>, ViewInfo>) {
        val classLoaders = listOfNotNull(
            Thread.currentThread().contextClassLoader,
            this::class.java.classLoader,
            ClassLoader.getSystemClassLoader()
        ).distinct()

        val packagePath = basePackage.replace('.', '/')
        var resourcesFound = false

        for (classLoader in classLoaders) {
            try {
                val resources = classLoader.getResources(packagePath)
                while (resources.hasMoreElements()) {
                    resourcesFound = true
                    val resource = resources.nextElement()
                    processResource(resource, basePackage, viewMap)
                }
                if (resourcesFound) break
            } catch (e: Exception) {
                logger.debug("Failed to scan with classloader ${classLoader::class.simpleName}", e)
            }
        }

        if (!resourcesFound) {
            logger.warn("No resources found for package: $basePackage")
        }
    }

    private fun processResource(resource: URL, basePackage: String, viewMap: MutableMap<Class<*>, ViewInfo>) {
        when (resource.protocol) {
            "file" -> {
                val packageDir = File(resource.toURI())
                if (packageDir.exists() && packageDir.isDirectory) {
                    scanDirectory(packageDir, basePackage, viewMap)
                }
            }
            "jar" -> {
                scanJarResource(resource, basePackage, viewMap)
            }
            else -> {
                logger.debug("Unsupported protocol: ${resource.protocol}")
            }
        }
    }

    private fun scanJarResource(resource: URL, basePackage: String, viewMap: MutableMap<Class<*>, ViewInfo>) {
        try {
            val jarPath = resource.path.substringBefore("!")
            val jarFile = JarFile(URI(jarPath).path)
            val packagePath = basePackage.replace('.', '/')
            jarFile.entries().asSequence()
                .filter { entry ->
                    !entry.isDirectory &&
                            entry.name.startsWith(packagePath) &&
                            entry.name.endsWith(".class")
                }
                .forEach { entry ->
                    val className = entry.name
                        .removeSuffix(".class")
                        .replace('/', '.')

                    loadAndScanClass(className, viewMap)
                }
        } catch (e: Exception) {
            logger.debug("Failed to scan JAR resource: {}", resource, e)
        }
    }

    private fun scanDirectory(dir: File, packageName: String, viewMap: MutableMap<Class<*>, ViewInfo>) {
        val files = dir.listFiles() ?: return

        files.forEach { file ->
            when {
                file.isDirectory -> {
                    val subPackage = if (packageName.isEmpty()) file.name else "$packageName.${file.name}"
                    scanDirectory(file, subPackage, viewMap)
                }
                file.name.endsWith(".class") && !file.name.contains('$') -> {
                    val className = file.name.removeSuffix(".class")
                    val fullClassName = if (packageName.isEmpty()) className else "$packageName.$className"
                    loadAndScanClass(fullClassName, viewMap)
                }
            }
        }
    }

    private fun loadAndScanClass(className: String, viewMap: MutableMap<Class<*>, ViewInfo>) {
        try {
            val clazz = Class.forName(className)
            scanClassForHtmlViews(clazz, viewMap)
        } catch (_: ClassNotFoundException) {
            logger.debug("Class not found: $className")
        } catch (_: NoClassDefFoundError) {
            logger.debug("Dependencies missing for class: $className")
        } catch (e: Exception) {
            logger.debug("Failed to load class: $className", e)
        }
    }

    private fun scanClassForHtmlViews(clazz: Class<*>, viewMap: MutableMap<Class<*>, ViewInfo>) {
        if (clazz.isInterface || clazz.isAnnotation || clazz.isEnum) return

        try {
            val objectInstance = getObjectInstance(clazz)
            scanMembersForViews(clazz, objectInstance, viewMap)
        } catch (e: Exception) {
            logger.debug("Failed to scan class: ${clazz.name}", e)
        }
    }

    private fun getObjectInstance(clazz: Class<*>): Any? {
        return try {
            // Try Kotlin object instance
            clazz.getDeclaredField("INSTANCE").get(null)
        } catch (_: NoSuchFieldException) {
            // Try singleton instance
            try {
                clazz.getDeclaredMethod("getInstance").invoke(null)
            } catch (_: Exception) {
                // Try default constructor
                try {
                    clazz.getDeclaredConstructor().newInstance()
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun scanMembersForViews(clazz: Class<*>, objectInstance: Any?, viewMap: MutableMap<Class<*>, ViewInfo>) {
        clazz.declaredFields
            .filter { field ->
                !field.isSynthetic &&
                        (isHtmlViewField(field) || isHtmlViewAsyncField(field))
            }
            .forEach { field ->
                processViewField(field, objectInstance, viewMap)
            }

        clazz.declaredMethods
            .filter { method ->
                !method.isSynthetic &&
                        method.parameterCount == 0 &&
                        (isHtmlViewMethod(method) || isHtmlViewAsyncMethod(method))
            }
            .forEach { method ->
                processViewMethod(method, objectInstance, viewMap)
            }
    }

    private fun isHtmlViewField(field: Field): Boolean =
        HtmlView::class.java.isAssignableFrom(field.type)

    private fun isHtmlViewAsyncField(field: Field): Boolean =
        HtmlViewAsync::class.java.isAssignableFrom(field.type)

    private fun isHtmlViewMethod(method: Method): Boolean =
        HtmlView::class.java.isAssignableFrom(method.returnType)

    private fun isHtmlViewAsyncMethod(method: Method): Boolean =
        HtmlViewAsync::class.java.isAssignableFrom(method.returnType)

    private fun processViewField(field: Field, objectInstance: Any?, viewMap: MutableMap<Class<*>, ViewInfo>) {
        try {
            field.isAccessible = true
            val view = field.get(objectInstance) ?: return
            val location = "${field.declaringClass.name}.${field.name}"
            addViewToMap(view, field.genericType, viewMap, location)
        } catch (e: Exception) {
            logger.debug("Failed to process field: ${field.declaringClass.name}.${field.name}", e)
        }
    }

    private fun processViewMethod(method: Method, objectInstance: Any?, viewMap: MutableMap<Class<*>, ViewInfo>) {
        try {
            method.isAccessible = true
            val view = method.invoke(objectInstance) ?: return
            val location = "${method.declaringClass.name}.${method.name}()"
            addViewToMap(view, method.genericReturnType, viewMap, location)
        } catch (e: Exception) {
            logger.debug("Failed to process method: ${method.declaringClass.name}.${method.name}()", e)
        }
    }

    private fun addViewToMap(
        view: Any,
        genericType: Type,
        viewMap: MutableMap<Class<*>, ViewInfo>,
        viewLocation: String
    ) {
        val viewModelType = extractViewModelType(genericType) ?: return

        val existing = viewMap[viewModelType]
        if (existing != null) {
            throw IllegalStateException(
                "HtmlFlow: Multiple views found for ViewModel type '${viewModelType.simpleName}'. " +
                        "Existing: ${existing.location}, New: $viewLocation"
            )
        }

        viewMap[viewModelType] = ViewInfo(view, viewLocation, viewModelType)
        logger.debug("Registered view for type ${viewModelType.simpleName} at $viewLocation")
    }

    private fun extractViewModelType(genericType: Type): Class<*>? {
        return when (genericType) {
            is ParameterizedType -> {
                val typeArguments = genericType.actualTypeArguments
                if (typeArguments.isNotEmpty()) {
                    typeArguments[0] as? Class<*>
                } else null
            }
            is Class<*> -> {
                val superType = genericType.genericSuperclass
                if (superType is ParameterizedType) {
                    extractViewModelType(superType)
                } else null
            }
            else -> null
        }
    }

    private fun createRenderer(viewMap: Map<Class<*>, ViewInfo>): TemplateRenderer {
        return object : TemplateRenderer {
            override fun invoke(vm: ViewModel): String {
                try {
                    val viewInfo = findViewForModel(vm, viewMap)
                    return renderView(viewInfo.view, vm, viewInfo.location)
                } catch (e: Exception) {
                    logger.error("Failed to render view for model: ${vm::class.simpleName}", e)
                    throw e
                }
            }
        }
    }

    private fun findViewForModel(viewModel: ViewModel, viewMap: Map<Class<*>, ViewInfo>): ViewInfo {
        val viewModelClass = viewModel::class.java

        viewMap[viewModelClass]?.let { return it }

        var currentClass: Class<*>? = viewModelClass
        while (currentClass != null) {
            viewMap[currentClass]?.let { return it }
            currentClass = currentClass.superclass
        }

        for (int in viewModelClass.interfaces) {
            viewMap[int]?.let { return it }
        }

        val compatibleView = viewMap.entries
            .find { (type, _) -> type.isAssignableFrom(viewModelClass) }
            ?.value

        return compatibleView
            ?: throw IllegalArgumentException(
                "No HtmlView found for ViewModel type: ${viewModelClass.simpleName}. " +
                        "Available views: ${viewMap.keys.map { it.simpleName }}"
            )
    }

    private fun renderView(view: Any, viewModel: ViewModel, location: String): String {
        return try {
            when (view) {
                is HtmlView<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    (view as HtmlView<ViewModel>).render(viewModel)
                }
                is HtmlViewAsync<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    // HTTP4k has no async support (?), so we just get the result synchronously
                    (view as HtmlViewAsync<ViewModel>).renderAsync(viewModel).get()
                }
                else -> throw IllegalArgumentException(
                    "Unknown view type: ${view::class.simpleName} at $location"
                )
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to render view at $location for model ${viewModel::class.simpleName}", e)
        }
    }
}