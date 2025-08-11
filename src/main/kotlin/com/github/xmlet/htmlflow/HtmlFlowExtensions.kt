package com.github.xmlet.htmlflow

import htmlflow.HtmlFlow
import htmlflow.HtmlTemplate
import htmlflow.HtmlView
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
import org.http4k.template.ViewModel
import org.http4k.websocket.WsMessage

/**
 * Creates a [TemplateRenderer] from an [HtmlView].
 *
 * The resulting renderer will perform synchronous rendering of the view model
 * using the provided [HtmlView] instance.
 *
 * @throws IllegalArgumentException if the provided [ViewModel] is not compatible with [T].
 */
inline fun <reified T : ViewModel> HtmlView<T>.renderer(): TemplateRenderer {
    return { viewModel: ViewModel ->
        try {
            @Suppress("UNCHECKED_CAST") this.render(viewModel as T)
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(
                "ViewModel type mismatch for view ${this::class.simpleName}. " + "Expected: ${T::class.simpleName}, Got: ${viewModel::class.simpleName}",
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

fun <T : ViewModel> WsMessage.Companion.viewModel(renderer: TypedTemplateRenderer<T>) = string().map<T>({
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

fun <T : ViewModel> HtmlView<T>.rendererTyped(
    caching: Boolean = true, threadSafe: Boolean = false, indented: Boolean = true
): TypedTemplateRenderer<T> {
    val templateField = this::class.java.getDeclaredField("template").apply { isAccessible = true }
    val template = templateField.get(this) as HtmlTemplate

    val engine = HtmlFlow.ViewFactory.builder().preEncoding(caching).threadSafe(threadSafe).indented(indented).build()

    val preConfiguredView = engine.view<T>(template)

    return object : TypedTemplateRenderer<T> {
        override fun invoke(viewModel: T): String {
            return preConfiguredView.render(viewModel)
        }
    }
}
