package io.github.wrightdk.repertory.data.remote

import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

/** Reads a saved TMDB response from `src/test/resources/tmdb/`. */
fun fixture(name: String): String =
    checkNotNull(FakeTmdbServer::class.java.classLoader?.getResource("tmdb/$name")) {
        "Missing fixture tmdb/$name"
    }.readText()

/**
 * MockWebServer standing in for `https://api.themoviedb.org/3/`. Routes are keyed
 * by path without the `/3` prefix, e.g. `/person/6817`. A route can hold a queue
 * of responses (served in order, the last one repeating). Unknown paths get
 * TMDB's 404 body.
 */
class FakeTmdbServer : Closeable {
    private val server = MockWebServer()
    private val routes = ConcurrentHashMap<String, ArrayDeque<MockResponse>>()
    val requests: MutableList<RecordedRequest> = java.util.Collections.synchronizedList(mutableListOf())

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.url.encodedPath.removePrefix("/3")
                val queue = routes[path] ?: return json(404, fixture("error_404.json"))
                return synchronized(queue) { if (queue.size > 1) queue.removeFirst() else queue.first() }
            }
        }
        server.start()
    }

    val baseUrl get() = server.url("/3/")

    fun service(apiKey: String = TEST_KEY): TmdbService =
        TmdbClient.createService(keyProvider = { apiKey }, baseUrl = baseUrl)

    fun route(path: String, vararg responses: MockResponse) {
        routes[path] = ArrayDeque(responses.toList())
    }

    fun routeFixture(path: String, fixtureName: String) = route(path, json(200, fixture(fixtureName)))

    fun requestCount(path: String): Int = requests.count { it.url.encodedPath == "/3$path" }

    override fun close() = server.close()

    companion object {
        const val TEST_KEY = "test-key"

        fun json(code: Int, body: String, vararg headers: Pair<String, String>): MockResponse =
            MockResponse.Builder()
                .code(code)
                .addHeader("Content-Type", "application/json;charset=utf-8")
                .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
                .body(body)
                .build()
    }
}
