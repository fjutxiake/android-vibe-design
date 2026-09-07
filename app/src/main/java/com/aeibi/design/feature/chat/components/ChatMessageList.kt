package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aeibi.design.feature.chat.ChatTimelineItem
import com.aeibi.design.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop

/**
 * 消息列表——Column（非虚拟化）。
 *
 * 真机二分实验结论：卡源 = markdown 渲染树组合/布局成本（纯文本同文本量不卡）。
 * Column 让消息**常驻组合**——组合成本只在消息出现时付一次，滚动零组合——
 * 用「消息出现时的一次性成本」换「滚动全程无卡」。
 *
 * 自动跟随（追尾）是一个 01 开关，**只被用户手势改写**，绝不被位置/像素范围/
 * 程序化滚动改写。手势复杂（拖、甩、按住、混合），以**最后手势**为准：
 *   · 手势上滚（拖/甩离底部）→ 断
 *   · 手势触底（下滚活动帧到达 maxValue）或松手在底 / 甩尾触底 → 通
 *   · 按住（拖拽中停住超过阈值）→ 断
 *
 * 三个真机实证的设计要点：
 * 1. 分类器只吃手势活动帧（isScrollInProgress）——自动滚动（跟随器自己的
 *    scrollTo）与内容收缩造成的 value 变化不驱动开关，否则自动滚会把自己
 *    "滚"成一次触底、开关被打回 ON。
 * 2. 跟随器是常驻收集器，每个生长事件发射点实时读开关——不靠 effect 重启
 *    来停（重帧下取消滞后 = 迟到 scrollTo 的来源）。
 * 3. 开关状态不随 sessionId 重建（rememberSaveable 无 key + 会话切换显式
 *    重置）——keyed scrollState 的 effect 在会话切换时不重启，若状态被
 *    sessionId key 重建会出现新旧两状态并存（手势写旧、跟随器读新）。
 */
@Composable
fun ChatMessageList(
    projectId: String,
    sessionId: String?,
    timeline: List<ChatTimelineItem>,
    isLoading: Boolean,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val scrollState = rememberScrollState()

    // 拖拽会话中（手指按下并越过 touch slop 后，直到松手）——期间自动滚动一律让位。
    // 组合期读委托值；协程内读 State 本体。
    val isDraggedState = scrollState.interactionSource.collectIsDraggedAsState()
    val isDragged by isDraggedState

    val followTailState = rememberSaveable { mutableStateOf(true) }
    var followTail by followTailState

    fun setFollow(value: Boolean) {
        followTail = value
    }

    // 会话切换：显式重置开关（见文件头第 3 点）。
    LaunchedEffect(sessionId) {
        setFollow(true)
    }

    // 手势分类器：拖拽与甩动（fling）都逐帧改变 value，同一差分器覆盖两者。
    // 不做 repeat/轮询/范围——每帧一个方向判定，快速上划在离开底部的第一帧
    // 就被判为读历史（像素范围方案会漏掉这个窗口：甩动起点仍贴着底）。
    // fling 到底时最后一次 clamp 推送发生在 isScrollInProgress 熄灭之后
    // （真机实证），但它紧跟手势活动帧——250ms 窗口内的触底帧仍算用户姿态。
    LaunchedEffect(scrollState) {
        var lastValue = scrollState.value
        var lastActiveNanos = 0L
        snapshotFlow { scrollState.isScrollInProgress to scrollState.value }
            .collect { (inProgress, value) ->
                val delta = value - lastValue
                if (delta != 0) {
                    lastValue = value
                    if (inProgress) {
                        lastActiveNanos = System.nanoTime()
                        if (delta < 0) {
                            setFollow(false)
                        } else if (value >= scrollState.maxValue) {
                            setFollow(true)
                        }
                    } else if (
                        delta > 0 &&
                        value >= scrollState.maxValue &&
                        System.nanoTime() - lastActiveNanos < FLING_TAIL_NS
                    ) {
                        setFollow(true)
                    }
                }
            }
    }

    // 松手判定：拖拽结束且松手位置在底（瞬时触底姿态）→ 通。
    // 手指抬起那刻的 value 就是用户"最后姿态"——不依赖差分帧（重帧下差分会被
    // 合并且带过期 isScrollInProgress）。若松手后甩动继续上滚，手势帧会立刻
    // 把它打回断，自校正。
    LaunchedEffect(scrollState) {
        var prev = isDraggedState.value
        snapshotFlow { isDraggedState.value }
            .collect { now ->
                if (now != prev) {
                    prev = now
                    if (!now && scrollState.value >= scrollState.maxValue) {
                        setFollow(true)
                    }
                }
            }
    }

    // 按住：拖拽会话中停下不动超过阈值 = 明确"按住"手势 → 断。
    // （停下期间内容仍在生长，若不判按住，松手后下一次生长会把人拽走。）
    LaunchedEffect(scrollState) {
        while (true) {
            if (isDraggedState.value) {
                val base = scrollState.value
                delay(HOLD_BREAK_MS)
                if (isDraggedState.value && scrollState.value == base) {
                    setFollow(false)
                }
            } else {
                delay(HOLD_BREAK_MS)
            }
        }
    }

    // 发送 / 新回合开始 = 明确的跟随意图（false→true 沿只触发一次，不打扰中途读历史）。
    LaunchedEffect(isRunning) {
        if (isRunning) setFollow(true)
    }

    // 增量跟随：常驻收集器 + 发射点实时读开关（见文件头第 2 点）。开关通且用户
    // 未介入（无拖拽、无滚动动画）时，内容高度每增长一次滚到新底一次——
    // markdown 异步渲染再撑高会再次触发，天然收敛。
    LaunchedEffect(sessionId, isLoading) {
        if (isLoading) return@LaunchedEffect
        snapshotFlow { scrollState.maxValue }
            .drop(1)
            .collect {
                if (followTail && !isDraggedState.value && !scrollState.isScrollInProgress) {
                    scrollState.scrollTo(scrollState.maxValue)
                }
            }
    }

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (timeline.isEmpty()) {
        ChatEmptyState(projectId = projectId, sessionId = sessionId, modifier = modifier.fillMaxSize())
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        timeline.forEach { item ->
            when (item) {
                is ChatTimelineItem.Message -> ChatMessageItem(item, Modifier.fillMaxWidth())
                is ChatTimelineItem.Thinking -> ThinkingItem(item, Modifier.fillMaxWidth())
                is ChatTimelineItem.ToolCall -> ToolEventItem(item)
                is ChatTimelineItem.ToolResult -> ToolEventItem(item)
            }
        }
    }
}

/** 按住判定阈值：拖拽会话中停止移动超过此时长视为"按住"手势（断开跟随）。 */
private const val HOLD_BREAK_MS = 220L

/** 甩尾窗口：p=false 的触底帧距最近手势活动帧在此时间内则视为用户甩尾。 */
private const val FLING_TAIL_NS = 250_000_000L
