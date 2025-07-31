package com.github.xmlet.htmlflow

import htmlflow.HtmlView
import htmlflow.HtmlViewAsync
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel

/**
 * Renderer for HtmlView that returns a [TemplateRenderer].
 *
 * The resulting [TemplateRenderer] will render the provided [ViewModel] in a
 * blocking manner.
 *
 * @throws IllegalArgumentException if the provided ViewModel is not of the expected type.
 */
fun <T : ViewModel> HtmlView<T>.renderer(): TemplateRenderer {
    return { viewModel: ViewModel ->
        try {
            @Suppress("UNCHECKED_CAST")
            this.render(viewModel as T)
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(
                "HtmlFlow: ViewModel type mismatch for view ${this::class.simpleName}", e
            )
        }
    }
}
/**
 * Renderer for HtmlViewAsync that returns a [TemplateRenderer].
 *
 * The resulting [TemplateRenderer] will render the provided [ViewModel] in a
 * blocking manner, waiting for the asynchronous rendering to complete.
 *
 * @throws IllegalArgumentException if the provided ViewModel is not of the expected type.
 */
fun <T : ViewModel> HtmlViewAsync<T>.renderer(): TemplateRenderer {
    return { viewModel: ViewModel ->
        try {
            @Suppress("UNCHECKED_CAST")
            this.renderAsync(viewModel as T).get() // No support for async rendering (?)
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(
                "HtmlFlow: ViewModel type mismatch for view ${this::class.simpleName}", e
            )
        }
    }
}