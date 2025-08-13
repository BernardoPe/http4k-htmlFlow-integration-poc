package com.github.xmlet.htmlflow

import htmlflow.HtmlView
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel

/**
 * Creates a [TemplateRenderer] from an [HtmlView].
 *
 * The resulting renderer will perform synchronous rendering of the view model using the provided
 * [HtmlView] instance.
 *
 * @throws IllegalArgumentException if the provided [ViewModel] is not compatible with [T].
 */
inline fun <reified T : ViewModel> HtmlView<T>.renderer(): TemplateRenderer {
    return { viewModel: ViewModel ->
        try {
            @Suppress("UNCHECKED_CAST") this.render(viewModel as T)
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(
                "ViewModel type mismatch for view ${this::class.simpleName}. " +
                    "Expected: ${T::class.simpleName}, Got: ${viewModel::class.simpleName}",
                e,
            )
        }
    }
}
