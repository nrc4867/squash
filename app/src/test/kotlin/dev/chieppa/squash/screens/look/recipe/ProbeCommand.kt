package dev.chieppa.squash.screens.look.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.recipe.RecipeException
import dev.chieppa.squash.recipe.RecipeJson
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.probe.RecipeProbe
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.smoke.CloseUps

/** The questions the probe answers, by argument key. */
private val QUESTIONS = setOf("part", "uv", "point", "face")

/**
 * Builds the recipe [RecipeArgs.probe] names and answers its questions about the character at rest, one line each: `part=<id>[,<id>]`
 * (or `part=all`, the default) its extent, frame, joints and sockets; `uv=<part>:<u>,<v>[,<patch>]` the body point there;
 * `point=<x>,<y>,<z>` the part, u and v nearest that body point; `face=<head>:<x>,<y>` the u and v at that place on the head's face.
 */
internal fun probe(options: RecipeArgs): Int {
    val file = requireNotNull(options.probe)
    if (!file.isFile) throw RecipeException("$", "no recipe file at ${file.absolutePath}")
    val folder = TemplateFolder.shipped()
    val built = RecipeBuilder(folder).build(RecipeJson.read(file.readText()))
    built.warnings.forEach { System.err.println("recipe warning at $it") }
    val questions = options.entries.filter { it.first in QUESTIONS }.ifEmpty { listOf("part" to "all") }
    return TestWork().use { work ->
        val session = CharacterSession(built.document, CloseUps.shippedLibrary(), work.scope, work.dispatcher)
        work.drain()
        val assembled = checkNotNull(session.assembly.output.value) { "the character did not assemble" }
        val plan = folder.plans.firstOrNull { it.ref == built.document.character.bodyPlan }
        val probe = RecipeProbe(built.document.character, assembled, folder, plan)
        println("probe: at rest, body space: x forward, y up, z to the character's right (its left is -z)")
        val wrong = questions.count { (key, value) ->
            try {
                answer(probe, key, value).forEach(::println)
                false
            } catch (e: RecipeException) {
                System.err.println("probe $key=$value: ${e.message}")
                true
            }
        }
        return if (wrong == 0) 0 else PROBE_WRONG
    }
}

/** What the probe exits with when a question could not be answered; the others are still answered. */
private const val PROBE_WRONG = 2

private fun answer(probe: RecipeProbe, key: String, value: String): List<String> = when (key) {
    "part" -> if (value == "all") probe.describeAll() else value.split(',').flatMap(probe::describe)
    "uv" -> {
        val (part, place) = split(key, value)
        val numbers = place.split(',')
        if (numbers.size !in 2..3) throw RecipeException(key, "give uv=<part>:<u>,<v> or uv=<part>:<u>,<v>,<patch>, not $value")
        listOf("uv $value: " + probe.line(probe.at(part, number(key, numbers[0]), number(key, numbers[1]), numbers.getOrNull(2))))
    }
    "point" -> {
        val numbers = value.split(',').map { number(key, it) }
        if (numbers.size != 3) throw RecipeException(key, "give point=<x>,<y>,<z>, not $value")
        val target = Vec3(numbers[0], numbers[1], numbers[2])
        val found = probe.nearest(target) ?: throw RecipeException(key, "the character builds no surface")
        listOf("point $value: " + probe.line(found) + ", ${"%.3f".format(java.util.Locale.ROOT, (found.point - target).length)} away")
    }
    "face" -> {
        val (part, place) = split(key, value)
        val numbers = place.split(',').map { number(key, it) }
        if (numbers.size != 2) throw RecipeException(key, "give face=<head>:<x>,<y>, not $value")
        listOf("face $value: " + probe.line(probe.face(part, numbers[0], numbers[1])))
    }
    else -> emptyList()
}

private fun split(key: String, value: String): Pair<String, String> {
    if (':' !in value) throw RecipeException(key, "give $key=<part>:<numbers>, not $value")
    return value.substringBefore(':') to value.substringAfter(':')
}

private fun number(key: String, text: String): Float = text.toFloatOrNull() ?: throw RecipeException(key, "\"$text\" is not a number")
