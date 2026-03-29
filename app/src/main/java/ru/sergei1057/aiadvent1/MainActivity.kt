package ru.sergei1057.aiadvent1

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import org.json.JSONArray
import org.json.JSONObject
import ru.sergei1057.aiadvent1.ui.theme.AiAdvent1Theme

private const val PREFS_NAME = "ai_advent_prefs"
private const val KEY_SYSTEM_PROMPT = "system_prompt"
private const val KEY_APPLY_SYSTEM_PROMPT = "apply_system_prompt"
private const val KEY_TEMPERATURE_ENABLED = "temperature_enabled"
private const val KEY_TEMPERATURE = "temperature"
private const val KEY_MODEL = "selected_model"

private data class GroqModel(val displayName: String, val apiId: String)

private val GROQ_MODELS = listOf(
    GroqModel("GPT OSS 120B", "openai/gpt-oss-120b"),
    GroqModel("Llama 3.3 70B Versatile", "llama-3.3-70b-versatile"),
    GroqModel("Qwen 3 32B", "qwen/qwen3-32b"),
    GroqModel("GPT OSS 20B", "openai/gpt-oss-20b")
)

private const val DEFAULT_MODEL_ID = "llama-3.3-70b-versatile"

private fun copyTextToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Ответ", text))
    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiAdvent1Theme {
                AiAdventApp()
            }
        }
    }
}

private enum class AppScreen { Main, Settings }

