package dev.chieppa.squash.screens.look.recipe.server

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.minutes

class BuildRequestTest {
    private val root = File(System.getProperty("java.io.tmpdir")).absoluteFile.path.replace('\\', '/')

    private fun body(vararg args: String, extra: String = "") =
        """{"args": [${(listOf("root=$root") + args).joinToString { "\"$it\"" }}]$extra}"""

    @Test
    fun `a build request reads its arguments, engine, stream and timeout`() {
        val request = BuildRequest.parse(body("recipe=$root/fox.recipe.json", "close=face", extra = """, "engine": "abc", "stream": true, "timeout": 3"""))
        assertEquals("fox.recipe.json", request.name)
        assertEquals("abc", request.engine)
        assertEquals(true, request.stream)
        assertEquals(3.minutes, request.timeout)
        assertEquals(File("$root/fox.recipe.json").absoluteFile, request.options.recipe?.absoluteFile)
    }

    @Test
    fun `encoded spaces in a path are read back`() {
        val request = BuildRequest.parse(body("recipe=$root/my%20models/fox.recipe.json"))
        assertEquals("my models", request.file.parentFile.name)
    }

    @Test
    fun `a request names exactly one thing to build`() {
        assertFailsWith<BadRequest> { BuildRequest.parse(body("close=face")) }
        assertFailsWith<BadRequest> { BuildRequest.parse(body("recipe=$root/a.json", "probe=$root/a.json")) }
    }

    @Test
    fun `keys the server sets, raw spaces, bare words and a relative root are refused`() {
        assertFailsWith<BadRequest> { BuildRequest.parse(body("recipe=$root/a.json", "status=$root/status.txt")) }
        assertFailsWith<BadRequest> { BuildRequest.parse(body("recipe=$root/a.json", "serve=true")) }
        assertFailsWith<BadRequest> { BuildRequest.parse(body("recipe=$root/a b.json")) }
        assertFailsWith<BadRequest> { BuildRequest.parse(body("recipe=$root/a.json", "nonsense")) }
        assertFailsWith<BadRequest> { BuildRequest.parse("""{"args": ["root=relative", "recipe=a.json"]}""") }
        assertFailsWith<BadRequest> { BuildRequest.parse("""{"args": ["recipe=$root/a.json"]}""") }
        assertFailsWith<BadRequest> { BuildRequest.parse("not json") }
        assertFailsWith<BadRequest> { BuildRequest.parse("""{"args": [1]}""") }
        assertFailsWith<BadRequest> { BuildRequest.parse(body("recipe=$root/a.json", extra = """, "timeout": -1""")) }
    }

    @Test
    fun `a watch takes a recipe only and always streams`() {
        assertFailsWith<BadRequest> { BuildRequest.parse(body("probe=$root/a.json"), watch = true) }
        assertEquals(true, BuildRequest.parse(body("recipe=$root/a.json"), watch = true).stream)
    }

    @Test
    fun `builds that render nothing are small and every outfit or a compare is large`() {
        assertEquals(0, BuildRequest.parse(body("probe=$root/a.json")).size)
        assertEquals(0, BuildRequest.parse(body("recipe=$root/a.json", "render=false")).size)
        assertEquals(1, BuildRequest.parse(body("recipe=$root/a.json")).size)
        assertEquals(2, BuildRequest.parse(body("recipe=$root/a.json", "outfits=all")).size)
        assertEquals(2, BuildRequest.parse(body("recipe=$root/a.json", "reference=$root/ref.png")).size)
    }

    @Test
    fun `builds are keyed by the folder they write, or the recipe when they name none`() {
        val one = BuildRequest.parse(body("recipe=$root/a.recipe.json", "out=$root/out/fox"))
        val two = BuildRequest.parse(body("recipe=$root/b.recipe.json", "out=$root/out/../out/fox"))
        val three = BuildRequest.parse(body("recipe=$root/a.recipe.json"))
        assertEquals(one.key, two.key)
        assertNotEquals(one.key, three.key)
    }
}
