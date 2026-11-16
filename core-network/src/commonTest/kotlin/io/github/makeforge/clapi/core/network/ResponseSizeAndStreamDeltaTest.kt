package io.github.makeforge.clapi.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResponseSizeAndStreamDeltaTest {

    @Test
    fun utf8ByteLength_matches_encodeToByteArray_for_ascii_multibyte_and_surrogate_pairs() {
        val samples = listOf(
            "",
            "hello world",
            "café résumé naïve",
            "日本語のテキスト",
            "🚀🔥🎉✨",
            "Mixed: ASCII + Ümlaut + 中文 + 🦄!",
        )
        for (sample in samples) {
            assertEquals(
                sample.encodeToByteArray().size.toLong(),
                sample.utf8ByteLength(),
                "UTF-8 byte length mismatch for: $sample",
            )
        }
    }

    @Test
    fun extractChunkDelta_parses_single_chunk_and_mayContainLlmEnvelope_gates_non_llm_json() {
        val openAiChunk = """{"choices":[{"delta":{"content":"Hello"}}]}"""
        val ollamaChunk = """{"response":" world"}"""
        val plainArrayJson = """[{"id":1,"name":"Alice"},{"id":2,"name":"Bob"}]"""

        assertEquals("Hello", LlmTextAssembler.extractChunkDelta(openAiChunk))
        assertEquals(" world", LlmTextAssembler.extractChunkDelta(ollamaChunk))
        assertNull(LlmTextAssembler.extractChunkDelta(plainArrayJson))

        assertTrue(LlmTextAssembler.mayContainLlmEnvelope(openAiChunk))
        assertTrue(LlmTextAssembler.mayContainLlmEnvelope(ollamaChunk))
        assertFalse(LlmTextAssembler.mayContainLlmEnvelope(plainArrayJson))
    }
}