@Composable
fun AiAdventApp() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var systemPrompt by remember {
        mutableStateOf(prefs.getString(KEY_SYSTEM_PROMPT, "") ?: "")
    }
    var applySystemPrompt by remember {
        mutableStateOf(prefs.getBoolean(KEY_APPLY_SYSTEM_PROMPT, true))
    }

    var screen by remember { mutableStateOf(AppScreen.Main) }
    var maxAnswerTokens by remember { mutableStateOf(1024) }
    var answerJsonFormat by remember { mutableStateOf(false) }
    var temperatureEnabled by remember {
        mutableStateOf(prefs.getBoolean(KEY_TEMPERATURE_ENABLED, false))
    }
    var temperature by remember {
        mutableStateOf(java.lang.Float.intBitsToFloat(prefs.getInt(KEY_TEMPERATURE, java.lang.Float.floatToIntBits(1.0f))))
    }
    var selectedModelId by remember {
        mutableStateOf(prefs.getString(KEY_MODEL, DEFAULT_MODEL_ID) ?: DEFAULT_MODEL_ID)
    }
    when (screen) {
        AppScreen.Main -> GroqChatScreen(
            maxAnswerTokens = maxAnswerTokens,
            answerJsonFormat = answerJsonFormat,
            systemPrompt = systemPrompt,
            applySystemPrompt = applySystemPrompt,
            temperatureEnabled = temperatureEnabled,
            temperature = temperature,
            selectedModelId = selectedModelId,
            onOpenSettings = { screen = AppScreen.Settings }
        )
        AppScreen.Settings -> SettingsScreen(
            initialMaxTokens = maxAnswerTokens,
            initialAnswerJsonFormat = answerJsonFormat,
            initialSystemPrompt = systemPrompt,
            initialApplySystemPrompt = applySystemPrompt,
            initialTemperatureEnabled = temperatureEnabled,
            initialTemperature = temperature,
            initialModelId = selectedModelId,
            onBack = { screen = AppScreen.Main },
            onApply = { tokens, jsonFormat, prompt, useSystemPrompt, tempEnabled, temp, modelId ->
                maxAnswerTokens = tokens
                answerJsonFormat = jsonFormat
                systemPrompt = prompt
                applySystemPrompt = useSystemPrompt
                temperatureEnabled = tempEnabled
                temperature = temp
                selectedModelId = modelId
                prefs.edit()
                    .putString(KEY_SYSTEM_PROMPT, prompt)
                    .putBoolean(KEY_APPLY_SYSTEM_PROMPT, useSystemPrompt)
                    .putBoolean(KEY_TEMPERATURE_ENABLED, tempEnabled)
                    .putInt(KEY_TEMPERATURE, java.lang.Float.floatToIntBits(temp))
                    .putString(KEY_MODEL, modelId)
                    .apply()
                screen = AppScreen.Main
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialMaxTokens: Int,
    initialAnswerJsonFormat: Boolean,
    initialSystemPrompt: String,
    initialApplySystemPrompt: Boolean,
    initialTemperatureEnabled: Boolean,
    initialTemperature: Float,
    initialModelId: String,
    onBack: () -> Unit,
    onApply: (maxTokens: Int, jsonFormat: Boolean, systemPrompt: String, applySystemPrompt: Boolean, temperatureEnabled: Boolean, temperature: Float, modelId: String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var draft by remember { mutableStateOf(initialMaxTokens.toString()) }
    var jsonFormatChecked by remember { mutableStateOf(initialAnswerJsonFormat) }
    var systemPromptDraft by remember { mutableStateOf(initialSystemPrompt) }
    var applySystemPromptChecked by remember { mutableStateOf(initialApplySystemPrompt) }
    var temperatureChecked by remember { mutableStateOf(initialTemperatureEnabled) }
    var temperatureDraft by remember { mutableStateOf(if (initialTemperature == 1.0f) "1.0" else initialTemperature.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var temperatureError by remember { mutableStateOf<String?>(null) }
    val selectedModel by remember {
        mutableStateOf(GROQ_MODELS.find { it.apiId == initialModelId } ?: GROQ_MODELS[1])
    }
    var currentModel by remember { mutableStateOf(selectedModel) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = modelDropdownExpanded,
                onExpandedChange = { modelDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = currentModel.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Модель") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = modelDropdownExpanded,
                    onDismissRequest = { modelDropdownExpanded = false }
                ) {
                    GROQ_MODELS.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.displayName) },
                            onClick = {
                                currentModel = model
                                modelDropdownExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it.filter { ch -> ch.isDigit() }
                    error = null
                },
                label = { Text("Длина ответа") },
                supportingText = { Text("Максимум токенов в ответе модели (1–8192)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = error != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardController?.show() },
                singleLine = true
            )
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedTextField(
                value = systemPromptDraft,
                onValueChange = { systemPromptDraft = it },
                label = { Text("Системный промпт") },
                placeholder = { Text("Инструкции для модели (необязательно)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardController?.show() },
                minLines = 3,
                maxLines = 12
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = applySystemPromptChecked,
                    onCheckedChange = { applySystemPromptChecked = it }
                )
                Text(
                    text = "Применить системный промпт",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = jsonFormatChecked,
                    onCheckedChange = { jsonFormatChecked = it }
                )
                Text(
                    text = "Ответ в json формате",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = temperatureChecked,
                    onCheckedChange = {
                        temperatureChecked = it
                        temperatureError = null
                    }
                )
                Text(
                    text = "Температура",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            if (temperatureChecked) {
                OutlinedTextField(
                    value = temperatureDraft,
                    onValueChange = { raw ->
                        val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                        val dots = filtered.count { it == '.' }
                        if (dots <= 1) {
                            val parts = filtered.split(".")
                            temperatureDraft = if (parts.size == 2) {
                                parts[0] + "." + parts[1].take(1)
                            } else {
                                filtered
                            }
                        }
                        temperatureError = null
                    },
                    label = { Text("Значение температуры") },
                    supportingText = { Text("Допустимый диапазон: 0.0–2.0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = temperatureError != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) keyboardController?.show() },
                    singleLine = true
                )
                temperatureError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Button(
                onClick = {
                    val v = draft.toIntOrNull()
                    val tempValue = temperatureDraft.toFloatOrNull()
                    when {
                        v == null || draft.isBlank() -> error = "Введите число"
                        v !in 1..8192 -> error = "Допустимый диапазон: 1–8192"
                        temperatureChecked && (tempValue == null || temperatureDraft.isBlank()) ->
                            temperatureError = "Введите значение"
                        temperatureChecked && tempValue != null && tempValue !in 0.0f..2.0f ->
                            temperatureError = "Допустимый диапазон: 0.0–2.0"
                        else -> onApply(
                            v,
                            jsonFormatChecked,
                            systemPromptDraft,
                            applySystemPromptChecked,
                            temperatureChecked,
                            if (temperatureChecked) tempValue ?: 1.0f else 1.0f,
                            currentModel.apiId
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Применить изменения")
            }
        }
    }
}

private data class GroqResult(
    val text: String,
    val totalTokens: Int? = null
)

private data class ChatTurn(
    val id: Long,
    val query: String,
    val answer: String,
    val loading: Boolean,
    val elapsedSeconds: Double? = null,
    val totalTokens: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroqChatScreen(
    maxAnswerTokens: Int,
    answerJsonFormat: Boolean,
    systemPrompt: String,
    applySystemPrompt: Boolean,
    temperatureEnabled: Boolean,
    temperature: Float,
    selectedModelId: String,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    val turns = remember { mutableStateListOf<ChatTurn>() }
    val listState = rememberLazyListState()
    val isAwaitingAnswer = turns.any { it.loading }

    LaunchedEffect(turns.size, turns.lastOrNull()?.loading) {
        if (turns.isNotEmpty()) {
            listState.animateScrollToItem(turns.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Chat (Groq)") },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text("Настройки")
                    }
                }
            )
        },
        bottomBar = {
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Запрос") },
                    placeholder = { Text("Введите ваш вопрос...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) keyboardController?.show() },
                    minLines = 2,
                    maxLines = 5,
                    enabled = !isAwaitingAnswer
                )
                Button(
                    onClick = {
                        val text = prompt.trim()
                        if (text.isBlank()) return@Button
                        val id = System.nanoTime()
                        turns.add(
                            ChatTurn(
                                id = id,
                                query = text,
                                answer = "",
                                loading = true
                            )
                        )
                        prompt = ""
                        scope.launch {
                            val startMs = System.currentTimeMillis()
                            val result = callGroq(
                                prompt = text,
                                maxTokens = maxAnswerTokens,
                                jsonFormat = answerJsonFormat,
                                systemPrompt = systemPrompt,
                                applySystemPrompt = applySystemPrompt,
                                temperatureEnabled = temperatureEnabled,
                                temperature = temperature,
                                model = selectedModelId
                            )
                            val elapsed = (System.currentTimeMillis() - startMs) / 1000.0
                            val idx = turns.indexOfFirst { it.id == id }
                            if (idx >= 0) {
                                turns[idx] = turns[idx].copy(
                                    answer = result.text,
                                    loading = false,
                                    elapsedSeconds = elapsed,
                                    totalTokens = result.totalTokens
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAwaitingAnswer && prompt.isNotBlank()
                ) {
                    Text("Отправить")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (turns.isEmpty()) {
                item {
                    Text(
                        text = "История пуста. Введите запрос внизу — здесь появятся все сообщения текущей сессии.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(
                items = turns,
                key = { it.id }
            ) { turn ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Запрос:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = turn.query,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    val answerDisplayText =
                        if (!turn.loading) {
                            if (answerJsonFormat && !turn.answer.startsWith("Ошибка")) {
                                formatJsonForDisplay(turn.answer)
                            } else {
                                turn.answer
                            }
                        } else {
                            ""
                        }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ответ:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (!turn.loading) {
                            IconButton(
                                onClick = { copyTextToClipboard(context, answerDisplayText) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Копировать ответ"
                                )
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (turn.loading) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            Text(
                                text = answerDisplayText,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = if (answerJsonFormat && !turn.answer.startsWith("Ошибка")) {
                                    FontFamily.Monospace
                                } else {
                                    FontFamily.Default
                                }
                            )
                        }
                    }
                    if (!turn.loading && (turn.elapsedSeconds != null || turn.totalTokens != null)) {
                        val parts = buildList {
                            turn.elapsedSeconds?.let { add("Время: ${"%.1f".format(it)} сек") }
                            turn.totalTokens?.let { add("Токены: $it") }
                        }
                        Text(
                            text = parts.joinToString("   "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// 10.0.2.2 — стандартный адрес хост-машины из Android-эмулятора
private val httpClient = OkHttpClient.Builder()
    .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("10.0.2.2", 12334)))
    .build()

private fun formatJsonForDisplay(raw: String): String {
    val t = raw.trim()
    return runCatching {
        when {
            t.startsWith("{") -> JSONObject(t).toString(2)
            t.startsWith("[") -> JSONArray(t).toString(2)
            else -> raw
        }
    }.getOrDefault(raw)
}

// Ключ задаётся в local.properties: GROQ_API_KEY=gsk_...
private suspend fun callGroq(
    prompt: String,
    maxTokens: Int,
    jsonFormat: Boolean,
    systemPrompt: String,
    applySystemPrompt: Boolean,
    temperatureEnabled: Boolean = false,
    temperature: Float = 1.0f,
    model: String = DEFAULT_MODEL_ID
): GroqResult = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.GROQ_API_KEY
    if (apiKey.isBlank()) {
        return@withContext GroqResult("Добавьте в local.properties строку GROQ_API_KEY=ваш_ключ (https://console.groq.com/keys)")
    }
    try {
        val messages = JSONArray()
        val customSystem = systemPrompt.trim()
        if (applySystemPrompt && customSystem.isNotEmpty()) {
            messages.put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", customSystem)
                }
            )
        }
        if (jsonFormat) {
            messages.put(
                JSONObject().apply {
                    put("role", "system")
                    put(
                        "content",
                        "Отвечай только валидным JSON (объект или массив). Без markdown, без текста до или после JSON."
                    )
                }
            )
        }
        messages.put(
            JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }
        )

        val requestJson = JSONObject().apply {
            put("model", model)
            put("max_tokens", maxTokens)
            put("messages", messages)
            if (temperatureEnabled) {
                put("temperature", temperature.toDouble())
            }
            if (jsonFormat) {
                put(
                    "response_format",
                    JSONObject().apply { put("type", "json_object") }
                )
            }
        }

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseText = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(responseText).optJSONObject("error")?.optString("message") ?: responseText
                }.getOrDefault(responseText)
                return@withContext GroqResult("Ошибка ${response.code}: $detail")
            }

            val json = JSONObject(responseText)
            val text = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
            val totalTokens = json.optJSONObject("usage")?.optInt("total_tokens")
            GroqResult(text = text, totalTokens = totalTokens)
        }
    } catch (e: Exception) {
        GroqResult("Ошибка: ${e.localizedMessage}")
    }
}
