package com.aeibi.design.apk

import com.aeibi.design.apk.operation.AbiCleanupOperation
import com.aeibi.design.apk.operation.AppLabelOperation
import com.aeibi.design.apk.operation.AssetInjectionOperation
import com.aeibi.design.apk.operation.ConfigJsonOperation
import com.aeibi.design.apk.operation.IconOperation
import com.aeibi.design.apk.operation.PackageNameOperation

/**
 * 默认装配：产品当前全部修改项的注册表。
 *
 * 新增修改项时：实现 [ApkOperation] → 加入 [defaultOperations]（
 * 或按需传入 [ApkPipeline] 的 operations 参数，保持默认列表不动）。
 */
object DefaultPipeline {

    /** 默认明文修改操作列表（按执行顺序）。 */
    val defaultOperations: List<ApkOperation> =
        listOf(
            PackageNameOperation(),
            AppLabelOperation(),
            IconOperation(),
            ConfigJsonOperation(),
            AssetInjectionOperation(),
            AbiCleanupOperation()
        )
}
