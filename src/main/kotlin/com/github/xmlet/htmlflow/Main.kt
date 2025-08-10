package com.github.xmlet.htmlflow

import htmlflow.HtmlFlow
import htmlflow.HtmlView
import htmlflow.HtmlViewAsync
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
import org.http4k.template.ViewModel
import org.http4k.template.viewModel
import org.xmlet.htmlapifaster.body
import org.xmlet.htmlapifaster.div
import org.xmlet.htmlapifaster.h2
import org.xmlet.htmlapifaster.p

data class Person(val name: String, val age: Int) : ViewModel

data class Person2(val name: String, val age: Int) : ViewModel

val personView: HtmlView<Person> =
    HtmlFlow.view {
        it.html {
            body {
                div {
                    attrClass("person-view")
                    h2 { text("Person Details") }
                    dyn { model: Person ->
                        p { text("${model.name} is ${model.age} years old") }
                    }
                }
            }
        }
    }

fun main() {
    // first, create a Renderer - this can be a Caching instance or a HotReload for development
    val renderer = HtmlFlowTemplates().HotReload()

    // first example uses a renderer to create a string
    val app: HttpHandler = {
        val viewModel = Person("Bob", 45)
        val renderedView = renderer(viewModel)
        Response(OK).body(renderedView)
    }
    println(app(Request(Method.GET, "/someUrl")))

    // the lens example uses the Body.viewModel to also set the content type, and avoid using Strings
    val viewLens = Body.viewModel(renderer, TEXT_HTML).toLens()

    val appUsingLens: HttpHandler = {
        Response(OK).with(viewLens of Person("Bob", 45))
    }

    println(appUsingLens(Request(Method.GET, "/someUrl")))

    // Extension example

    val renderer2 = personView.renderer()

    val appUsingExtension: HttpHandler = {
        val viewModel = Person("Alice", 30)

        // Intentionally using a different ViewModel type to demonstrate
        // that the renderer can use any ViewModel type to invoke the renderer
        // method, which does not make much sense in the extension case
        // as we have the type defined in HtmlView<T>.
        //
        // Ideally, this should not compile, but in this case it does
        // and throws an exception at runtime.
        val viewModel2 = Person2("Bob", 45)
        val renderedView = renderer2(viewModel)
        try {
            val renderedView2 = renderer2(viewModel2)
        } catch (e: Exception) {
            println("Error rendering view with Person2: ${e.stackTraceToString()}")
        }
        Response(OK).body(renderedView)
    }

    println(appUsingExtension(Request(Method.GET, "/someUrl")))

    // Using the extension with a lens

    val viewLens2 = Body.viewModel(renderer2, TEXT_HTML).toLens()

    val appUsingLens2: HttpHandler = {
        Response(OK).with(viewLens2 of Person("Alice", 30))
    }

    println(appUsingLens2(Request(Method.GET, "/someUrl")))

    // Strongly typed extension example

    val stronglyTypedRenderer = personView.rendererTyped<Person>()

    val appUsingStronglyTypedExtension: HttpHandler = {
        val viewModel = Person("Charlie", 25)
        val renderedView = stronglyTypedRenderer(viewModel)

        // Does not compile, since we now have strong typing
        // val viewModel2 = Person2("Dave", 35)
        // val rendereredView2 = stronglyTypedRenderer(viewModel2)

        Response(OK).body(renderedView)
    }

    println(appUsingStronglyTypedExtension(Request(Method.GET, "/someUrl")))

    // Using the strongly typed extension with a lens

    val stronglyTypedViewLens = Body.viewModel(stronglyTypedRenderer, TEXT_HTML).toLens()

    val appUsingStronglyTypedLens: HttpHandler = {
        Response(OK).with(stronglyTypedViewLens of Person("Charlie", 25))
    }

    println(appUsingStronglyTypedLens(Request(Method.GET, "/someUrl")))
}
