package com.farrah.sproutvision.analyze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Guards the SAM 3.1 request shape and SSE assembly (both pure and
 * unit-testable). Transport itself needs the network; the request JSON
 * below is asserted field by field so a wrong part type or endpoint
 * cannot slip in silently.
 */
class SamClientTest {

    @Test
    fun `concept phrases are the pinned playground nouns`() {
        assertEquals("microgreens", samConcept(growOut = false))
        assertEquals("plants", samConcept(growOut = true))
        assertEquals("black tray", SAM_TRAY_CONCEPT)
    }

    @Test
    fun `request body matches the playground responses shape`() {
        val body = buildSamRequestBody("microgreens", "file-abc123")
        assertTrue(body.contains("\"model\":\"sam-3.1\""))
        assertTrue(body.contains("\"stream\":true"))
        assertTrue(body.contains("\"mask_encoding\":\"lossless\""))
        assertTrue(body.contains("\"type\":\"input_text\""))
        assertTrue(body.contains("\"text\":\"microgreens\""))
        assertTrue(body.contains("\"type\":\"input_image\""))
        assertTrue(body.contains("\"file_id\":\"file-abc123\""))
        assertTrue(!body.contains("image_url"))
    }

    @Test
    fun `request body escapes quotes in the concept and file id`() {
        val body = buildSamRequestBody("Farrah's \"Basil\"", "file-\"x\"")
        assertTrue(body.contains("Farrah's \\\"Basil\\\""))
        assertTrue(body.contains("\"file_id\":\"file-\\\"x\\\"\""))
    }

    @Test
    fun `upload id parses from the files response`() {
        assertEquals("file-abc123", parseUploadId("""{"id":"file-abc123","purpose":"user_data"}"""))
        assertEquals("f1", parseUploadId("""{"id" : "f1"}"""))
    }

    @Test
    fun `upload id rejects missing and malformed responses`() {
        for (body in listOf("{}", """{"id":""}""", """{"id":42}""", "not json")) {
            try {
                parseUploadId(body)
                fail("expected SamException for: $body")
            } catch (e: SamException) {
                // Expected.
            }
        }
    }

    @Test
    fun `stream text assembles deltas until done`() {
        val lines = listOf(
            ": ping",
            "",
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"<0f>0\"}",
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"<|box\"}",
            "data: [DONE]",
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"NEVER\"}"
        )
        assertEquals("<0f>0<|box", collectStreamText(lines))
    }

    @Test
    fun `stream text stops at response completed`() {
        val lines = listOf(
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"abc\"}",
            "data: {\"type\":\"response.completed\",\"id\":\"r1\"}",
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"NEVER\"}"
        )
        assertEquals("abc", collectStreamText(lines))
    }

    @Test
    fun `stream failure throws with the message`() {
        val lines = listOf(
            "data: {\"type\":\"response.failed\",\"message\":\"bad image\"}"
        )
        try {
            collectStreamText(lines)
            fail("expected SamException")
        } catch (e: SamException) {
            assertTrue(e.message!!.contains("bad image"))
        }
    }

    @Test
    fun `delta extraction honors escapes and nested type-like text`() {
        // A delta whose text itself contains "type": must not confuse the
        // top-level scan; \n and \uXXXX must decode.
        val lines = listOf(
            "data: {\"type\":\"response.output_text.delta\"," +
                "\"delta\":\"a\\\"type\\\":b\\nA\\u0021\"}"
        )
        assertEquals("a\"type\":b\nA!", collectStreamText(lines))
    }

    @Test
    fun `failure flag names the real error`() {
        assertEquals(
            "SAM measurement unavailable: SAM API error 404: not found",
            samFailureFlag(IOException("SAM API error 404: not found"))
        )
    }

    @Test
    fun `failure flag handles missing errors and folds newlines`() {
        assertEquals("SAM measurement unavailable", samFailureFlag(null))
        assertEquals(
            "SAM measurement unavailable: line one line two",
            samFailureFlag(IOException("line one\nline two"))
        )
    }

    @Test
    fun `failure flag truncates long bodies`() {
        val flag = samFailureFlag(IOException("x".repeat(500)))
        assertTrue(flag.startsWith("SAM measurement unavailable: "))
        assertTrue(flag.length < 220)
    }

    @Test
    fun `corrupt events are ignored, other events pass through`() {
        val lines = listOf(
            "data: not-json{{{",
            "event: message",
            "data: {\"type\":\"response.created\",\"id\":\"r1\"}",
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"ok\"}"
        )
        assertEquals("ok", collectStreamText(lines))
    }

