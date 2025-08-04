package com.github.xmlet.htmlflow.testviews.simple

import com.github.xmlet.htmlflow.AsyncTestViewModel
import com.github.xmlet.htmlflow.AsyncTestViewModel2
import com.github.xmlet.htmlflow.SimpleTestViewModel
import com.github.xmlet.htmlflow.SimpleTestViewModel2
import htmlflow.HtmlFlow
import htmlflow.HtmlView
import htmlflow.HtmlViewAsync
import htmlflow.dyn
import htmlflow.html
import org.xmlet.htmlapifaster.body
import org.xmlet.htmlapifaster.div
import org.xmlet.htmlapifaster.h1
import org.xmlet.htmlapifaster.h2
import org.xmlet.htmlapifaster.h3
import org.xmlet.htmlapifaster.h4
import org.xmlet.htmlapifaster.p
import org.xmlet.htmlapifaster.span

/**
 * Simple test views for basic functionality testing
 */
object SimpleTestViews {
    val simpleHtmlView: HtmlView<SimpleTestViewModel> =
        HtmlFlow.view {
            it.html {
                body {
                    div {
                        attrClass("simple")
                        h1 { text("Simple View") }
                        dyn { model: SimpleTestViewModel ->
                            p {
                                text("Content: ${model.content}")
                            }
                        }
                    }
                }
            }
        }

    val asyncHtmlView: HtmlViewAsync<AsyncTestViewModel> =
        HtmlFlow.viewAsync {
            it.html {
                body {
                    div {
                        attrClass("async")
                        h3 { text("Async View") }
                        dyn { model: AsyncTestViewModel ->
                            p {
                                text("Async content: ${model.content}")
                            }
                        }
                    }
                }
            }
        }

    fun getSimpleView(): HtmlView<SimpleTestViewModel2> {
        return HtmlFlow.view {
            it.html {
                body {
                    div {
                        attrClass("method-view")
                        h2 { text("Method View") }
                        dyn { model: SimpleTestViewModel ->
                            span {
                                text("Method content: ${model.content}")
                            }
                        }
                    }
                }
            }
        }
    }

    fun getAsyncView(): HtmlViewAsync<AsyncTestViewModel2> {
        return HtmlFlow.viewAsync {
            it.html {
                body {
                    div {
                        attrClass("async-method")
                        h4 { text("Async Method View") }
                        dyn { model: AsyncTestViewModel ->
                            p {
                                text("Async method content: ${model.content}")
                            }
                        }
                    }
                }
            }
        }
    }
}
