package com.github.xmlet.htmlflow.testviews.builder

import com.github.xmlet.htmlflow.BuilderTestViewModel
import com.github.xmlet.htmlflow.BuilderTestViewModel2
import com.github.xmlet.htmlflow.BuilderTestViewModel3
import htmlflow.HtmlFlow
import htmlflow.HtmlView
import com.github.xmlet.htmlflow.ComplexTestViewModel
import com.github.xmlet.htmlflow.SimpleTestViewModel3
import htmlflow.dyn
import htmlflow.html
import org.xmlet.htmlapifaster.body
import org.xmlet.htmlapifaster.div
import org.xmlet.htmlapifaster.h1
import org.xmlet.htmlapifaster.h2
import org.xmlet.htmlapifaster.head
import org.xmlet.htmlapifaster.li
import org.xmlet.htmlapifaster.p
import org.xmlet.htmlapifaster.title
import org.xmlet.htmlapifaster.ul

/**
 * Test views using view builder constructor pattern.
 * These classes accept HtmlFlow.Engine in their constructor to configure view rendering.
 */
val simpleBuilderView: HtmlView<BuilderTestViewModel> = HtmlFlow.view {
    it.html {
        body {
            div {
                attrClass("builder-configured")
                h2 { text("Builder Configured View") }
                dyn { model: BuilderTestViewModel ->
                    p {
                        text("Builder content: ${model.content}")
                    }
                }
            }
        }
    }
}

class BuilderConfiguredViews(private val engine: HtmlFlow.Engine) {

    val simpleBuilderView: HtmlView<BuilderTestViewModel2> = HtmlFlow.view {
        it.html {
            body {
                div {
                    attrClass("builder-configured")
                    h2 { text("Builder Configured View") }
                    dyn { model: BuilderTestViewModel2 ->
                        p {
                            text("Builder content: ${model.content}")
                        }
                    }
                }
            }
        }
    }
}

/**
 * View class with multiple constructor parameters (should be skipped during scanning)
 */
class MultiParameterViews(
    private val builder: HtmlFlow.Engine,
    private val config: String
) {

    val multiParamView: HtmlView<BuilderTestViewModel3> = builder.view {
        it.html {
            body {
                div {
                    attrClass("multi-param")
                    h2 { text("Multi Parameter View: $config") }
                    dyn { model: BuilderTestViewModel3 ->
                        p { text("Multi param: ${model.content}") }
                    }
                }
            }
        }
    }
}

/**
 * View class without constructor parameters (should work with default constructor)
 */
class NoBuilderViews {
    
    val defaultView: HtmlView<BuilderTestViewModel3> = HtmlFlow.view {
        it.html {
            body {
                div {
                    attrClass("no-builder")
                    h2 { text("No Builder View") }
                    dyn { model: BuilderTestViewModel3 ->
                        p { text("Default: ${model.content}") }
                    }
                }
            }
        }
    }
}
