package org.http4k.core

import org.http4k.routing.RoutingHttpHandler

interface HttpHandler {
    suspend operator fun invoke(request: Request): Response

    companion object {
        operator fun invoke(fn: suspend (Request) -> Response): HttpHandler = object : HttpHandler {
            override suspend operator fun invoke(request: Request): Response = fn(request)
        }
    }
}

interface Filter {
    suspend operator fun invoke(next: HttpHandler): HttpHandler

    companion object {
        operator fun invoke(fn: suspend (HttpHandler) -> HttpHandler): Filter = object : Filter {
            override suspend operator fun invoke(next: HttpHandler): HttpHandler = fn(next)
        }
    }
}

val Filter.Companion.NoOp: Filter get() = Filter { next -> HttpHandler { next(it) } }

fun Filter.then(fn: suspend (Request) -> Response) = then(HttpHandler(fn))

fun Filter.then(next: Filter): Filter = Filter { this(next(it)) }

fun Filter.then(next: HttpHandler): HttpHandler = HttpHandler { this(next)(it) }

fun Filter.then(routingHttpHandler: RoutingHttpHandler): RoutingHttpHandler = routingHttpHandler.withFilter(this)