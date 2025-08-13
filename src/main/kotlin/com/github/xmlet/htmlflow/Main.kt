package com.github.xmlet.htmlflow

import htmlflow.HtmlFlow
import htmlflow.HtmlView
import htmlflow.dyn
import htmlflow.html
import org.http4k.core.Body
import org.http4k.core.ContentType.Companion.TEXT_HTML
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.template.HtmlFlowTemplates
import org.http4k.template.ViewModel
import org.http4k.template.viewModel
import org.xmlet.htmlapifaster.body
import org.xmlet.htmlapifaster.div
import org.xmlet.htmlapifaster.h2
import org.xmlet.htmlapifaster.p

data class Person(val name: String, val age: Int) : ViewModel

val personView: HtmlView<Person> =
    HtmlFlow.view {
        it.html {
            body {
                div {
                    attrClass("person-view")
                    h2 { text("Person Details") }
                    dyn { model: Person -> p { text("${model.name} is ${model.age} years old") } }
                }
            }
        }
    }

fun main() {
    // first, create a Renderer - this can be a Caching instance or a HotReload for development
    val renderer = HtmlFlowTemplates().CachingClasspath()

    // first example uses a renderer to create a string
    val app: HttpHandler = {
        val viewModel = Person("Bob", 45)
        val renderedView = renderer(viewModel)
        Response(OK).body(renderedView)
    }

    println(app(Request(Method.GET, "/someUrl")))

    // the lens example uses the Body.viewModel to also set the content type, and avoid using Strings
    val viewLens = Body.viewModel(renderer, TEXT_HTML).toLens()

    val appUsingLens: HttpHandler = { Response(OK).with(viewLens of Person("Bob", 45)) }

    println(appUsingLens(Request(Method.GET, "/someUrl")))

    // Extension example
    val renderer2 = personView.renderer()

    val appUsingExtension: HttpHandler = {
        val viewModel = Person("Alice", 30)
        val renderedView = renderer2(viewModel)
        Response(OK).body(renderedView)
    }

    println(appUsingExtension(Request(Method.GET, "/someUrl")))

    // Using the extension with a lens
    val viewLens2 = Body.viewModel(renderer2, TEXT_HTML).toLens()

    val appUsingLens2: HttpHandler = { Response(OK).with(viewLens2 of Person("Alice", 30)) }

    println(appUsingLens2(Request(Method.GET, "/someUrl")))
}
