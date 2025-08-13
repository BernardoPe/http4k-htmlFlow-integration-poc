package com.github.xmlet.htmlflow.testviewsfailing.duplicate

import com.github.xmlet.htmlflow.SimpleTestViewModel
import htmlflow.HtmlFlow
import htmlflow.HtmlView
import htmlflow.dyn
import htmlflow.html
import org.xmlet.htmlapifaster.body
import org.xmlet.htmlapifaster.div
import org.xmlet.htmlapifaster.h2
import org.xmlet.htmlapifaster.p

/** Duplicate test views for testing duplicate detection */
object FirstDuplicateViews {
    val duplicateView: HtmlView<SimpleTestViewModel> =
        HtmlFlow.view {
            it.html {
                body {
                    div {
                        attrClass("first-duplicate")
                        h2 { text("First Duplicate") }
                        dyn { model: SimpleTestViewModel -> p { text("First: ${model.content}") } }
                    }
                }
            }
        }
}

object SecondDuplicateViews {
    val duplicateView: HtmlView<SimpleTestViewModel> =
        HtmlFlow.view {
            it.html {
                body {
                    div {
                        attrClass("second-duplicate")
                        h2 { text("Second Duplicate") }
                        dyn { model: SimpleTestViewModel -> p { text("Second: ${model.content}") } }
                    }
                }
            }
        }
}
