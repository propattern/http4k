package org.http4k.core

import org.http4k.routing.RoutingHttpHandler

typealias RespondAsync = suspend (Request) -> Response

interface HttpHandler {
    suspend operator fun invoke(request: Request): Response

    companion object {
        operator fun invoke(fn: RespondAsync) = object : HttpHandler {
            override suspend operator fun invoke(request: Request): Response = fn(request)
        }
    }
}

interface Filter {
    suspend operator fun invoke(next: HttpHandler): HttpHandler

    companion object {
        operator fun invoke(fn: suspend (RespondAsync) -> RespondAsync) = object : Filter {
            override suspend operator fun invoke(next: HttpHandler) = HttpHandler(fn(next::invoke))
        }
    }
}

val Filter.Companion.NoOp: Filter get() = Filter { next -> { next(it) } }

fun Filter.then(fn: RespondAsync) = then(HttpHandler(fn))

fun Filter.then(next: Filter) = Filter { this(next(HttpHandler(it)))::invoke }

fun Filter.then(next: HttpHandler) = HttpHandler { this(next)(it) }

fun Filter.then(routingHttpHandler: RoutingHttpHandler) = routingHttpHandler.withFilter(this)