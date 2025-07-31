package com.github.xmlet.htmlflow

import org.http4k.core.Body
import org.http4k.core.ContentType.Companion.TEXT_HTML
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.template.ViewModel
import org.http4k.template.viewModel

data class Person(val name: String, val age: Int) : ViewModel

fun main() {

    val renderer = HtmlFlowTemplates().CachingClasspath()

    // first example uses a renderer to create a string
    val app: HttpHandler = {
        val viewModel = Person("Bob", 45)
        val renderedView = renderer(viewModel)
        Response(OK).body(renderedView)
    }
    println(app(Request(GET, "/someUrl")))

    // the lens example uses the Body.viewModel to also set the content type, and avoid using Strings
    val viewLens = Body.viewModel(renderer, TEXT_HTML).toLens()

    val appUsingLens: HttpHandler = {
        Response(OK).with(viewLens of Person("Bob", 45))
    }

    println(appUsingLens(Request(GET, "/someUrl")))

    println("-----------------------------------")
    // Second Example uses HtmlView directly

    val renderer2 = PersonHtmlFlow.htmlFlowTemplatePerson.renderer()

    // first example uses a renderer to create a string
    val app2: HttpHandler = {
        val viewModel = Person("Bob", 45)
        val renderedView = renderer2(viewModel)
        Response(OK).body(renderedView)
    }
    println(app2(Request(GET, "/someUrl")))

    // the lens example uses the Body.viewModel to also set the content type, and avoid using Strings
    val viewLens2 = Body.viewModel(renderer2, TEXT_HTML).toLens()

    val appUsingLens2: HttpHandler = {
        Response(OK).with(viewLens2 of Person("Bob", 45))
    }

    println(appUsingLens2(Request(GET, "/someUrl")))
}