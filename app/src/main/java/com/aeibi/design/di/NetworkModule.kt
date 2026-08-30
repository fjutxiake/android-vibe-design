package com.aeibi.design.di

import com.aeibi.design.ai.chat.AiChatService
import com.aeibi.design.ai.chat.OpenAiCompatChatService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import jakarta.inject.Singleton

/**
 * AI 聊天网络层。
 *
 * 不装 ContentNegotiation:请求体与响应体都由 OpenAiCompatChatService 手工
 * 构建与解析(JSON 字符串直发)。若装了 Json 转换器,setBody(String) 会被
 * 当作字符串值再序列化一次(整体多一层引号),导致服务端 400。
 *
 * 超时策略:连接 10s 快速暴露无网;requestTimeout 放宽到 180s —— 流式长回复
 * 不能被整体墙钟腰斩(Ktor 的 requestTimeout 是全请求时长限制)。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 180_000
            connectTimeoutMillis = 10_000
        }
    }

    @Provides
    @Singleton
    fun provideAiChatService(client: HttpClient): AiChatService = OpenAiCompatChatService(client)
}
