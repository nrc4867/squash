package dev.chieppa.squash.screens.look.recipe.server

import dev.chieppa.squash.screens.look.recipe.RecipeArgs
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RecipeServerTest {
    private val workers = Executors.newCachedThreadPool()
    private val scope = CoroutineScope(SupervisorJob() + workers.asCoroutineDispatcher())
    private val servers = mutableListOf<RecipeServer>()
    private val root = File(System.getProperty("java.io.tmpdir")).absoluteFile.path.replace('\\', '/')
    private val client = HttpClient.newHttpClient()

    @AfterTest
    fun close() {
        servers.forEach(RecipeServer::close)
        scope.cancel()
        workers.shutdownNow()
    }

    /** A server on a free port whose builds run [build] in place of the recipe command. */
    private fun server(build: (RecipeArgs) -> Int): RecipeServer {
        val queue = BuildQueue(scope, workers.asCoroutineDispatcher(), limit = 2)
        return RecipeServer(ServerOptions(listOf("port" to "0")), EngineInfo("abc", "1234567"), queue, build).also {
            it.start()
            servers += it
        }
    }

    private fun post(server: RecipeServer, path: String, body: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}$path")).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun get(server: RecipeServer, path: String) =
        client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}$path")).build(), HttpResponse.BodyHandlers.ofString())

    private fun request(engine: String = "abc", stream: Boolean = true, name: String = "fox") =
        """{"args": ["root=$root", "recipe=$root/$name.recipe.json"], "engine": "$engine", "stream": $stream}"""

    @Test
    fun `a streamed build sends what it prints, line by line, then its code`() {
        val server = server { options ->
            println("recipe: building ${options.recipe?.name}")
            System.err.println("recipe warning at $.parts")
            2
        }
        val events = post(server, "/build", request()).body().lines().filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }
        assertEquals("recipe: building fox.recipe.json", events[0]["out"]?.jsonPrimitive?.content)
        assertEquals("recipe warning at $.parts", events[1]["err"]?.jsonPrimitive?.content)
        assertEquals(2, events[2]["done"]!!.jsonObject["code"]!!.jsonPrimitive.int)
    }

    @Test
    fun `an answer without streaming holds the code and the log`() {
        val server = server { println("recipe: built"); 0 }
        val answer = Json.parseToJsonElement(post(server, "/build", request(stream = false)).body()).jsonObject
        assertEquals(0, answer["code"]!!.jsonPrimitive.int)
        assertEquals("""[{"out":"recipe: built"}]""", answer["log"].toString())
    }

    @Test
    fun `a build that throws answers failed and the server builds on`() {
        val server = server { options -> if (options.recipe?.name == "bad.recipe.json") error("no device") else 0 }
        val failed = post(server, "/build", request(name = "bad")).body()
        assertTrue("build failed: java.lang.IllegalStateException: no device" in failed, failed)
        assertTrue(""""done":{"code":1,""" in failed, failed)
        assertTrue(post(server, "/build", request()).body().contains(""""code":0"""))
    }

    @Test
    fun `a request for another engine is refused, and so is any once the server drains`() {
        val server = server { 0 }
        assertEquals(409, post(server, "/build", request(engine = "other")).statusCode())
        assertEquals(400, post(server, "/build", """{"args": []}""").statusCode())
        server.drain()
        assertEquals(503, post(server, "/build", request()).statusCode())
        runBlocking { server.drained() }
        val status = Json.parseToJsonElement(get(server, "/status").body()).jsonObject
        assertEquals("abc", status["engine"]?.jsonPrimitive?.content)
        assertEquals("false", status["accepting"]?.jsonPrimitive?.content)
    }
}
