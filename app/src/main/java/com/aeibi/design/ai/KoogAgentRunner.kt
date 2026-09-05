package com.aeibi.design.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMSendMessageStreaming
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponse
import com.aeibi.design.ai.provider.AiProviderRegistry
import com.aeibi.design.ai.tools.RuntimeLogsTool
import com.aeibi.design.ai.tools.WorkspaceTools
import com.aeibi.design.data.agentmemory.AgentMemory
import com.aeibi.design.data.ai.AiProviderRepository
import com.aeibi.design.data.projectfiles.ProjectFileTools
import com.aeibi.design.data.projects.ProjectRepository
import com.aeibi.design.data.runtimelogs.RuntimeLogStore
import com.aeibi.design.data.sessions.AgentFailure
import com.aeibi.design.data.sessions.MessageOrigin
import com.aeibi.design.data.sessions.SessionRepository
import com.aeibi.design.data.sessions.TurnStatus
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList

sealed interface AgentEvent {
    data object ResponseStarted : AgentEvent
    data class TextDelta(val text: String) : AgentEvent
    data class ReasoningDelta(val text: String) : AgentEvent
    data class ToolStarted(val name: String) : AgentEvent
    data class ToolFinished(val name: String) : AgentEvent
}

@Singleton
class KoogAgentRunner @Inject constructor(
    private val providerRepository: AiProviderRepository,
    private val providerRegistry: AiProviderRegistry,
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
    private val runtimeLogStore: RuntimeLogStore
) {
    suspend fun run(projectId: String, sessionId: String, input: String, onEvent: (AgentEvent) -> Unit): String {
        val turnId = UUID.randomUUID().toString()
        val pendingText = StringBuilder()
        val pendingReasoning = StringBuilder()
        var executor: MultiLLMPromptExecutor? = null
        var sessionRunStarted = false
        try {
            sessionRepository.beginSessionRun(sessionId)
            sessionRunStarted = true
            val modelMessages = sessionRepository.loadModelMessages(sessionId)
            sessionRepository.appendMessage(
                sessionId,
                turnId,
                MessageOrigin.USER,
                Message.User(input, RequestMetaInfo.Empty)
            )

            val settings = providerRepository.settings.first()
            val providerConfig = settings.providers.firstOrNull { it.id == settings.selectedProviderId }
                ?: error("Select an AI provider before starting a chat")
            val modelId = settings.selectedModelId
                ?.takeIf(providerConfig.models::contains)
                ?: error("Select a model before starting a chat")
            val apiKey = providerRepository.readApiKey(providerConfig.id)
                ?.takeIf(String::isNotBlank)
                ?: error("The selected provider has no API key")
            checkNotNull(projectRepository.getProject(projectId)) { "Project not found: $projectId" }

            val provider = providerRegistry.get(providerConfig.providerType)
            val createdExecutor = MultiLLMPromptExecutor(provider.createClient(providerConfig, apiKey))
            executor = createdExecutor
            val response = executeKoogAgent(
                promptExecutor = createdExecutor,
                model = provider.createModel(modelId),
                workspaceTools = WorkspaceTools(ProjectFileTools(projectRepository.workspaceDirectory(projectId))),
                runtimeLogsTool = RuntimeLogsTool(projectId, runtimeLogStore),
                sessionRepository = sessionRepository,
                sessionId = sessionId,
                turnId = turnId,
                input = input,
                modelMessages = modelMessages,
                persistUserMessage = false,
                memoryInjection = AgentMemory(
                    projectRepository.workspaceDirectory(projectId),
                    sessionId
                ).readInjection(),
                onEvent = { event ->
                    when (event) {
                        is AgentEvent.TextDelta -> pendingText.append(event.text)
                        is AgentEvent.ReasoningDelta -> pendingReasoning.append(event.text)
                        else -> Unit
                    }
                    onEvent(event)
                },
                onAssistantMessageStored = {
                    pendingText.clear()
                    pendingReasoning.clear()
                }
            )
            sessionRepository.finishTurn(sessionId, turnId, TurnStatus.COMPLETE)
            return response
        } catch (error: CancellationException) {
            sessionRepository.finishTurn(
                sessionId = sessionId,
                turnId = turnId,
                status = TurnStatus.CANCELLED,
                partialResponse = pendingText.toString().takeIf(String::isNotEmpty),
                partialReasoning = pendingReasoning.toString().takeIf(String::isNotEmpty)
            )
            throw error
        } catch (error: Exception) {
            sessionRepository.finishTurn(
                sessionId = sessionId,
                turnId = turnId,
                status = TurnStatus.FAILED,
                failure = error.toAgentFailure(),
                partialResponse = pendingText.toString().takeIf(String::isNotEmpty),
                partialReasoning = pendingReasoning.toString().takeIf(String::isNotEmpty)
            )
            throw error
        } finally {
            try {
                executor?.close()
            } finally {
                if (sessionRunStarted) sessionRepository.endSessionRun(sessionId)
            }
        }
    }
}

