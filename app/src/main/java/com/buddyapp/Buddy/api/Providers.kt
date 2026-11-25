package com.buddyapp.Buddy.api

/** Fixed settings for the built-in providers. No personal URLs or keys here. */
object Providers {
    /** OpenCode Go's public base URLs (the same for everyone). */
    const val OPENCODE_ENDPOINT = "https://opencode.ai/inference/go/openai/v1"
    const val OPENCODE_MESSAGES_ENDPOINT = "https://opencode.ai/inference/go/anthropic/v1"

    const val DEFAULT_CLAUDE_MODEL = "claude-haiku-5-5"

    /** Haiku: fastest and cheapest. Sonnet: better writing. Opus: best, slowest, most expensive. */
    val CLAUDE_MODELS = listOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5")

    /** Pref key holding the last loaded OpenCode model list (see [OpenCodeModel.parseList]). */
    const val OPENCODE_MODELS_PREF = "opencode_models_meta"
}

/**
 * One OpenCode model and how Buddy must talk to it.
 * [format]: "chat" (chat completions), "messages" (Anthropic format, e.g. Claude,
 * Qwen) or "responses" (OpenAI's Responses format, e.g. Grok).
 */
data class OpenCodeModel(val id: String, val format: String, val acceptsTemperature: Boolean) {
    companion object {
        const val CHAT = "chat"
        const val MESSAGES = "messages"
        const val RESPONSES = "responses"

        /** Parses lines of "id<TAB>format<TAB>1|0" from openai_client.list_chat_models. */
        fun parseList(lines: String?): List<OpenCodeModel> =
            lines.orEmpty().lines().mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 3 || parts[0].isBlank()) null
                else OpenCodeModel(parts[0], parts[1], parts[2] != "0")
            }

        /** How to talk to [id]. Unknown models (typed by hand) use the chat format. */
        fun find(list: List<OpenCodeModel>, id: String): OpenCodeModel =
            list.firstOrNull { it.id == id } ?: OpenCodeModel(id, CHAT, true)
    }
}
