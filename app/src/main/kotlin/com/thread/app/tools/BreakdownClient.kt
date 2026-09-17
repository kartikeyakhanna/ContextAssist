package com.thread.app.tools

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.generationConfig
import com.thread.app.firebase.AppCheckConfigurator
import org.json.JSONException
import org.json.JSONObject

class BreakdownClient(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private var appCheckInstalled = false

    suspend fun generate(task: String, intent: String?): TaskBreakdown {
        ensureFirebaseConfigured()

        val prompt = buildString {
            appendLine("Break this task into 3 to 12 short, concrete, actionable steps.")
            appendLine("Use stable lowercase hyphenated ids.")
            appendLine("Do not invent names, dates, codes, amounts, or facts.")
            appendLine("Return plain checklist text, not code, commands, links, or Markdown.")
            appendLine()
            appendLine("Task: $task")
            if (!intent.isNullOrBlank()) {
                append("General context: $intent")
            }
        }

        val responseText = try {
            generateWithFallback(prompt)
        } catch (error: ServiceDisabledException) {
            throw BreakdownClientException(
                "Firebase AI Logic is not enabled. Open Firebase Console, select AI Logic, " +
                    "and click Get started.",
                error,
            )
        } catch (error: APINotConfiguredException) {
            throw BreakdownClientException(
                "Finish the Firebase AI Logic setup for the Gemini Developer API.",
                error,
            )
        } catch (error: QuotaExceededException) {
            throw BreakdownClientException(
                "The Gemini free-tier quota is exhausted. Try again later.",
                error,
            )
        } catch (error: InvalidAPIKeyException) {
            throw BreakdownClientException(
                "Firebase rejected this app's configuration. Download a new google-services.json.",
                error,
            )
        } catch (error: FirebaseAIException) {
            throw BreakdownClientException(
                if (error.isCapacityError()) {
                    "Gemini is temporarily busy. Wait a moment and retry."
                } else {
                    "Gemini could not generate steps. Check the network and try again."
                },
                error,
            )
        }

        if (responseText.isNullOrBlank()) {
            throw BreakdownClientException("Gemini returned an empty checklist.")
        }
        return parseBreakdown(responseText)
    }

    private suspend fun generateWithFallback(prompt: String): String? {
        var lastCapacityError: FirebaseAIException? = null

        for (modelName in MODEL_NAMES) {
            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = modelName,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = BREAKDOWN_SCHEMA
                    temperature = 0.2f
                },
            )

            try {
                return model.generateContent(prompt).text
            } catch (error: FirebaseAIException) {
                if (!error.isCapacityError()) throw error
                lastCapacityError = error
            }
        }

        throw checkNotNull(lastCapacityError)
    }

    private fun FirebaseAIException.isCapacityError(): Boolean {
        val details = generateSequence<Throwable>(this) { it.cause }
            .joinToString(" ") { it.message.orEmpty() }
            .lowercase()
        return listOf(
            "overloaded",
            "high demand",
            "prefill queue",
            "unavailable",
            "too many retries",
        ).any(details::contains)
    }

    private fun parseBreakdown(responseBody: String): TaskBreakdown {
        try {
            val response = JSONObject(responseBody)
            val title = response.optString("title").trim()
            val steps = response.optJSONArray("steps")
                ?: throw BreakdownClientException("Gemini returned no checklist steps.")

            val items = buildList {
                for (index in 0 until steps.length()) {
                    val step = steps.getJSONObject(index)
                    add(
                        TodoItem(
                            id = step.optString("id").trim(),
                            text = step.optString("text").trim(),
                        ),
                    )
                }
            }

            return validatedTaskBreakdown(title, items)
        } catch (error: JSONException) {
            throw BreakdownClientException("Gemini returned invalid checklist JSON.", error)
        } catch (error: IllegalArgumentException) {
            throw BreakdownClientException("Gemini returned an invalid checklist.", error)
        }
    }

    private fun ensureFirebaseConfigured() {
        if (
            FirebaseApp.getApps(applicationContext).isEmpty() &&
            FirebaseApp.initializeApp(applicationContext) == null
        ) {
            throw BreakdownClientException(
                "Firebase is not configured. Add app/google-services.json and reinstall the app.",
            )
        }
        if (!appCheckInstalled) {
            AppCheckConfigurator.install()
            appCheckInstalled = true
        }
    }

    companion object {
        private val MODEL_NAMES = listOf(
            "gemini-3.7-flash",
            "gemini-3.1-flash-lite",
        )

        private val BREAKDOWN_SCHEMA = Schema.obj(
            mapOf(
                "title" to Schema.string(
                    description = "A concise title for the user's task.",
                ),
                "steps" to Schema.array(
                    items = Schema.obj(
                        mapOf(
                            "id" to Schema.string(
                                description = "A unique lowercase hyphenated identifier.",
                            ),
                            "text" to Schema.string(
                                description = "One short, concrete action.",
                            ),
                        ),
                    ),
                    description = "The ordered task steps.",
                    minItems = 3,
                    maxItems = 12,
                ),
            ),
        )
    }
}

class BreakdownClientException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
