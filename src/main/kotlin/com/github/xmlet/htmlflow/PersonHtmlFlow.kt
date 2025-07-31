package com.github.xmlet.htmlflow

import htmlflow.HtmlFlow
import htmlflow.HtmlView
import htmlflow.dyn
import htmlflow.html
import org.xmlet.htmlapifaster.body
import org.xmlet.htmlapifaster.div
import org.xmlet.htmlapifaster.h1
import org.xmlet.htmlapifaster.head
import org.xmlet.htmlapifaster.meta
import org.xmlet.htmlapifaster.p


object PersonHtmlFlow {

    val htmlFlowTemplatePerson: HtmlView<Person> =
        HtmlFlow.view<Person> { view ->
            view
                .html {
                    attrLang("en-us")
                    head {
                        meta { attrCharset("UTF-8") }
                        meta { attrName("viewport").attrContent("width=device-width, initial-scale=1.0") }
                        meta { addAttr("http-equiv", "X-UA-Compatible").attrContent("IE=Edge") }
                    }
                    body {
                        div {
                           dyn { model: Person ->
                                div {
                                    attrClass("container")
                                    h1 { text(model.name) }
                                    p { text("Age: ${model.age}") }
                                }
                           }
                        }
                    }
                }
        }.threadSafe()
}
