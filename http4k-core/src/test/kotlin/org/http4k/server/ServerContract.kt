package org.http4k.server

import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import kotlinx.coroutines.runBlocking
import org.http4k.asByteBuffer
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.ACCEPTED
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK
import org.http4k.core.StreamBody
import org.http4k.core.with
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasStatus
import org.http4k.lens.binary
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Random


abstract class ServerContract(private val serverConfig: (Int) -> ServerConfig, private val client: HttpHandler,
                              private val requiredMethods: Array<Method> = Method.values()) {
    private var server: Http4kServer? = null

//    @Rule
//    @JvmField
//    var retryRule = RetryRule.LOCAL

    private val port = Random().nextInt(1000) + 8000

    private val size = 1000 * 1024
    private val random = (0 until size).map { '.' }.joinToString("")

    private val routes =
        requiredMethods.map {
            "/" + it.name bind it to HttpHandler { _: Request -> Response(OK).body(it.name) }
        }.plus(listOf(
            "/headers" bind GET to HttpHandler { _: Request ->
                Response(ACCEPTED)
                    .header("content-type", "text/plain")
            },
            "/large" bind GET to HttpHandler { Response(OK).body((0..size).map { '.' }.joinToString("")) },
            "/large" bind POST to HttpHandler { Response(OK).body((0..size).map { '.' }.joinToString("")) },
            "/stream" bind GET to HttpHandler { Response(OK).with(Body.binary(ContentType.TEXT_PLAIN).toLens() of Body("hello".asByteBuffer())) },
            "/presetlength" bind GET to HttpHandler { Response(OK).header("Content-Length", "0") },
            "/echo" bind POST to HttpHandler { req: Request -> Response(OK).body(req.bodyString()) },
            "/request-headers" bind GET to HttpHandler { request: Request -> Response(OK).body(request.headerValues("foo").joinToString(", ")) },
            "/length" bind HttpHandler { req: Request ->
                when (req.body) {
                    is StreamBody -> Response(OK).body(req.body.length.toString())
                    else -> Response(INTERNAL_SERVER_ERROR)
                }
            },
            "/uri" bind GET to HttpHandler { req: Request -> Response(OK).body(req.uri.toString()) },
            "/boom" bind GET to HttpHandler { _: Request -> throw IllegalArgumentException("BOOM!") }
        ))

    @BeforeEach
    fun before() {
        server = routes(*routes.toTypedArray()).asServer(serverConfig(port)).start()
    }

    @Test
    fun `can call an endpoint with all supported Methods`() = runBlocking {
        for (method in requiredMethods) {

            val response = client(Request(method, "http://localhost:$port/" + method.name))

            assertThat(response.status, equalTo(OK))
            if (method == Method.HEAD) assertThat(response.body, equalTo(Body.EMPTY))
            else assertThat(response.bodyString(), equalTo(method.name))
        }
    }

    @Test
    open fun `can return a large body - GET`() = runBlocking {
        val response = client(Request(GET, "http://localhost:$port/large").body("hello mum"))

        assertThat(response.status, equalTo(OK))
        assertThat(response.bodyString().length, equalTo(random.length + 1))
    }

    @Test
    open fun `can return a large body - POST`() = runBlocking {
        val response = client(Request(POST, "http://localhost:$port/large").body("hello mum"))

        assertThat(response.status, equalTo(OK))
        assertThat(response.bodyString().length, equalTo(random.length + 1))
    }

    @Test
    fun `gets the body from the request`() = runBlocking {
        val response = client(Request(POST, "http://localhost:$port/echo").body("hello mum"))

        assertThat(response.status, equalTo(OK))
        assertThat(response.bodyString(), equalTo("hello mum"))
    }

    @Test
    fun `returns headers`() = runBlocking {
        val response = client(Request(GET, "http://localhost:$port/headers"))

        assertThat(response.status, equalTo(ACCEPTED))
        assertThat(response.header("content-type"), equalTo("text/plain"))
    }

    @Test
    fun `length is set on body if it is sent`() = runBlocking {
        val response = client(Request(POST, "http://localhost:$port/length")
            .body("12345").header("Content-Length", "5"))
        response shouldMatch hasStatus(OK).and(hasBody("5"))
    }

    @Test
    fun `length is ignored on body if it not well formed`() = runBlocking {
        val response = client(Request(POST, "http://localhost:$port/length").header("Content-Length", "nonsense").body("12345"))
        response shouldMatch hasStatus(OK).and(hasBody("5"))
    }

    @Test
    fun `gets the uri from the request`() = runBlocking {
        val response = client(Request(GET, "http://localhost:$port/uri?bob=bill"))

        assertThat(response.status, equalTo(OK))
        assertThat(response.bodyString(), equalTo("/uri?bob=bill"))
    }

    @Test
    fun `endpoint that blows up results in 500`() = runBlocking {
        val response = client(Request(GET, "http://localhost:$port/boom"))

        assertThat(response.status, equalTo(INTERNAL_SERVER_ERROR))
    }

    @Test
    fun `can handle multiple request headers`() = runBlocking {
        val response = client(Request(GET, "http://localhost:$port/request-headers").header("foo", "one").header("foo", "two").header("foo", "three"))

        assertThat(response.status, equalTo(OK))
        assertThat(response.bodyString(), equalTo("one, two, three"))
    }

    @Test
    fun `deals with streaming response`() = runBlocking {
        val response = client(Request(GET, "http://localhost:$port/stream"))

        assertThat(response.status, equalTo(OK))
        assertThat(response.bodyString(), equalTo("hello"))
    }

    @Test
    fun `ok when length already set`() = runBlocking {
        val response = client(Request(GET, "http://localhost:$port/presetlength"))
        assertThat(response.status, equalTo(OK))
        assertThat(response.header("content-length"), equalTo("0"))
    }

    @Test
    fun `can start on port zero and then get the port`() = runBlocking {
        routes(*routes.toTypedArray()).asServer(serverConfig(0)).start().use {
            assertThat(client(Request(GET, "http://localhost:${it.port()}/uri")).status, equalTo(OK))
        }
    }

    @AfterEach
    fun after() {
        server?.stop()
    }

}