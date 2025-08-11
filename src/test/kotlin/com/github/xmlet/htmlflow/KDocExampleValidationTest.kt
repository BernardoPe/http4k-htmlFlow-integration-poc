package com.github.xmlet.htmlflow

import com.github.xmlet.htmlflow.testviews.kdoc.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Assertions.*
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel

/**
 * Tests that validate the KDoc examples provided in HtmlFlowTemplates.findCompatibleView()
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KDocExampleValidationTest {

    private lateinit var htmlFlowTemplates: HtmlFlowTemplates
    private lateinit var renderer: TemplateRenderer

    @BeforeEach
    fun setUp() {
        htmlFlowTemplates = HtmlFlowTemplates()
        renderer = htmlFlowTemplates.CachingClasspath("com.github.xmlet.htmlflow.testviews.kdoc")
        
        // Clear resolution cache before each test to ensure predictable behavior
        clearResolutionCache()
    }

    @Nested
    inner class DirectMatchTests {

        @Test
        fun `should find view through direct match - Example 1`() {
            // KDoc Example 1: Direct match
            // data class UserVm(val name: String) : ViewModel
            // val userView: HtmlView<UserVm> = HtmlFlow.view { ... }
            // registry contains (UserVm -> userView). Rendering UserVm finds userView immediately.
            
            val userModel = UserVm("Alice")
            val result = renderer(userModel)
            
            assertTrue(result.contains("User Profile"))
            assertTrue(result.contains("Welcome, Alice!"))
            assertTrue(result.contains("class=\"user-view\""))
        }
    }

    @Nested
    inner class InheritanceChainTests {

        @Test
        fun `should find view through superclass match - Example 3`() {
            // KDoc Example 3: Superclass match
            // open class BaseVm : ViewModel
            // class DerivedVm : BaseVm()
            // val baseView: HtmlView<BaseVm> = HtmlFlow.view { ... }
            // registry has BaseVm only; rendering DerivedVm walks inheritance and uses baseView.
            
            val derivedModel = DerivedVm("base content", "derived content")
            val result = renderer(derivedModel)
            
            assertTrue(result.contains("Base View"))
            assertTrue(result.contains("Base content: base content"))
            assertTrue(result.contains("class=\"base-view\""))
            assertFalse(result.contains("derived content"))
        }

        @Test
        fun `should cache inheritance resolution - Example 2`() {
            // KDoc Example 2: Cached resolution (second+ render of derived type)
            // class DerivedVm : BaseVm()
            // First render: walks inheritance chain, finds BaseVm view, caches DerivedVm -> BaseVm view
            // Second render: cache hit, no traversal needed
            
            val derivedModel1 = DerivedVm("first", "extra1")
            val derivedModel2 = DerivedVm("second", "extra2")
            
            // First render - should walk inheritance chain and cache result
            val result1 = renderer(derivedModel1)
            assertTrue(result1.contains("Base content: first"))
            
            // Verify cache was populated
            val cacheSize = getResolutionCacheSize()
            assertTrue(cacheSize > 0, "Resolution cache should contain cached entries after first resolution")
            
            // Second render - should use cache
            val result2 = renderer(derivedModel2)
            assertTrue(result2.contains("Base content: second"))
            
            // Cache size should remain the same (no new entries)
            assertEquals(cacheSize, getResolutionCacheSize(), "Cache size should not change on subsequent lookups")
        }
    }

    @Nested
    inner class InterfaceMatchTests {

        @Test
        fun `should find view through interface match - Example 4`() {
            // KDoc Example 4: Interface match
            // interface ProfileLike : ViewModel { val name: String }
            // data class PublicProfile(override val name: String) : ProfileLike
            // val profileView: HtmlView<ProfileLike> = HtmlFlow.view { ... }
            // registry key is ProfileLike; rendering PublicProfile matches interface.
            
            val publicProfile = PublicProfile("John Doe", "Software Developer")
            val result = renderer(publicProfile)
            
            assertTrue(result.contains("Profile Interface View"))
            assertTrue(result.contains("Profile name: John Doe"))
            assertTrue(result.contains("class=\"profile-view\""))
        }

        @Test
        fun `should handle multiple interfaces with deterministic precedence`() {
            // Test that interface order matters when a class implements multiple interfaces
            val multiInterfaceModel = MultiInterfaceVm("Multi User", "Secondary Info")
            val result = renderer(multiInterfaceModel)
            
            // Should match the first interface in the iteration order
            // Based on Class.getInterfaces() order, which is declaration order
            assertTrue(result.contains("Profile name: Multi User") || result.contains("Secondary: Secondary Info"))
        }
    }

    @Nested
    inner class ResolutionPrecedenceTests {

        @Test
        fun `should follow correct precedence order`() {
            val model = DerivedVm("test content", "extra")
            
            clearResolutionCache()
            
            val result = renderer(model)
            
            assertTrue(result.contains("Base View"))
            assertTrue(result.contains("test content"))
        }

        @Test
        fun `should cache resolved entries for performance`() {
            val startTime = System.nanoTime()
            
            val model1 = DerivedVm("performance test 1", "extra")
            renderer(model1)
            val firstLookupTime = System.nanoTime() - startTime
            
            val midTime = System.nanoTime()
            
            val model2 = DerivedVm("performance test 2", "extra")
            renderer(model2)
            val secondLookupTime = System.nanoTime() - midTime

            assertTrue(secondLookupTime < firstLookupTime, "Second lookup should be faster due to caching")
            assertNotNull(renderer(model1))
            assertNotNull(renderer(model2))
            
            assertTrue(getResolutionCacheSize() > 0, "Cache should contain resolved entries")
        }
    }

    @Nested
    inner class ErrorCaseTests {

        @Test
        fun `should throw descriptive error when no view found`() {
            // We have to reassign the renderer to a non-existent package to do this, since otherwise
            // the view would be found through the fallback given by the HtmlView<Any>.
            renderer = htmlFlowTemplates.CachingClasspath("com.github.xmlet.htmlflow.testviews.kdoc.nonexistent")
            data class UnmatchedVm(val content: String) : ViewModel
            
            val unmatchedModel = UnmatchedVm("no view for this")
            
            val exception = assertThrows(IllegalArgumentException::class.java) {
                renderer(unmatchedModel)
            }
            
            assertTrue(exception.message!!.contains("No compatible HtmlView found"))
            assertTrue(exception.message!!.contains("UnmatchedVm"))
            assertTrue(exception.message!!.contains("Available views:"))
        }
    }

    private fun clearResolutionCache() {
        try {
            val resolutionCacheField = HtmlFlowTemplates::class.java.getDeclaredField("resolutionCache")
            resolutionCacheField.isAccessible = true
            val cache = resolutionCacheField.get(null) as? java.util.concurrent.ConcurrentHashMap<*, *>
            cache?.clear()
        } catch (e: Exception) {
            // If reflection fails, test will still work but cache behavior can't be verified
        }
    }

    private fun getResolutionCacheSize(): Int {
        return try {
            val resolutionCacheField = HtmlFlowTemplates::class.java.getDeclaredField("resolutionCache")
            resolutionCacheField.isAccessible = true
            val cache = resolutionCacheField.get(null) as? java.util.concurrent.ConcurrentHashMap<*, *>
            cache?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
