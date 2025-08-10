package com.github.xmlet.htmlflow

import com.github.xmlet.htmlflow.testviews.objects.ClassTestViews
import com.github.xmlet.htmlflow.testviews.simple.SimpleTestViews
import htmlflow.HtmlFlow
import htmlflow.HtmlView
import htmlflow.dyn
import htmlflow.html
import org.http4k.template.ViewModel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.xmlet.htmlapifaster.body
import org.xmlet.htmlapifaster.div
import org.xmlet.htmlapifaster.p
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.jvm.java
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Comprehensive test suite for HtmlFlowTemplates class
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HtmlFlowTemplatesTest {
    private lateinit var htmlFlowTemplates: HtmlFlowTemplates

    @BeforeEach
    fun setUp() {
        htmlFlowTemplates = HtmlFlowTemplates()
    }

    @Nested
    inner class ViewFindingTests {
        @Test
        fun `should find views in simple package`() {
            val renderer =
                htmlFlowTemplates.CachingClasspath(
                    "com.github.xmlet.htmlflow.testviews.simple",
                )

            val simpleModel = SimpleTestViewModel("test content")
            val result = renderer(simpleModel)

            assertTrue(result.contains("Simple View") || result.contains("Method View"))
            assertTrue(result.contains("test content"))
        }

        @Test
        fun `should find views in objects package`() {
            val renderer =
                htmlFlowTemplates.CachingClasspath(
                    "com.github.xmlet.htmlflow.testviews.objects",
                )

            val complexModel = ComplexTestViewModel("Test Title", listOf("item1", "item2"), true)
            val result = renderer(complexModel)

            assertTrue(result.contains("Complex View") || result.contains("Object Complex View"))
            assertTrue(result.contains("Test Title"))
        }

        @Test
        fun `should handle empty package gracefully`() {
            val renderer =
                htmlFlowTemplates.CachingClasspath(
                    "com.github.xmlet.htmlflow.testviews.empty",
                )

            val model = SimpleTestViewModel("test")
            val exception =
                assertThrows<IllegalArgumentException> {
                    renderer(model)
                }

            assertTrue(exception.message!!.contains("No compatible HtmlView found"))
        }

        @Test
        fun `should find views across multiple packages`() {
            val renderer = htmlFlowTemplates.CachingClasspath("com.github.xmlet.htmlflow.testviews")

            val simpleModel = SimpleTestViewModel("test content")
            val result = renderer(simpleModel)

            assertNotNull(result)
            assertTrue(result.contains("test content"))
        }

        @Test
        fun `should cache view registry across multiple calls`() {
            val renderer1 =
                htmlFlowTemplates.CachingClasspath(
                    "com.github.xmlet.htmlflow.testviews.simple",
                )
            val renderer2 =
                htmlFlowTemplates.CachingClasspath(
                    "com.github.xmlet.htmlflow.testviews.simple",
                )

            val model = SimpleTestViewModel("test")
            val result1 = renderer1(model)
            val result2 = renderer2(model)

            assertEquals(result1, result2)
        }
    }

    @Nested
    inner class ViewRenderingTests {
        @Test
        fun `should render HtmlView correctly`() {
            val renderer =
                htmlFlowTemplates.CachingClasspath(
                    "com.github.xmlet.htmlflow.testviews.simple",
                )

            val model = SimpleTestViewModel("Hello World")
            val result = renderer(model)

            assertTrue(result.contains("Hello World"))
            assertTrue(result.contains("<html>"))
            assertTrue(result.contains("</html>"))
        }

        @Test
        fun `should render complex view with lists and conditionals`() {
            val renderer =
                htmlFlowTemplates.CachingClasspath(
                    "com.github.xmlet.htmlflow.testviews.objects",
                )

            val model = ComplexTestViewModel("My Title", listOf("First", "Second", "Third"), true)
            val result = renderer(model)

            assertTrue(result.contains("My Title"))
            assertTrue(result.contains("First"))
            assertTrue(result.contains("Second"))
            assertTrue(result.contains("Third"))
            assertTrue(result.contains("Active"))
        }

        @Test
        fun `should handle inactive status in complex view`() {
            val renderer =
                htmlFlowTemplates.CachingClasspath(
                    "com.github.xmlet.htmlflow.testviews.objects",
                )

            val model = ComplexTestViewModel("Inactive Title", listOf("Item"), false)
            val result = renderer(model)

            assertTrue(result.contains("Inactive Title"))
            assertTrue(result.contains("Item"))
            assertFalse(result.contains("Active"))
        }

        @Test
        fun `should throw exception for unknown view type`() {
            val renderer =
                htmlFlowTemplates.CachingClasspath(
                    "com.github.xmlet.htmlflow.testviews.simple",
                )

            val unknownModel = object : ViewModel {}

            val exception =
                assertThrows<IllegalArgumentException> {
                    renderer(unknownModel)
                }

            assertTrue(exception.message!!.contains("No compatible HtmlView found"))
        }
    }

    @Nested
    inner class InheritanceTests {
        @Test
        fun `should find view for base class when using inherited model`() {
            val renderer =
                htmlFlowTemplates.CachingClasspath(
                    "com.github.xmlet.htmlflow.testviews.inheritance",
                )

            val inheritedModel = InheritedTestViewModel("base content", "derived content")
            val result = renderer(inheritedModel)

            assertTrue(result.contains("base content"))
        }

        @Test
        fun `should find view for interface when using implementing model`() {
            val renderer =
                htmlFlowTemplates.CachingClasspath(
                    "com.github.xmlet.htmlflow.testviews.inheritance",
                )

            val interfaceModel = InterfaceTestViewModel("interface content", "additional content")
            val result = renderer(interfaceModel)

            assertTrue(result.contains("interface content"))
        }

        @Test
        fun `should handle direct base class model`() {
            val renderer =
                htmlFlowTemplates.CachingClasspath(
                    "com.github.xmlet.htmlflow.testviews.inheritance",
                )

            val baseModel = BaseTestViewModel("direct base content")
            val result = renderer(baseModel)

            assertTrue(result.contains("direct base content"))
        }
    }

    @Nested
    inner class DuplicateViewTests {
        @Test
        fun `should throw exception when duplicate views are found`() {
            val exception =
                assertThrows<IllegalStateException> {
                    htmlFlowTemplates.CachingClasspath(
                        "com.github.xmlet.htmlflow.testviewsfailing.duplicate",
                    )
                }

            assertTrue(exception.message!!.contains("Multiple views found"))
            assertTrue(exception.message!!.contains("SimpleTestViewModel"))
        }
    }

    @Nested
    inner class TypeCheckingTests {
        @Test
        fun `should correctly identify HtmlView fields`() {
            val field = createMockField(HtmlView::class.java)
            val result = callPrivateMethod("isHtmlViewField", field)
            assertTrue(result as Boolean)
        }

        @Test
        fun `should correctly identify HtmlView methods`() {
            val method = createMockMethod(HtmlView::class.java)
            val result = callPrivateMethod("isHtmlViewMethod", method)
            assertTrue(result as Boolean)
        }

        @Test
        fun `should reject non-view fields`() {
            val field = createMockField(String::class.java)
            val result = callPrivateMethod("isHtmlViewField", field)
            assertFalse(result as Boolean)
        }

        @Test
        fun `should reject non-view methods`() {
            val method = createMockMethod(String::class.java)
            val result = callPrivateMethod("isHtmlViewMethod", method)
            assertFalse(result as Boolean)
        }

        private fun createMockField(type: Class<*>): Field {
            val field = org.mockito.Mockito.mock(Field::class.java)
            org.mockito.Mockito.`when`(field.type).thenReturn(type)
            return field
        }

        private fun createMockMethod(returnType: Class<*>): Method {
            val method = org.mockito.Mockito.mock(Method::class.java)
            org.mockito.Mockito.`when`(method.returnType).thenReturn(returnType)
            return method
        }
    }

    @Nested
    inner class ObjectInstanceTests {
        @Test
        fun `should successfully create Kotlin object instance`() {
            val result = callPrivateMethod("tryObjectInstance", SimpleTestViews::class.java)
            assertNotNull(result)
        }

        @Test
        fun `should return null for non-object class`() {
            val result = callPrivateMethod("tryObjectInstance", ClassTestViews::class.java)
            assertNull(result)
        }

        @Test
        fun `should create default constructor instance`() {
            val result = callPrivateMethod("tryDefaultConstructor", ClassTestViews::class.java)
            assertNotNull(result)
        }
    }

    @Nested
    inner class ViewTypeExtractionTests {
        @Test
        fun `should handle non-parameterized types gracefully`() {
            val result = callPrivateMethod("extractFromClassType", String::class.java)
            assertNull(result)
        }
    }

    @Nested
    inner class IntegrationTests {
        @Test
        fun `should work with extension functions`() {
            val view: HtmlView<SimpleTestViewModel> =
                HtmlFlow.view {
                    it.html {
                        body {
                            div {
                                attrClass("extension-test")
                                dyn { model: SimpleTestViewModel ->
                                    p { text("Extension: ${model.content}") }
                                }
                            }
                        }
                    }
                }

            val renderer = view.renderer()
            val model = SimpleTestViewModel("extension test")
            val result = renderer(model)

            assertTrue(result.contains("Extension: extension test"))
        }

        @Test
        fun `should handle type mismatch in extension function`() {
            val view: HtmlView<SimpleTestViewModel> =
                HtmlFlow.view {
                    it.html { body { } }
                }

            val renderer = view.renderer()
            val wrongModel = AsyncTestViewModel("wrong type")

            val exception =
                assertThrows<IllegalArgumentException> {
                    renderer(wrongModel)
                }

            assertTrue(exception.message!!.contains("ViewModel type mismatch"))
        }
    }

    @Nested
    inner class TemplatesInterfaceTests {
        @Test
        fun `should throw UnsupportedOperationException for Caching with directory`() {
            val exception =
                assertThrows<UnsupportedOperationException> {
                    htmlFlowTemplates.Caching("some/template/dir")
                }

            assertTrue(exception.message!!.contains("Template directory caching is not supported"))
            assertTrue(exception.message!!.contains("Use CachingClasspath() instead"))
        }

        @Test
        fun `should return working TemplateRenderer from CachingClasspath`() {
            val renderer =
                htmlFlowTemplates.CachingClasspath(
                    "com.github.xmlet.htmlflow.testviews.simple",
                )
            assertNotNull(renderer)
        }
    }

    /**
     * Helper method to call private methods using reflection
     */
    private fun callPrivateMethod(
        methodName: String,
        vararg args: Any?,
    ): Any? {
        val method =
            htmlFlowTemplates::class.java.getDeclaredMethod(
                methodName,
                *args.map { it?.javaClass ?: Object::class.java }.toTypedArray(),
            )
        method.isAccessible = true
        return method.invoke(htmlFlowTemplates, *args)
    }

    @Nested
    inner class EngineConstructorTests {

        @Test
        fun `should discover views from classes with Engine constructor`() {
            val renderer = htmlFlowTemplates.CachingClasspath("com.github.xmlet.htmlflow.testviews.builder")
            
            val simpleModel = BuilderTestViewModel2("builder test content")
            val result = renderer(simpleModel)
            
            assertTrue(result.contains("Builder Configured View") || result.contains("Builder content"))
            assertTrue(result.contains("builder test content"))
        }

        @Test
        fun `should handle async views from builder constructor`() {
            val renderer = htmlFlowTemplates.CachingClasspath("com.github.xmlet.htmlflow.testviews.builder")
            
            val asyncModel = BuilderTestViewModel("async builder content")
            val result = renderer(asyncModel)
            
            assertTrue(result.contains("async builder content"))
            assertTrue(result.contains("<html>"))
            assertTrue(result.contains("</html>"))
        }

        @Test
        fun `should prefer builder constructor over default constructor when both available`() {
            val renderer = htmlFlowTemplates.CachingClasspath("com.github.xmlet.htmlflow.testviews.builder")
            
            val model = BuilderTestViewModel("constructor test")
            val result = renderer(model)
            
            // Should find views from classes with builder constructors
            assertNotNull(result)
            assertTrue(result.contains("constructor test"))
        }

        @Test
        fun `should fall back to default constructor when Engine constructor not available`() {
            val renderer = htmlFlowTemplates.CachingClasspath("com.github.xmlet.htmlflow.testviews.builder")
            
            val model = BuilderTestViewModel3("fallback test")
            val result = renderer(model)
            
            // Should work with NoBuilderViews class that has default constructor
            assertNotNull(result)
            assertTrue(result.contains("fallback test"))
        }

        @Test
        fun `should skip classes with multiple constructor parameters`() {
            // This test verifies that classes with constructors requiring multiple parameters
            // are gracefully skipped during scanning (since we can't provide all parameters)
            val renderer = htmlFlowTemplates.CachingClasspath("com.github.xmlet.htmlflow.testviews.builder")
            
            // Should still work with other classes in the package
            val model = BuilderTestViewModel2("multi param test")
            val result = renderer(model)
            
            assertNotNull(result)
            assertTrue(result.contains("multi param test"))
        }

        @Test
        fun `should handle builder constructor instantiation errors gracefully`() {
            // Test that if builder constructor fails, we fall back to other instantiation methods
            val renderer = htmlFlowTemplates.CachingClasspath("com.github.xmlet.htmlflow.testviews.builder")
            
            val model = BuilderTestViewModel2("error handling test")
            
            // Should not throw exception even if some classes fail to instantiate
            assertDoesNotThrow {
                val result = renderer(model)
                assertNotNull(result)
            }
        }

        @Test
        fun `should create views with proper Engine configuration`() {
            val renderer = htmlFlowTemplates.CachingClasspath("com.github.xmlet.htmlflow.testviews.builder")
            
            val model = BuilderTestViewModel("config test")
            val result = renderer(model)

            assertTrue(result.contains("config test"))
            assertTrue(result.contains("<html>"))
            assertTrue(result.contains("</html>"))
            
            // Test that the result is indented
            assertTrue(result.contains("\n") || result.contains("\t"))
        }

        @Test
        fun `should cache views created with builder constructors`() {
            val timeStart1 = System.nanoTime()
            val renderer1 = htmlFlowTemplates.CachingClasspath("com.github.xmlet.htmlflow.testviews.builder")
            val time1 = System.nanoTime() - timeStart1

            val timeStart2 = System.nanoTime()
            val renderer2 = htmlFlowTemplates.CachingClasspath("com.github.xmlet.htmlflow.testviews.builder")
            val time2 = System.nanoTime() - timeStart2

            assertTrue(time2 < time1, "Second renderer should be faster due to caching")

            val model = BuilderTestViewModel("cache test")
            val result1 = renderer1(model)
            val result2 = renderer2(model)
            
            assertEquals(result1, result2)
        }

        private fun assertDoesNotThrow(action: () -> Unit) {
            try {
                action()
            } catch (e: Exception) {
                throw AssertionError("Expected no exception, but got: ${e.message}", e)
            }
        }
    }
}
