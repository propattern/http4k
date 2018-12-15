package org.http4k.routing.experimental

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.or
import kotlinx.coroutines.runBlocking
import org.http4k.core.ContentType
import org.http4k.core.ContentType.Companion.APPLICATION_XML
import org.http4k.core.ContentType.Companion.TEXT_HTML
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri.Companion.of
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasHeader
import org.http4k.routing.Router
import org.junit.jupiter.api.Test

abstract class ResourceLoaderContract(private val loader: Router) {

    @Test
    fun `loads existing file`() = runBlocking {
        checkContents("mybob.xml", "<xml>content</xml>", APPLICATION_XML)
    }

    @Test
    fun `loads root index file`() = runBlocking {
        checkContents("", "hello from the root index.html", TEXT_HTML)
        checkContents("/", "hello from the root index.html", TEXT_HTML)
    }

    @Test
    open fun `loads embedded index file`() = runBlocking {
        checkContents("org", "hello from the io index.html", TEXT_HTML)
        checkContents("org/", "hello from the io index.html", TEXT_HTML)
    }

    @Test
    fun `loads existing child file`() = runBlocking {
        checkContents("org/index.html", "hello from the io index.html", TEXT_HTML)
    }

    @Test
    fun `missing file`() = runBlocking {
        checkContents("notAFile", null, TEXT_HTML)
    }

    protected suspend fun checkContents(path: String, expected: String?, expectedContentType: ContentType) {
        val request = Request(Method.GET, of(path))
        if (expected == null)
            assertThat(loader.match(request), absent())
        else {
            val response = (loader.match(request)!!).invoke(request)
            assertThat(response, hasBody(expected))
            assertThat(response, hasHeader("Content-Length", expected.length.toString()) or hasHeader("Content-Length", absent()))
            assertThat(response, hasHeader("Content-Type", expectedContentType.withNoDirective().toHeaderValue()))
        }
    }
}

