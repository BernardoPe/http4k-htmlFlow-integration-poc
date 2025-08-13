package com.github.xmlet.htmlflow

import com.github.xmlet.htmlflow.testviews.kdoc.DerivedVm
import com.github.xmlet.htmlflow.testviews.kdoc.PublicProfile
import com.github.xmlet.htmlflow.testviews.kdoc.UserVm
import org.http4k.template.HtmlFlowTemplates
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/** Simple integration tests that validate each KDoc example works as documented */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KDocExampleIntegrationTest {

    @Test
    fun `all KDoc examples should render successfully`() {
        val templates = HtmlFlowTemplates()
        val renderer = templates.CachingClasspath("com.github.xmlet.htmlflow.testviews.kdoc")

        // Example 1: Direct match
        val userResult = renderer(UserVm("Direct User"))
        assertContainsExpectedContent(userResult, "User Profile", "Direct User")

        // Example 2 & 3: Inheritance chain (first render populates cache, second uses cache)
        val firstDerived = renderer(DerivedVm("First Base", "First Derived"))
        assertContainsExpectedContent(firstDerived, "Base View", "First Base")

        val secondDerived = renderer(DerivedVm("Second Base", "Second Derived"))
        assertContainsExpectedContent(secondDerived, "Base View", "Second Base")

        // Example 4: Interface match
        val profileResult = renderer(PublicProfile("Interface User", "Developer"))
        assertContainsExpectedContent(profileResult, "Profile Interface View", "Interface User")
    }

    @Test
    fun `hot reload should clear caches and work correctly`() {
        val templates = HtmlFlowTemplates()

        // First, use caching renderer to populate cache
        val cachingRenderer = templates.CachingClasspath("com.github.xmlet.htmlflow.testviews.kdoc")
        val cachingResult = cachingRenderer(DerivedVm("Caching Test", "Extra"))
        assertContainsExpectedContent(cachingResult, "Base View", "Caching Test")

        // Then use hot reload (should clear caches)
        val hotReloadRenderer = templates.HotReloadClasspath("com.github.xmlet.htmlflow.testviews.kdoc")
        val hotReloadResult = hotReloadRenderer(DerivedVm("Hot Reload Test", "Extra"))
        assertContainsExpectedContent(hotReloadResult, "Base View", "Hot Reload Test")
    }

    @Test
    fun `resolution precedence should be consistent`() {
        val templates = HtmlFlowTemplates()
        val renderer = templates.CachingClasspath("com.github.xmlet.htmlflow.testviews.kdoc")

        // Test model that could potentially match multiple views
        // Should consistently use inheritance chain resolution
        val models =
            listOf(
                DerivedVm("Test 1", "Extra 1"),
                DerivedVm("Test 2", "Extra 2"),
                DerivedVm("Test 3", "Extra 3")
            )

        val results = models.map { renderer(it) }

        // All should resolve to the same view type (BaseVm view)
        results.forEach { result ->
            assertTrue(
                result.contains("Base View"),
                "All derived models should resolve to base view"
            )
        }
    }

    private fun assertContainsExpectedContent(html: String, viewTitle: String, content: String) {
        assertTrue(html.contains(viewTitle), "HTML should contain view title: $viewTitle")
        assertTrue(html.contains(content), "HTML should contain expected content: $content")
        assertTrue(html.contains("<html>"), "Should be valid HTML")
        assertTrue(html.contains("</html>"), "Should be valid HTML")
    }
}
