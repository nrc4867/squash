package dev.chieppa.squash.schemagen

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import dev.chieppa.squash.character.load.TemplateJson
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemasTest {
    private val schemaFolder = Path.of(System.getProperty("squash.schema"))
    private val templates = Path.of(System.getProperty("squash.templates"))
    private val mapper = ObjectMapper()

    private fun schema(name: String): JsonSchema =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaFolder.resolve(name).readText())

    /** A template file as plain JSON, its comments and trailing commas resolved as the app reads them. */
    private fun tree(text: String): JsonNode = mapper.readTree(TemplateJson.parseToJsonElement(text).toString())

    private fun errors(schemaName: String, text: String) = schema(schemaName).validate(tree(text))

    private val leg get() = templates.resolve("leg.part.json").readText()

    @Test
    fun theCommittedSchemasAreCurrent() {
        for ((name, text) in Schemas.render()) {
            val file = schemaFolder.resolve(name)
            assertTrue(file.exists(), "$name is missing; run generateSchemas")
            assertEquals(text, file.readText().replace("\r\n", "\n"), "$name is stale; run generateSchemas")
        }
    }

    @Test
    fun everyShippedTemplatePassesItsSchema() {
        val parts = templates.listDirectoryEntries("*.part.json")
        assertEquals(33, parts.size)
        for (file in parts) assertEquals(emptySet(), errors("part-template.schema.json", file.readText()), file.name)
    }

    @Test
    fun everyShippedPlanPassesItsSchema() {
        val plans = templates.resolve("plans").listDirectoryEntries("*.plan.json")
        assertEquals(5, plans.size)
        for (file in plans) assertEquals(emptySet(), errors("body-plan.schema.json", file.readText()), file.name)
    }

    @Test
    fun theSchemaRejectsWhatTheParserRejects() {
        val schema = "part-template.schema.json"
        assertTrue(errors(schema, leg.replace("\"tube\"", "\"wing\"")).isNotEmpty(), "unknown kind")
        assertTrue(errors(schema, leg.replace("\"openEnd\"", "\"openEndd\"")).isNotEmpty(), "unknown key")
        assertTrue(errors(schema, leg.replace("\"\$thickness\"", "\"thickness\"")).isNotEmpty(), "bad parameter reference")
        assertTrue(errors(schema, leg.replace("\"circle\"", "\"square\"")).isNotEmpty(), "unknown section")
        assertTrue(errors(schema, leg.replace("[0.5, 0.6]", "[0.5, 1.6]")).isNotEmpty(), "span outside 0 to 1")
        assertTrue(errors(schema, leg.replace("\"u\": 0.55", "\"u\": 1.55")).isNotEmpty(), "@Range on u")
        assertTrue(errors(schema, leg.replace("\"format\": 1", "\"format\": 2")).isNotEmpty(), "@Range on format")
    }

    @Test
    fun theSchemaAcceptsExperimentKeysAndAParameterOrANumber() {
        val schema = "part-template.schema.json"
        assertEquals(emptySet(), errors(schema, leg.replace("\"format\": 1", "\"x-note\": 1, \"format\": 1")))
        assertEquals(emptySet(), errors(schema, leg.replace("\"\$thickness\"", "0.3")))
    }
}
