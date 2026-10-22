package com.example.bookcatalog.data.metadata

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.bookcatalog.data.db.Book
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeChatDescriptionWriterTest {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun collapsesWhitespaceInDescription() {
        assertEquals(
            "A history of the city. It covers three thousand years.",
            HomeChatDescriptionWriter.parseDescription(
                """{"description":"  A history of the city.\n\nIt covers  three thousand years. "}""",
                moshi,
            ),
        )
    }

    @Test
    fun treatsEmptyDescriptionAsUnknownBook() {
        assertNull(HomeChatDescriptionWriter.parseDescription("""{"description":""}""", moshi))
        assertNull(HomeChatDescriptionWriter.parseDescription("""{}""", moshi))
    }

    @Test
    fun rejectsOverlyLongDescription() {
        assertThrows(IllegalArgumentException::class.java) {
            HomeChatDescriptionWriter.parseDescription(
                """{"description":"${"word ".repeat(400)}"}""",
                moshi,
            )
        }
    }

    @Test
    fun readsDescriptionInsideCodeFence() {
        assertEquals(
            "A memoir about food.",
            HomeChatDescriptionWriter.parseDescription(
                "```json\n{\"description\":\"A memoir about food.\"}\n```",
                moshi,
            ),
        )
    }

    @Test
    fun searchesWhenThereIsNoSourceDescription() {
        val api = FakeHomeChatApi(
            toolCall("""{"query":"Ci 9788893884938"}"""),
            answer("""{"description":"A memoir about food."}"""),
        )
        val queries = mutableListOf<String>()
        val writer = HomeChatDescriptionWriter(api, "model", "key", moshi) { query ->
            queries += query
            "RESULT 1\nTitle: Ci vuole gusto"
        }

        val description = runBlocking { writer.write(Book(isbn13 = "9788893884938", title = "Ci")) }

        assertEquals("A memoir about food.", description)
        assertEquals(listOf("Ci 9788893884938"), queries)
        val followUp = api.requests[1]
        assertEquals("tool", followUp.messages.last().role)
        assertEquals("call-1", followUp.messages.last().toolCallId)
        assertTrue(followUp.messages.last().content.contains("Ci vuole gusto"))
    }

    @Test
    fun stopsOfferingSearchAfterTwoRounds() {
        val api = FakeHomeChatApi(
            toolCall("""{"query":"first"}"""),
            toolCall("""{"query":"second"}"""),
            answer("""{"description":""}"""),
        )
        val writer = HomeChatDescriptionWriter(api, "model", "key", moshi) { "No results." }

        assertNull(runBlocking { writer.write(Book(title = "Unknown")) })
        assertEquals(3, api.requests.size)
        assertNull(api.requests[2].tools)
        assertEquals("json_object", api.requests[2].responseFormat?.type)
    }

    @Test
    fun summarizesWithoutOfferingSearch() {
        val api = FakeHomeChatApi(answer("""{"description":"A history of Jerusalem."}"""))
        val writer = HomeChatDescriptionWriter(api, "model", "key", moshi) { error("should not search") }

        val description = runBlocking {
            writer.write(Book(title = "Jerusalem", description = "A BESTSELLER. A history of Jerusalem."))
        }

        assertEquals("A history of Jerusalem.", description)
        assertNull(api.requests.single().tools)
    }

    @Test
    fun sendsSearchToolWithoutForcedJsonFormat() {
        val api = FakeHomeChatApi(answer("""{"description":""}"""))
        val writer = HomeChatDescriptionWriter(api, "model", "key", moshi) { "No results." }
        runBlocking { writer.write(Book(title = "Ci")) }

        val json = moshi.adapter(ChatCompletionRequest::class.java).toJson(api.requests.single())

        assertTrue(json, json.contains(""""tools":[{"type":"function","function":{"name":"web_search""""))
        assertTrue(json, json.contains(""""required":["query"]"""))
        assertTrue(json, !json.contains("response_format") && !json.contains("tool_call_id"))
    }

    private fun toolCall(arguments: String) = ChatCompletionMessage(
        toolCalls = listOf(ChatToolCall(id = "call-1", function = ChatToolCallFunction("web_search", arguments))),
    )

    private fun answer(content: String) = ChatCompletionMessage(content = content)

    private class FakeHomeChatApi(vararg replies: ChatCompletionMessage) : HomeChatApi {
        private val remaining = ArrayDeque(replies.toList())
        val requests = mutableListOf<ChatCompletionRequest>()

        override suspend fun complete(
            authorization: String,
            request: ChatCompletionRequest,
        ): ChatCompletionResponse {
            requests += request.copy(messages = request.messages.toList())
            return ChatCompletionResponse(listOf(ChatCompletionChoice(remaining.removeFirst())))
        }
    }
}
