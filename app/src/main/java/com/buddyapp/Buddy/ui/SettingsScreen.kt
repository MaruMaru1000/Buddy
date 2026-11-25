package com.buddyapp.Buddy.ui

import com.buddyapp.Buddy.manager.KeyManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddyapp.Buddy.manager.CommandManager
import kotlinx.coroutines.launch
import com.buddyapp.Buddy.api.PythonBridge
import com.buddyapp.Buddy.api.Providers
import com.buddyapp.Buddy.api.OpenCodeModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.buddyapp.Buddy.manager.HistoryManager
import com.buddyapp.Buddy.manager.PlainPunctuation
import com.buddyapp.Buddy.manager.UpdateManager
import com.buddyapp.Buddy.ui.components.ScreenTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var providerType by remember { mutableStateOf(prefs.getString("provider_type", KeyManager.PROVIDER_CUSTOM) ?: KeyManager.PROVIDER_CUSTOM) }
    var temperature by remember { mutableFloatStateOf(prefs.getFloat("temperature", 0.5f)) }

    // Gemini settings
    var selectedModel by remember { mutableStateOf(prefs.getString("model", "gemini-3.1-flash-lite-preview") ?: "gemini-3.1-flash-lite-preview") }
    val geminiModels = listOf("gemini-2.5-flash-lite", "gemini-3-flash-preview", "gemini-3.1-flash-lite-preview")

    // Groq settings
    var selectedGroqModel by remember { mutableStateOf(prefs.getString("groq_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile") }
    val groqModels = listOf(
        "llama-3.3-70b-versatile",
        "meta-llama/llama-4-scout-17b-16e-instruct",
        "llama-3.1-8b-instant",
        "openai/gpt-oss-20b",
        "openai/gpt-oss-120b"
    )

    // Custom provider settings
    var customEndpoint by remember { mutableStateOf(prefs.getString("custom_endpoint", "") ?: "") }
    var customModel by remember { mutableStateOf(prefs.getString("custom_model", "") ?: "") }
    val keyManager = remember { KeyManager(context) }
    var customKeyCount by remember { mutableIntStateOf(keyManager.getKeysFor(KeyManager.PROVIDER_CUSTOM).size) }
    val scope = rememberCoroutineScope()

    // Claude settings
    var selectedClaudeModel by remember {
        mutableStateOf(prefs.getString("claude_model", Providers.DEFAULT_CLAUDE_MODEL) ?: Providers.DEFAULT_CLAUDE_MODEL)
    }
    var claudeKeyCount by remember { mutableIntStateOf(keyManager.getKeysFor(KeyManager.PROVIDER_CLAUDE).size) }

    // OpenCode settings
    var openCodeModel by remember { mutableStateOf(prefs.getString("opencode_model", "") ?: "") }
    var openCodeKeyCount by remember { mutableIntStateOf(keyManager.getKeysFor(KeyManager.PROVIDER_OPENCODE).size) }
    // Start with the last saved list, then refresh it from OpenCode.
    var openCodeModels by remember {
        mutableStateOf(OpenCodeModel.parseList(prefs.getString(Providers.OPENCODE_MODELS_PREF, null)))
    }
    var openCodeModelsError by remember { mutableStateOf<String?>(null) }
    var openCodeModelsLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(providerType) {
        if (providerType == KeyManager.PROVIDER_OPENCODE && !openCodeModelsLoaded) {
            PythonBridge.openCodeListModels()
                .onSuccess { raw ->
                    openCodeModelsLoaded = true
                    openCodeModelsError = null
                    // Saved so the service knows each model's format (and for model= in "!!").
                    prefs.edit().putString(Providers.OPENCODE_MODELS_PREF, raw).apply()
                    openCodeModels = OpenCodeModel.parseList(raw)
                }
                .onFailure {
                    if (openCodeModels.isEmpty()) {
                        openCodeModelsError = "Couldn't load OpenCode's model list. Type the model name instead."
                    }
                }
        }
    }

    fun selectProvider(type: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        providerType = type
        prefs.edit().putString("provider_type", type).apply()
    }

    // History
    val historyManager = remember { HistoryManager(context) }
    var saveHistory by remember { mutableStateOf(historyManager.isEnabled()) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    // Trigger prefix
    val commandManager = remember { CommandManager(context) }
    var triggerPrefix by remember { mutableStateOf(commandManager.getTriggerPrefix()) }
    var prefixError by remember { mutableStateOf<String?>(null) }

    // Updates
    val updateManager = remember { UpdateManager(context) }
    var autoUpdate by remember { mutableStateOf(updateManager.autoCheck) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var foundUpdate by remember { mutableStateOf<UpdateManager.Release?>(null) }

    var plainPunctuation by remember {
        mutableStateOf(prefs.getBoolean(PlainPunctuation.PREF_KEY, PlainPunctuation.DEFAULT))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenTitle("Settings")
        Spacer(modifier = Modifier.height(8.dp))

        // AI Provider Section
        SettingsSection(title = "AI Provider", icon = Icons.Outlined.CloudQueue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProviderCard(
                    title = "Google Gemini",
                    selected = providerType == KeyManager.PROVIDER_GEMINI,
                    modifier = Modifier.weight(1f)
                ) { selectProvider(KeyManager.PROVIDER_GEMINI) }
                ProviderCard(
                    title = "Groq",
                    selected = providerType == KeyManager.PROVIDER_GROQ,
                    modifier = Modifier.weight(1f)
                ) { selectProvider(KeyManager.PROVIDER_GROQ) }
                ProviderCard(
                    title = "Claude",
                    selected = providerType == KeyManager.PROVIDER_CLAUDE,
                    modifier = Modifier.weight(1f)
                ) { selectProvider(KeyManager.PROVIDER_CLAUDE) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProviderCard(
                    title = "OpenCode",
                    selected = providerType == KeyManager.PROVIDER_OPENCODE,
                    modifier = Modifier.weight(1f)
                ) { selectProvider(KeyManager.PROVIDER_OPENCODE) }
                ProviderCard(
                    title = "Custom",
                    selected = providerType == KeyManager.PROVIDER_CUSTOM,
                    modifier = Modifier.weight(1f)
                ) { selectProvider(KeyManager.PROVIDER_CUSTOM) }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Configuration Section
        AnimatedVisibility(
            visible = providerType == "gemini" || providerType == "groq",
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SettingsSection(
                title = if (providerType == "gemini") "Gemini Configuration" else "Groq Configuration",
                icon = Icons.Outlined.Memory
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Model Selection",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (providerType == "gemini") "Select from available Gemini models" else "Free tier Open Source models",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (providerType == "gemini") {
                            CleanDropdown(
                                options = geminiModels,
                                selectedOption = selectedModel,
                                onOptionSelected = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedModel = it
                                    prefs.edit().putString("model", it).apply()
                                }
                            )
                        } else {
                            CleanDropdown(
                                options = groqModels,
                                selectedOption = selectedGroqModel,
                                onOptionSelected = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedGroqModel = it
                                    prefs.edit().putString("groq_model", it).apply()
                                }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = providerType == KeyManager.PROVIDER_CLAUDE,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SettingsSection(title = "Claude Configuration", icon = Icons.Outlined.Memory) {
                ProviderPanel {
                    FieldHeading(
                        "Model Selection",
                        "Haiku is fastest and cheapest. Sonnet writes better. Opus is the best and costs the most."
                    )
                    CleanDropdown(
                        options = Providers.CLAUDE_MODELS,
                        selectedOption = selectedClaudeModel,
                        onOptionSelected = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedClaudeModel = it
                            prefs.edit().putString("claude_model", it).apply()
                        }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    KeyEntry(
                        description = if (claudeKeyCount == 0) "Needs an API key from console.anthropic.com (starts with sk-ant-). A Claude.ai subscription does not work here."
                                      else "$claudeKeyCount key(s) saved (encrypted). Manage them in the Keys tab.",
                        placeholder = "sk-ant-..."
                    ) { key ->
                        when {
                            KeyManager.providerForKey(key) != KeyManager.PROVIDER_CLAUDE ->
                                false to "Claude API keys start with sk-ant-"
                            keyManager.getKeys().contains(key) ->
                                false to "This key is already saved"
                            else -> {
                                val result = PythonBridge.claudeValidateKey(key)
                                if (result.isFailure) {
                                    false to (result.exceptionOrNull()?.message ?: "Validation failed")
                                } else if (!keyManager.addKey(key)) {
                                    false to "Could not encrypt the key. It was not saved."
                                } else {
                                    claudeKeyCount = keyManager.getKeysFor(KeyManager.PROVIDER_CLAUDE).size
                                    true to "Key works and is saved"
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = providerType == KeyManager.PROVIDER_OPENCODE,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SettingsSection(title = "OpenCode Configuration", icon = Icons.Outlined.Memory) {
                ProviderPanel {
                    FieldHeading(
                        "Model Selection",
                        "Chat models from OpenCode Go's model list, including Claude Haiku, Qwen and Grok"
                    )
                    if (openCodeModels.isNotEmpty()) {
                        CleanDropdown(
                            options = openCodeModels.map { it.id },
                            selectedOption = openCodeModel.ifEmpty { "Choose a model" },
                            onOptionSelected = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                openCodeModel = it
                                prefs.edit().putString("opencode_model", it).apply()
                            }
                        )
                    } else {
                        OutlinedTextField(
                            value = openCodeModel,
                            onValueChange = {
                                openCodeModel = it
                                prefs.edit().putString("opencode_model", it.trim()).apply()
                            },
                            placeholder = { Text(if (openCodeModelsError == null) "Loading models..." else "model-name") },
                            supportingText = openCodeModelsError?.let { msg -> { Text(msg) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    KeyEntry(
                        description = if (openCodeKeyCount == 0) "Paste your OpenCode Go key. It is stored encrypted and only sent to OpenCode."
                                      else "$openCodeKeyCount key(s) saved (encrypted). Manage them in the Keys tab.",
                        buttonText = "Save key"
                    ) { key ->
                        val tagged = KeyManager.tagOpenCode(key)
                        when {
                            KeyManager.providerForKey(key) != KeyManager.PROVIDER_CUSTOM ->
                                false to "This looks like a ${KeyManager.providerName(KeyManager.providerForKey(key))} key, not an OpenCode key"
                            keyManager.getKeys().contains(tagged) ->
                                false to "This key is already saved"
                            !keyManager.addKey(tagged) ->
                                false to "Could not encrypt the key. It was not saved."
                            else -> {
                                openCodeKeyCount = keyManager.getKeysFor(KeyManager.PROVIDER_OPENCODE).size
                                // OpenCode's model list is public, so a key can't be tested for free.
                                true to "Key saved. It is checked the first time you use a command."
                            }
                        }
                    }
                    if (customKeyCount > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val moved = keyManager.moveCustomKeysToOpenCode()
                                customKeyCount = keyManager.getKeysFor(KeyManager.PROVIDER_CUSTOM).size
                                openCodeKeyCount = keyManager.getKeysFor(KeyManager.PROVIDER_OPENCODE).size
                                Toast.makeText(context, "Moved $moved key(s) to OpenCode", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Move my $customKeyCount Custom key(s) to OpenCode")
                        }
                        Text(
                            text = "Only do this if those keys are OpenCode keys.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = providerType == KeyManager.PROVIDER_CUSTOM,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SettingsSection(title = "Custom Configuration", icon = Icons.Outlined.Link) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "API Endpoint",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Base URL of your OpenAI-compatible provider, usually ending in /v1. Must start with https://",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customEndpoint,
                            onValueChange = {
                                customEndpoint = it
                                // Only save HTTPS URLs (or empty). Your key and text
                                // must never travel unencrypted.
                                val cleaned = normalizeEndpoint(it)
                                if (cleaned.isEmpty() || cleaned.startsWith("https://", ignoreCase = true)) {
                                    prefs.edit().putString("custom_endpoint", cleaned).apply()
                                }
                            },
                            placeholder = { Text("https://api.example.com/v1") },
                            isError = customEndpoint.isNotBlank() &&
                                !customEndpoint.trim().startsWith("https://", ignoreCase = true),
                            supportingText = {
                                if (customEndpoint.isNotBlank() &&
                                    !customEndpoint.trim().startsWith("https://", ignoreCase = true)
                                ) {
                                    Text("Must start with https:// (not saved)")
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Model Identifier",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Exact model name from your provider's model list",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customModel,
                            onValueChange = {
                                customModel = it
                                prefs.edit().putString("custom_model", it.trim()).apply()
                            },
                            placeholder = { Text("model-name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        KeyEntry(
                            description = if (customKeyCount == 0) "No key saved yet. The key is tested against the endpoint above, then stored encrypted."
                                          else "$customKeyCount key(s) saved (encrypted). Manage them in the Keys tab."
                        ) { key ->
                            val endpoint = normalizeEndpoint(customEndpoint)
                            when {
                                KeyManager.providerForKey(key) != KeyManager.PROVIDER_CUSTOM ->
                                    false to "This looks like a ${KeyManager.providerName(KeyManager.providerForKey(key))} key. Choose that provider instead."
                                !endpoint.startsWith("https://", ignoreCase = true) ->
                                    false to "Set an https:// endpoint first"
                                keyManager.getKeys().contains(key) ->
                                    false to "This key is already saved"
                                else -> {
                                    val result = PythonBridge.openaiValidateKey(key, endpoint)
                                    if (result.isFailure) {
                                        false to (result.exceptionOrNull()?.message ?: "Validation failed")
                                    } else if (!keyManager.addKey(key)) {
                                        false to "Could not encrypt the key. It was not saved."
                                    } else {
                                        customKeyCount = keyManager.getKeysFor(KeyManager.PROVIDER_CUSTOM).size
                                        true to "Key works and is saved"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Temperature Configuration Section
        SettingsSection(title = "AI Creativity", icon = Icons.Outlined.Tune) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Temperature",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (providerType == KeyManager.PROVIDER_CLAUDE)
                            "Claude does not use this setting (current Claude models don't allow it)."
                        else
                            "Higher values make the AI more creative and less predictable.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            onValueChangeFinished = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                prefs.edit().putFloat("temperature", temperature).apply()
                            },
                            valueRange = 0f..2f,
                            steps = 19, // 0.1 increments
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f", temperature),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(28.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Trigger Configuration Section
        SettingsSection(title = "App Preferences", icon = Icons.Outlined.Keyboard) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = "Activation Symbol",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Character used to trigger Buddy before a command (e.g. ${triggerPrefix}fix)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        OutlinedTextField(
                            value = triggerPrefix,
                            onValueChange = { input ->
                                val filtered = input.take(1)
                                triggerPrefix = filtered
                                prefixError = when {
                                    filtered.length != 1 -> "Must be exactly 1 character"
                                    filtered[0].isWhitespace() -> "Cannot be whitespace"
                                    filtered[0].isLetterOrDigit() -> "Cannot be alphanumeric"
                                    else -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        commandManager.setTriggerPrefix(filtered)
                                        null
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                textAlign = TextAlign.Center, 
                                fontSize = 20.sp, 
                                fontWeight = FontWeight.ExtraBold
                            ),
                            modifier = Modifier.width(72.dp),
                            isError = prefixError != null,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                    
                    prefixError?.let { msg ->
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp).align(Alignment.End)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = "Plain punctuation",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "No em dashes (—) and no Oxford comma in AI results. Any em dash left over is turned into a comma.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = plainPunctuation,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                plainPunctuation = it
                                prefs.edit().putBoolean(PlainPunctuation.PREF_KEY, it).apply()
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(28.dp))

        // Updates (from this fork's GitHub Releases)
        SettingsSection(title = "Updates", icon = Icons.Outlined.SystemUpdate) {
            ProviderPanel {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Check for updates automatically",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Once a day when you open Buddy, it asks GitHub if there's a new version. " +
                                   "Nothing is installed without your OK. You have version ${updateManager.currentVersion}.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoUpdate,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            autoUpdate = it
                            updateManager.autoCheck = it
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        checkingUpdate = true
                        updateStatus = null
                        scope.launch {
                            updateManager.check()
                                .onSuccess { release ->
                                    if (release == null) updateStatus = "You have the latest version."
                                    else foundUpdate = release
                                }
                                .onFailure { updateStatus = "Couldn't check: ${it.message ?: "unknown error"}" }
                            checkingUpdate = false
                        }
                    },
                    enabled = !checkingUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (checkingUpdate) "Checking..." else "Check now")
                }
                updateStatus?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        foundUpdate?.let { release ->
            UpdateDialog(release) { foundUpdate = null }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Privacy: history controls
        SettingsSection(title = "Privacy", icon = Icons.Outlined.History) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = "Save history",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Keep your original text and the AI result on this phone. Off: nothing is saved.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = saveHistory,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                saveHistory = it
                                historyManager.setEnabled(it)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showClearHistoryDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear history", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text("Clear history") },
                text = { Text("Delete all saved history? This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        historyManager.clearHistory()
                        showClearHistoryDialog = false
                        Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(40.dp)) // bottom padding
    }
}

@Composable
fun SettingsSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
fun ProviderCard(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanDropdown(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** The rounded box that holds one provider's settings. */
@Composable
private fun ProviderPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun FieldHeading(title: String, description: String) {
    Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(12.dp))
}

/**
 * "API Key" field with a save button.
 * [onSave] gets the trimmed key and returns (worked, message to show).
 */
@Composable
private fun KeyEntry(
    description: String,
    placeholder: String = "Paste API key",
    buttonText: String = "Test & save key",
    onSave: suspend (String) -> Pair<Boolean, String>
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busy by remember { mutableStateOf(false) }

    FieldHeading("API Key", description)
    OutlinedTextField(
        value = input,
        onValueChange = { input = it; status = null },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            busy = true
            status = null
            scope.launch {
                val result = onSave(input.trim())
                busy = false
                status = result
                if (result.first) input = ""
            }
        },
        enabled = input.isNotBlank() && !busy,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(if (busy) "Testing..." else buttonText)
    }
    status?.let { (ok, msg) ->
        Text(
            text = msg,
            fontSize = 12.sp,
            color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * Trims the endpoint and cuts a pasted full URL back to the base URL,
 * e.g. "https://host/v1/chat/completions/" -> "https://host/v1".
 */
private fun normalizeEndpoint(raw: String): String {
    var url = raw.trim().trimEnd('/')
    if (url.endsWith("/chat/completions", ignoreCase = true)) {
        url = url.dropLast("/chat/completions".length).trimEnd('/')
    }
    return url
}
