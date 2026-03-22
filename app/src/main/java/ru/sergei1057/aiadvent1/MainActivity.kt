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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    when (screen) {
        AppScreen.Main -> GroqChatScreen(
            maxAnswerTokens = maxAnswerTokens,
            answerJsonFormat = answerJsonFormat,
            systemPrompt = systemPrompt,
            applySystemPrompt = applySystemPrompt,
            onOpenSettings = { screen = AppScreen.Settings }
        )
        AppScreen.Settings -> SettingsScreen(
            initialMaxTokens = maxAnswerTokens,
            initialAnswerJsonFormat = answerJsonFormat,
            initialSystemPrompt = systemPrompt,
            initialApplySystemPrompt = applySystemPrompt,
            onBack = { screen = AppScreen.Main },
            onApply = { tokens, jsonFormat, prompt, useSystemPrompt ->
                maxAnswerTokens = tokens
                answerJsonFormat = jsonFormat
                systemPrompt = prompt
                applySystemPrompt = useSystemPrompt
                prefs.edit()
                    .putString(KEY_SYSTEM_PROMPT, prompt)
                    .putBoolean(KEY_APPLY_SYSTEM_PROMPT, useSystemPrompt)
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
    onBack: () -> Unit,
    onApply: (maxTokens: Int, jsonFormat: Boolean, systemPrompt: String, applySystemPrompt: Boolean) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var draft by remember { mutableStateOf(initialMaxTokens.toString()) }
    var jsonFormatChecked by remember { mutableStateOf(initialAnswerJsonFormat) }
    var systemPromptDraft by remember { mutableStateOf(initialSystemPrompt) }
    var applySystemPromptChecked by remember { mutableStateOf(initialApplySystemPrompt) }
    var error by remember { mutableStateOf<String?>(null) }

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
            Button(
                onClick = {
                    val v = draft.toIntOrNull()
                    when {
                        v == null || draft.isBlank() -> error = "Введите число"
                        v !in 1..8192 -> error = "Допустимый диапазон: 1–8192"
                        else -> onApply(v, jsonFormatChecked, systemPromptDraft, applySystemPromptChecked)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Применить изменения")
            }
        }
    }
}

private data class ChatTurn(
    val id: Long,
    val query: String,
    val answer: String,
    val loading: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroqChatScreen(
    maxAnswerTokens: Int,
    answerJsonFormat: Boolean,
    systemPrompt: String,
    applySystemPrompt: Boolean,
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
                            val result = callGroq(
                                prompt = text,
                                maxTokens = maxAnswerTokens,
                                jsonFormat = answerJsonFormat,
                                systemPrompt = systemPrompt,
                                applySystemPrompt = applySystemPrompt
                            )
                            val idx = turns.indexOfFirst { it.id == id }
                            if (idx >= 0) {
                                turns[idx] = turns[idx].copy(answer = result, loading = false)
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
    applySystemPrompt: Boolean
): String = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.GROQ_API_KEY
    if (apiKey.isBlank()) {
        return@withContext "Добавьте в local.properties строку GROQ_API_KEY=ваш_ключ (https://console.groq.com/keys)"
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
            put("model", "llama-3.3-70b-versatile")
            put("max_tokens", maxTokens)
            put("messages", messages)
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
                return@withContext "Ошибка ${response.code}: $detail"
            }

            JSONObject(responseText)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }
    } catch (e: Exception) {
        "Ошибка: ${e.localizedMessage}"
    }
}
