package com.aeibi.design.data.ai

import com.aeibi.design.ai.provider.ProviderConfig
import com.aeibi.design.data.sessions.SessionEntity

/** 一次生效的 provider/model 选择:发送路径与展示路径共用同一解析结果。 */
data class ProviderSelection(val config: ProviderConfig, val model: String)

/**
 * 两级选择模型的唯一实现:会话绑定优先;未绑定或绑定指向已删配置时回退
 * 全局默认。发送路径(解析请求参数)与展示路径(顶栏/面板当前值)都调它,
 * 两条路径不可能漂移。
 */
fun resolveSelection(
    session: SessionEntity?,
    providers: List<ProviderConfig>,
    default: DefaultProviderSelection
): ProviderSelection? {
    session?.let { bound ->
        val model = bound.model ?: return@let
        providers.firstOrNull { it.id == bound.providerConfigId }?.let { config ->
            return ProviderSelection(config, model)
        }
    }
    if (!default.isSet) return null
    val config = providers.firstOrNull { it.id == default.providerConfigId } ?: return null
    return ProviderSelection(config, default.model!!)
}
