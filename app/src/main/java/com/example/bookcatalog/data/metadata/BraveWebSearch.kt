package com.example.bookcatalog.data.metadata

import android.util.Log
import com.example.bookcatalog.data.remote.BraveSearchApi
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

fun interface WebSearch {
    /** Returns the top results as plain text for the model, or a note saying the search failed. */
    suspend fun search(query: String): String
}

class BraveWebSearch(
    private val api: BraveSearchApi,
    private val apiKey: String,
) : WebSearch {
    override suspend fun search(query: String): String {
        val results = try {
            api.search(apiKey, query, MAX_RESULTS).web?.results.orEmpty()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            Log.w(TAG, "Web search failed", exception)
            return "Web search failed."
        } catch (exception: HttpException) {
            Log.w(TAG, "Web search failed", exception)
            return "Web search failed."
        } catch (exception: JsonDataException) {
            Log.w(TAG, "Web search returned an unreadable response", exception)
            return "Web search failed."
        }
        return formatResults(query, results.map { (it.title.orEmpty()) to (it.description.orEmpty()) })
    }

    companion object {
        private const val TAG = "BraveWebSearch"
        private const val MAX_RESULTS = 5
        private const val MAX_SNIPPET_LENGTH = 400

        /** Brave marks query matches with <strong> tags and escapes entities, so both are removed. */
        internal fun formatResults(query: String, results: List<Pair<String, String>>): String {
            if (results.isEmpty()) return "No web search results found for '$query'."
            return results.take(MAX_RESULTS).mapIndexed { index, (title, description) ->
                """
                    RESULT ${index + 1}
                    Title: ${plainText(title)}
                    Description: ${plainText(description).take(MAX_SNIPPET_LENGTH)}
                """.trimIndent()
            }.joinToString("\n\n")
        }

        private fun plainText(value: String): String = value
            .replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .trim()
    }
}