    @Test
    fun `event-style SSE takes the type from the event line`() {
        // Meta's stream sends `event: <type>` with data payloads that
        // carry no type of their own.
        val lines = listOf(
            "event: response.output_text.delta",
            "data: {\"delta\":\"<0f>0\"}",
            "",
            "event: response.output_text.delta",
            "data: {\"delta\":\"<|box\"}",
            "",
            "event: response.completed",
            "data: {\"response\":{}}"
        )
        assertEquals("<0f>0<|box", collectStreamText(lines))
    }

    @Test
    fun `done text supplies the full output when no deltas arrive`() {
        val lines = listOf(
            "event: response.output_text.done",
            "data: {\"text\":\"FULL\"}"
        )
        assertEquals("FULL", collectStreamText(lines))
    }

    @Test
    fun `done text never duplicates deltas`() {
        val lines = listOf(
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"a\"}",
            "data: {\"type\":\"response.output_text.done\",\"text\":\"a\"}",
            "data: {\"type\":\"response.output_text.done\",\"text\":\"a\"}"
        )
        assertEquals("a", collectStreamText(lines))
    }

    @Test
    fun `completed event harvests the output array with zero deltas`() {
        // The observed in-headset shape: created / in_progress /
        // output_item.added, no text deltas, masks only in the
        // completed response object.
        val lines = listOf(
            "event: response.created",
            "data: {\"response\":{\"id\":\"r1\"}}",
            "",
            "event: response.in_progress",
            "data: {\"response\":{\"id\":\"r1\"}}",
            "",
            "event: response.output_item.added",
            "data: {\"item\":{\"id\":\"m1\"}}",
            "",
            "event: response.completed",
            "data: {\"response\":{\"output\":[{\"content\":[" +
                "{\"type\":\"output_text\",\"text\":\"MASK\"}]}]}}"
        )
        assertEquals("MASK", collectStreamText(lines))
    }

    @Test
    fun `completed output is ignored once deltas arrived`() {
        val lines = listOf(
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"a\"}",
            "data: {\"type\":\"response.completed\",\"response\":" +
                "{\"output\":[{\"content\":[{\"type\":\"output_text\"," +
                "\"text\":\"a\"}]}]}}"
        )
        assertEquals("a", collectStreamText(lines))
    }

    @Test
    fun `event type clears on blank lines`() {
        val lines = listOf(
            "event: response.output_text.delta",
            "",
            "data: {\"delta\":\"stale\"}"
        )
        assertEquals("", collectStreamText(lines))
    }

    @Test
    fun `delta without delta text is ignored`() {
        val lines = listOf(
            "data: {\"type\":\"response.output_text.delta\"}",
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"ok\"}"
        )
        assertEquals("ok", collectStreamText(lines))
    }

    @Test
    fun `failed event reads the nested error message`() {
        val lines = listOf(
            "data: {\"type\":\"response.failed\",\"response\":" +
                "{\"error\":{\"message\":\"quota hit\"}}}"
        )
        try {
            collectStreamText(lines)
            fail("expected SamException")
        } catch (e: SamException) {
            assertTrue(e.message!!.contains("quota hit"))
        }
    }

    @Test
    fun `content part done surfaces output text and refusals`() {
        assertEquals(
            "FULL",
            collectStreamText(
                listOf(
                    "data: {\"type\":\"response.content_part.done\"," +
                        "\"part\":{\"type\":\"output_text\",\"text\":\"FULL\"}}"
                )
            )
        )
        try {
            collectStreamText(
                listOf(
                    "data: {\"type\":\"response.content_part.done\"," +
                        "\"part\":{\"type\":\"refusal\",\"refusal\":\"no\"}}"
                )
            )
            fail("expected SamException")
        } catch (e: SamException) {
            assertTrue(e.message!!.contains("no"))
        }
    }

    @Test
    fun `completed body harvests a non-streamed response object`() {
        val body = "{\"id\":\"r1\",\"output\":[{\"content\":[" +
            "{\"type\":\"output_text\",\"text\":\"A\"}," +
            "{\"type\":\"output_text\",\"text\":\"B\"}," +
            "{\"type\":\"refusal\",\"refusal\":\"x\"}]}]}"
        assertEquals("AB", harvestCompletedBody(body))
        assertEquals(null, harvestCompletedBody("{\"id\":\"r1\"}"))
        assertEquals(null, harvestCompletedBody("not json"))
    }

    @Test
    fun `completed body throws on error objects`() {
        try {
            harvestCompletedBody("{\"error\":{\"message\":\"bad key\"}}")
            fail("expected SamException")
        } catch (e: SamException) {
            assertTrue(e.message!!.contains("bad key"))
        }
    }

    @Test
    fun `json parser rejects trailing garbage and deep nesting`() {
        assertEquals(null, parseJsonValue("{\"a\":1} trailing"))
        assertEquals(null, parseJsonValue("[".repeat(40)))
        assertEquals(
            JsonValue.Other("1"),
            (parseJsonValue("{\"a\":1}") as JsonValue.Obj).entries["a"]
        )
    }
}
