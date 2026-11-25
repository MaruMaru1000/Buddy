package com.buddyapp.Buddy.manager

/**
 * "Plain punctuation" setting (Settings > App Preferences, on by default).
 *
 * - [RULE] is added to every AI command's prompt: no em/en dashes as punctuation,
 *   no Oxford comma.
 * - [clean] is a safety net that removes any em dash the model still writes.
 *   Oxford commas are only handled by the prompt rule: ", and" is often correct
 *   grammar ("I called, and she answered"), so code can't remove it safely.
 */
object PlainPunctuation {

    const val PREF_KEY = "plain_punctuation"
    const val DEFAULT = true

    const val RULE =
        "\n\nPunctuation rules for your output: never use em dashes (—) or en dashes (–) " +
        "as punctuation. Use a comma, a full stop or the word \"and\" instead. " +
        "Do not use the Oxford comma: write \"red, green and blue\", not \"red, green, and blue\"."

    // A dash used as a list marker at the start of a line becomes "- ".
    private val LIST_MARKER = Regex("""(?m)^([ \t]*)[—–][ \t]*""")
    // Number ranges like 5–7 or 5—7 become 5-7.
    private val RANGE = Regex("""(\d)[ \t]*[–—][ \t]*(\d)""")
    // Any other em dash, or a spaced en dash, becomes a comma.
    private val EM_DASH = Regex("""[ \t]*—[ \t]*""")
    private val SPACED_EN_DASH = Regex("""[ \t]+–[ \t]+""")
    // Tidy up what the swap can leave behind: ", ." -> "." and ", ," -> ","
    private val COMMA_BEFORE_PUNCT = Regex(""",[ \t]*([,.;:!?])""")
    // A comma left at the very end of a line.
    private val TRAILING_COMMA = Regex("""(?m),[ \t]*$""")

    fun clean(text: String): String {
        var out = text
        out = LIST_MARKER.replace(out, "$1- ")
        out = RANGE.replace(out, "$1-$2")
        out = EM_DASH.replace(out, ", ")
        out = SPACED_EN_DASH.replace(out, ", ")
        out = COMMA_BEFORE_PUNCT.replace(out, "$1")
        out = TRAILING_COMMA.replace(out, "")
        return out.trim()
    }
}
