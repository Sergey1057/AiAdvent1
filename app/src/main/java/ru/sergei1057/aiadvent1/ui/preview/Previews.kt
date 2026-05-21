package ru.sergei1057.aiadvent1.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ru.sergei1057.aiadvent1.ChatTurn
import ru.sergei1057.aiadvent1.GroqChatScreen
import ru.sergei1057.aiadvent1.SettingsScreen
import ru.sergei1057.aiadvent1.ui.theme.AiAdvent1Theme

@Preview(name = "Настройки", showBackground = true, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    AiAdvent1Theme(dynamicColor = false) {
        SettingsScreen(
            initialMaxTokens = 1024,
            initialAnswerJsonFormat = false,
            initialSystemPrompt = "Отвечай кратко и по делу.",
            initialApplySystemPrompt = true,
            initialTemperatureEnabled = false,
            initialTemperature = 1.0f,
            initialModelId = "GigaChat",
            onBack = {},
            onApply = { _, _, _, _, _, _, _ -> }
        )
    }
}

@Preview(name = "Настройки — GigaChat", showBackground = true)
@Composable
private fun SettingsScreenGigaChatPreview() {
    AiAdvent1Theme(dynamicColor = false) {
        SettingsScreen(
            initialMaxTokens = 2048,
            initialAnswerJsonFormat = true,
            initialSystemPrompt = "",
            initialApplySystemPrompt = false,
            initialTemperatureEnabled = true,
            initialTemperature = 0.7f,
            initialModelId = "GigaChat",
            onBack = {},
            onApply = { _, _, _, _, _, _, _ -> }
        )
    }
}

@Preview(name = "Чат — пустой", showBackground = true, showSystemUi = true)
@Composable
private fun GroqChatScreenEmptyPreview() {
    AiAdvent1Theme(dynamicColor = false) {
        GroqChatScreen(
            maxAnswerTokens = 1024,
            answerJsonFormat = false,
            systemPrompt = "",
            applySystemPrompt = true,
            temperatureEnabled = false,
            temperature = 1.0f,
            selectedModelId = "GigaChat",
            onOpenSettings = {}
        )
    }
}

@Preview(name = "Чат — с историей", showBackground = true, showSystemUi = true)
@Composable
private fun GroqChatScreenWithHistoryPreview() {
    AiAdvent1Theme(dynamicColor = false) {
        GroqChatScreen(
            maxAnswerTokens = 1024,
            answerJsonFormat = false,
            systemPrompt = "",
            applySystemPrompt = true,
            temperatureEnabled = false,
            temperature = 1.0f,
            selectedModelId = "GigaChat",
            onOpenSettings = {},
            initialTurns = listOf(
                ChatTurn(
                    id = 1L,
                    query = "Что такое Kotlin?",
                    answer = "Kotlin — статически типизированный язык для JVM и Android.",
                    loading = false,
                    elapsedSeconds = 1.2,
                    totalTokens = 42
                ),
                ChatTurn(
                    id = 2L,
                    query = "Приведи пример data class",
                    answer = "```kotlin\ndata class User(val name: String, val age: Int)\n```",
                    loading = false,
                    elapsedSeconds = 2.8,
                    totalTokens = 89
                )
            )
        )
    }
}

@Preview(name = "Чат — загрузка", showBackground = true)
@Composable
private fun GroqChatScreenLoadingPreview() {
    AiAdvent1Theme(dynamicColor = false) {
        GroqChatScreen(
            maxAnswerTokens = 1024,
            answerJsonFormat = false,
            systemPrompt = "",
            applySystemPrompt = true,
            temperatureEnabled = false,
            temperature = 1.0f,
            selectedModelId = "GigaChat-Pro",
            onOpenSettings = {},
            initialTurns = listOf(
                ChatTurn(
                    id = 1L,
                    query = "Сколько планет в Солнечной системе?",
                    answer = "",
                    loading = true
                )
            )
        )
    }
}
