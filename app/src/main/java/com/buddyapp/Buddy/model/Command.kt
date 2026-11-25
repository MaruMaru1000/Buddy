package com.buddyapp.Buddy.model

data class Command(
    val trigger: String,
    val prompt: String,
    val isBuiltIn: Boolean = false,
    val isTextReplacer: Boolean = false,
    /** Internal name of a built-in command ("fix", "reply", ...). Stays the same if the trigger is renamed. */
    val builtInName: String? = null
)
