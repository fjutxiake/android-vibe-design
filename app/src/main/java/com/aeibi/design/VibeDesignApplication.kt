package com.aeibi.design

import android.app.Application
import com.aeibi.design.data.messages.MessageRepository
import dagger.hilt.android.HiltAndroidApp
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class VibeDesignApplication : Application() {

    @Inject
    lateinit var messageRepository: MessageRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 启动恢复:上次运行若在 assistant 生成中途退出,遗留的 STREAMING 条目
        // 在此确定性地收敛为 INTERRUPTED(#23 接入真实产生源后此不变式生效)。
        applicationScope.launch {
            runCatching { messageRepository.reconcileInterruptedEntries() }
        }
    }
}