internal suspend fun executeKoogAgent(
    promptExecutor: PromptExecutor,
    model: LLModel,
    workspaceTools: WorkspaceTools,
    runtimeLogsTool: RuntimeLogsTool,
    sessionRepository: SessionRepository,
    sessionId: String,
    turnId: String,
    input: String,
    modelMessages: List<Message>? = null,
    persistUserMessage: Boolean = true,
    memoryInjection: String? = null,
    onEvent: (AgentEvent) -> Unit,
    onAssistantMessageStored: () -> Unit = {}
): String {
    val history = modelMessages ?: sessionRepository.loadModelMessages(sessionId)
    val agent = AIAgent(
        promptExecutor = promptExecutor,
        strategy = streamingReActStrategy(
            sessionRepository,
            sessionId,
            turnId,
            persistUserMessage,
            onEvent,
            onAssistantMessageStored
        ),
        toolRegistry = ToolRegistry {
            tools(workspaceTools.asTools())
            tools(runtimeLogsTool.asTools())
        },
        agentConfig = AIAgentConfig(
            prompt = prompt(sessionId) {
                system(SYSTEM_PROMPT)
                // workspace 记忆/skill 投影——紧跟主提示的静态段（缓存友好），无文件时为零注入。
                memoryInjection?.let { system(it) }
                history.forEach(::message)
            },
            model = model,
            maxAgentIterations = 30
        )
    ) {
        handleEvents {
            onLLMStreamingFrameReceived { context ->
                val frame = context.streamFrame
                if (frame is StreamFrame.TextDelta && frame.text.isNotEmpty()) {
                    onEvent(AgentEvent.TextDelta(frame.text))
                }
                if (frame is StreamFrame.ReasoningDelta) {
                    (frame.summary ?: frame.text)
                        ?.takeIf(String::isNotBlank)
                        ?.let { onEvent(AgentEvent.ReasoningDelta(it)) }
                }
            }
            onToolCallStarting { onEvent(AgentEvent.ToolStarted(it.toolName)) }
            onToolCallCompleted { onEvent(AgentEvent.ToolFinished(it.toolName)) }
        }
    }
    return agent.run(input, sessionId)
}

private fun streamingReActStrategy(
    sessionRepository: SessionRepository,
    sessionId: String,
    turnId: String,
    persistUserMessage: Boolean,
    onEvent: (AgentEvent) -> Unit,
    onAssistantMessageStored: () -> Unit
) = strategy<String, String>("streaming_react") {
    val appendUserMessage by node<String, Message.User> { input ->
        Message.User(input, RequestMetaInfo.Empty).also {
            if (persistUserMessage) {
                sessionRepository.appendMessage(sessionId, turnId, MessageOrigin.USER, it)
            }
        }
    }
    val requestModel by nodeLLMSendMessageStreaming().transform { frames ->
        onEvent(AgentEvent.ResponseStarted)
        frames.toList().toMessageResponse()
    }
    val executeTools by nodeExecuteTools(parallel = false)
    val appendToolResults by node<ai.koog.agents.core.dsl.extension.ReceivedToolResults, Message.User> { results ->
        Message.User(
            results.toolResults.map { it.toMessagePart() },
            RequestMetaInfo.Empty
        ).also {
            sessionRepository.appendMessage(sessionId, turnId, MessageOrigin.TOOL, it)
        }
    }
    val sendToolResults by nodeLLMSendMessageStreaming().transform { frames ->
        onEvent(AgentEvent.ResponseStarted)
        frames.toList().toMessageResponse()
    }
    val appendAssistantMessage by node<Message.Assistant, Message.Assistant> { response ->
        sessionRepository.appendMessage(sessionId, turnId, MessageOrigin.ASSISTANT, response)
        onAssistantMessageStored()
        llm.writeSession {
            appendPrompt { message(response) }
        }
        response
    }

    edge(nodeStart forwardTo appendUserMessage)
    edge(appendUserMessage forwardTo requestModel)
    edge(requestModel forwardTo appendAssistantMessage)
    edge(appendAssistantMessage forwardTo executeTools onToolCalls { true })
    edge(appendAssistantMessage forwardTo nodeFinish onTextMessage { true })
    edge(executeTools forwardTo appendToolResults)
    edge(appendToolResults forwardTo sendToolResults)
    edge(sendToolResults forwardTo appendAssistantMessage)
}

private fun Exception.toAgentFailure(): AgentFailure {
    val message = message ?: javaClass.simpleName
    val code = when {
        message.contains("has no API key", ignoreCase = true) ||
            message.contains("Select an AI provider", ignoreCase = true) ||
            message.contains("Select a model", ignoreCase = true) -> "CONFIGURATION"
        message.contains("quota", ignoreCase = true) ||
            message.contains("insufficient", ignoreCase = true) -> "QUOTA"
        message.contains("rate limit", ignoreCase = true) || message.contains("429") -> "RATE_LIMIT"
        message.contains("401") ||
            message.contains("403") ||
            message.contains("unauthorized", ignoreCase = true) ||
            message.contains("invalid API key", ignoreCase = true) -> "AUTHENTICATION"
        this is java.net.SocketTimeoutException -> "TIMEOUT"
        message.contains("timeout", ignoreCase = true) -> "TIMEOUT"
        this is java.io.IOException -> "NETWORK"
        Regex("\\b5\\d{2}\\b").containsMatchIn(message) -> "SERVER"
        else -> "UNKNOWN"
    }
    return AgentFailure(message, code)
}

private val SYSTEM_PROMPT = """
    You create and modify files in the current project workspace.
    Read relevant files before changing them.
    Use only relative workspace paths and preserve the existing project structure.
    When finished, briefly summarize the changes.
""".trimIndent()
