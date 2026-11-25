package com.buddyapp.Buddy.api

/** Fixed settings for the built-in providers. No personal URLs or keys here. */
object Providers {
    /** OpenCode Go's public OpenAI-compatible base URL (the same for everyone). */
    const val OPENCODE_ENDPOINT = "https://opencode.ai/inference/go/openai/v1"

    const val DEFAULT_CLAUDE_MODEL = "claude-haiku-5-5"

    /** Haiku: fastest and cheapest. Sonnet: better writing. Opus: best, slowest, most expensive. */
    val CLAUDE_MODELS = listOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5")
}
