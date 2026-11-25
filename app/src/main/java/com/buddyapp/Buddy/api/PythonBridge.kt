package com.buddyapp.Buddy.api

import com.chaquo.python.Python
import com.chaquo.python.PyException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PythonBridge
 *
 * Thin Kotlin wrapper around the Python AI modules
 * (gemini_client.py / openai_client.py / groq_client.py / anthropic_client.py).
 * All calls go through Chaquopy — Python runs embedded inside the APK.
 * No server, no Termux, no external process needed.
 */
object PythonBridge {

    // ── Gemini ──────────────────────────────────────────────────────────────

    suspend fun geminiValidateKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val module = py.getModule("gemini_client")
            val result = module.callAttr("validate_key", apiKey)
            val map = result.asMap()
            val success = map[py.builtins.callAttr("str", "success")]?.toBoolean() ?: false
            if (success) {
                Result.success("Valid")
            } else {
                val error = map[py.builtins.callAttr("str", "error")]?.toString() ?: "Validation failed"
                Result.failure(Exception(error))
            }
        } catch (e: PyException) {
            Result.failure(Exception("Python error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun geminiGenerate(
        prompt: String,
        text: String,
        apiKey: String,
        model: String,
        temperature: Double
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val module = py.getModule("gemini_client")
            val result = module.callAttr("generate", prompt, text, apiKey, model, temperature)
            val map = result.asMap()
            val success = map[py.builtins.callAttr("str", "success")]?.toBoolean() ?: false
            if (success) {
                val resultText = map[py.builtins.callAttr("str", "result")]?.toString() ?: ""
                Result.success(resultText)
            } else {
                val error = map[py.builtins.callAttr("str", "error")]?.toString() ?: "Unknown error"
                Result.failure(Exception(error))
            }
        } catch (e: PyException) {
            Result.failure(Exception("Python error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── OpenAI-compatible ────────────────────────────────────────────────────

    suspend fun openaiValidateKey(apiKey: String, endpoint: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val module = py.getModule("openai_client")
            val result = module.callAttr("validate_key", apiKey, endpoint)
            val map = result.asMap()
            val success = map[py.builtins.callAttr("str", "success")]?.toBoolean() ?: false
            if (success) {
                Result.success("Valid")
            } else {
                val error = map[py.builtins.callAttr("str", "error")]?.toString() ?: "Validation failed"
                Result.failure(Exception(error))
            }
        } catch (e: PyException) {
            Result.failure(Exception("Python error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun openaiGenerate(
        prompt: String,
        text: String,
        apiKey: String,
        model: String,
        temperature: Double,
        endpoint: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val module = py.getModule("openai_client")
            val result = module.callAttr("generate", prompt, text, apiKey, model, temperature, endpoint)
            val map = result.asMap()
            val success = map[py.builtins.callAttr("str", "success")]?.toBoolean() ?: false
            if (success) {
                val resultText = map[py.builtins.callAttr("str", "result")]?.toString() ?: ""
                Result.success(resultText)
            } else {
                val error = map[py.builtins.callAttr("str", "error")]?.toString() ?: "Unknown error"
                Result.failure(Exception(error))
            }
        } catch (e: PyException) {
            Result.failure(Exception("Python error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Groq ────────────────────────────────────────────────────────────────

    suspend fun groqValidateKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val module = py.getModule("groq_client")
            val result = module.callAttr("validate_key", apiKey)
            val map = result.asMap()
            val success = map[py.builtins.callAttr("str", "success")]?.toBoolean() ?: false
            if (success) {
                Result.success("Valid")
            } else {
                val error = map[py.builtins.callAttr("str", "error")]?.toString() ?: "Validation failed"
                Result.failure(Exception(error))
            }
        } catch (e: PyException) {
            Result.failure(Exception("Python error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun groqGenerate(
        prompt: String,
        text: String,
        apiKey: String,
        model: String,
        temperature: Double
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val module = py.getModule("groq_client")
            val result = module.callAttr("generate", prompt, text, apiKey, model, temperature)
            val map = result.asMap()
            val success = map[py.builtins.callAttr("str", "success")]?.toBoolean() ?: false
            if (success) {
                val resultText = map[py.builtins.callAttr("str", "result")]?.toString() ?: ""
                Result.success(resultText)
            } else {
                val error = map[py.builtins.callAttr("str", "error")]?.toString() ?: "Unknown error"
                Result.failure(Exception(error))
            }
        } catch (e: PyException) {
            Result.failure(Exception("Python error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Chat model names from an OpenAI-compatible endpoint's public model list. */
    suspend fun openaiListChatModels(endpoint: String): Result<List<String>> = withContext(Dispatchers.IO) {
        callPython("openai_client", "list_chat_models", endpoint) { map -> map["models"] ?: "" }
            .map { it.lines().filter(String::isNotBlank) }
    }

    // ── Claude (Anthropic) ──────────────────────────────────────────────────

    suspend fun claudeValidateKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        callPython("anthropic_client", "validate_key", apiKey) { "Valid" }
    }

    /** No temperature: current Claude models reject it. */
    suspend fun claudeGenerate(
        prompt: String,
        text: String,
        apiKey: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        callPython("anthropic_client", "generate", prompt, text, apiKey, model) { map ->
            map["result"] ?: ""
        }
    }

    /**
     * Calls [function] in [module]. The Python side returns
     * {"success": bool, "error": str, ...}; [onSuccess] picks the value to return.
     */
    private fun callPython(
        module: String,
        function: String,
        vararg args: Any,
        onSuccess: (Map<String, String>) -> String
    ): Result<String> = try {
        val py = Python.getInstance()
        val result = py.getModule(module).callAttr(function, *args)
        val map = result.asMap().entries.associate { (k, v) -> k.toString() to v.toString() }
        if (map["success"] == "True") {
            Result.success(onSuccess(map))
        } else {
            Result.failure(Exception(map["error"] ?: "Unknown error"))
        }
    } catch (e: PyException) {
        Result.failure(Exception("Python error: ${e.message}"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
