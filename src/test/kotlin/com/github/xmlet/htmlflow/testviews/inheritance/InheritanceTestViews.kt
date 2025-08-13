package com.github.xmlet.htmlflow.testviews.inheritance

import com.github.xmlet.htmlflow.BaseTestViewModel
import com.github.xmlet.htmlflow.TestViewModelInterface
import htmlflow.HtmlFlow
import htmlflow.HtmlView
import htmlflow.dyn
import htmlflow.html
import org.xmlet.htmlapifaster.body
import org.xmlet.htmlapifaster.div
import org.xmlet.htmlapifaster.h2
import org.xmlet.htmlapifaster.p

/** Test views for inheritance and interface compatibility testing */
class InheritanceTestViews {
    val baseView: HtmlView<BaseTestViewModel> =
        HtmlFlow.view {
            it.html {
                body {
                    div {
                        attrClass("base-view")
                        h2 { text("Base View") }
                        dyn { model: BaseTestViewModel -> p { text("Base: ${model.baseContent}") } }
                    }
                }
            }
        }

    val interfaceView: HtmlView<TestViewModelInterface> =
        HtmlFlow.view {
            it.html {
                body {
                    div {
                        attrClass("interface-view")
                        h2 { text("Interface View") }
                        dyn { model: TestViewModelInterface ->
                            p { text("Interface: ${model.interfaceContent}") }
                        }
                    }
                }
            }
        }
}
