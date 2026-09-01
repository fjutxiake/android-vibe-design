package com.aeibi.design.ai

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.chatMemory.feature.InMemoryChatHistoryProvider
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResultsStreaming
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponse
import com.aeibi.design.ai.provider.AiProviderRegistry
import com.aeibi.design.ai.tools.WorkspaceTools
import com.aeibi.design.data.ai.AiProviderRepository
import com.aeibi.design.data.projects.ProjectRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList

sealed interface AgentEvent {
    data class TextDelta(val text: String) : AgentEvent
    data class ToolStarted(val name: String) : AgentEvent
    data class ToolFinished(val name: String) : AgentEvent
}

@Singleton
class KoogAgentRunner @Inject constructor(
    private val providerRepository: AiProviderRepository,
    private val providerRegistry: AiProviderRegistry,
    private val projectRepository: ProjectRepository,
    private val chatHistoryProvider: InMemoryChatHistoryProvider
) {
    suspend fun run(projectId: String, sessionId: String, input: String, onEvent: (AgentEvent) -> Unit): String {
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
        val executor = MultiLLMPromptExecutor(provider.createClient(providerConfig, apiKey))
        try {
            return executeKoogAgent(
                promptExecutor = executor,
                model = provider.createModel(modelId),
                workspaceTools = WorkspaceTools(projectRepository.workspaceDirectory(projectId)),
                chatHistoryProvider = chatHistoryProvider,
                sessionId = sessionId,
                input = input,
                onEvent = onEvent
            )
        } finally {
            executor.close()
        }
    }
}

internal suspend fun executeKoogAgent(
    promptExecutor: PromptExecutor,
    model: LLModel,
    workspaceTools: WorkspaceTools,
    chatHistoryProvider: InMemoryChatHistoryProvider,
    sessionId: String,
    input: String,
    onEvent: (AgentEvent) -> Unit
): String {
    val agent = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = model,
        strategy = streamingReActStrategy(),
        toolRegistry = ToolRegistry { tools(workspaceTools.asTools()) },
        systemPrompt = SYSTEM_PROMPT,
        maxIterations = 30
    ) {
        install(ChatMemory) {
            this.chatHistoryProvider = chatHistoryProvider
            windowSize(30)
        }
        handleEvents {
            onLLMStreamingFrameReceived { context ->
                val frame = context.streamFrame
                if (frame is StreamFrame.TextDelta && frame.text.isNotEmpty()) {
                    onEvent(AgentEvent.TextDelta(frame.text))
                }
            }
            onToolCallStarting { onEvent(AgentEvent.ToolStarted(it.toolName)) }
            onToolCallCompleted { onEvent(AgentEvent.ToolFinished(it.toolName)) }
        }
    }
    return agent.run(input, sessionId)
}

private fun streamingReActStrategy() = strategy<String, String>("streaming_react") {
    val requestModel by nodeLLMRequestStreaming().transform { frames ->
        frames.toList().toMessageResponse()
    }
    val executeTools by nodeExecuteTools(parallel = false)
    val sendToolResults by nodeLLMSendToolResultsStreaming().transform { frames ->
        frames.toList().toMessageResponse()
    }
    val rememberResponse by node<Message.Assistant, Message.Assistant> { response ->
        llm.writeSession {
            appendPrompt { message(response) }
        }
        response
    }

    edge(nodeStart forwardTo requestModel)
    edge(requestModel forwardTo rememberResponse)
    edge(rememberResponse forwardTo executeTools onToolCalls { true })
    edge(rememberResponse forwardTo nodeFinish onTextMessage { true })
    edge(executeTools forwardTo sendToolResults)
    edge(sendToolResults forwardTo rememberResponse)
}

private val SYSTEM_PROMPT = """
    You create and modify files in the current project workspace.
    Read relevant files before changing them.
    Use only relative workspace paths and preserve the existing project structure.
    When finished, briefly summarize the changes.
""".trimIndent()
