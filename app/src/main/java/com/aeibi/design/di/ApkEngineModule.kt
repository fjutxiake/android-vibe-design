package com.aeibi.design.di

import android.content.Context
import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkPipeline
import com.aeibi.design.apk.BuildLogger
import com.aeibi.design.apk.engine.AndroidApkEngine
import com.aeibi.design.apk.engine.ApkBuilderEngine
import com.aeibi.design.apk.engine.ApkDecoder
import com.aeibi.design.apk.engine.ApkEditorLayout
import com.aeibi.design.apk.engine.ApkSigner
import com.aeibi.design.apk.engine.ApksigSigner
import com.aeibi.design.apk.engine.NoOpZipaligner
import com.aeibi.design.apk.engine.RuntimeKeystoreProvider
import com.aeibi.design.apk.engine.SigningKeyProvider
import com.aeibi.design.apk.engine.Zipaligner
import com.aeibi.design.apk.operation.AbiCleanupOperation
import com.aeibi.design.apk.operation.AppLabelOperation
import com.aeibi.design.apk.operation.AssetInjectionOperation
import com.aeibi.design.apk.operation.ConfigJsonOperation
import com.aeibi.design.apk.operation.IconOperation
import com.aeibi.design.apk.operation.PackageNameOperation
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.File
import javax.inject.Singleton

/**
 * APK 引擎装配模块——声明式插件化注册点。
 *
 * 新增修改项：实现 [ApkOperation] → 加一个 @Provides @IntoSet 方法（含 order 排序）。
 * 替换引擎：改对应 @Provides 的实现类（如手机端换成其他实现）。
 */
@Module
@InstallIn(SingletonComponent::class)
object ApkEngineModule {

    // ---- 引擎（替换点）----

    @Provides
    @Singleton
    fun provideAndroidApkEngine(@ApplicationContext context: Context): AndroidApkEngine = AndroidApkEngine(context)

    @Provides
    @Singleton
    fun provideApkDecoder(engine: AndroidApkEngine): ApkDecoder = engine

    @Provides
    @Singleton
    fun provideApkBuilder(engine: AndroidApkEngine): ApkBuilderEngine = engine

    @Provides
    @Singleton
    fun provideZipaligner(): Zipaligner = NoOpZipaligner

    @Provides
    @Singleton
    fun provideApkSigner(@ApplicationContext context: Context): ApkSigner = ApksigSigner(
        keyStorePath = File(context.filesDir, TEST_KEYSTORE).toPath(),
        alias = TEST_ALIAS,
        storePass = TEST_PASS.toCharArray()
    )

    @Provides
    @Singleton
    fun provideSigningKeyProvider(@ApplicationContext context: Context): SigningKeyProvider =
        RuntimeKeystoreProvider(File(context.filesDir, TEST_KEYSTORE))

    // ---- 修改操作（扩展点：新增修改项在此注册）----

    @Provides
    @IntoSet
    fun providePackageNameOperation(): ApkOperation = PackageNameOperation()

    @Provides
    @IntoSet
    fun provideAppLabelOperation(): ApkOperation = AppLabelOperation()

    @Provides
    @IntoSet
    fun provideIconOperation(): ApkOperation = IconOperation()

    @Provides
    @IntoSet
    fun provideConfigJsonOperation(): ApkOperation = ConfigJsonOperation()

    @Provides
    @IntoSet
    fun provideAssetInjectionOperation(): ApkOperation = AssetInjectionOperation()

    @Provides
    @IntoSet
    fun provideAbiCleanupOperation(): ApkOperation = AbiCleanupOperation()

    // ---- 管线装配 ----

    @Provides
    @Singleton
    fun provideApkPipeline(
        decoder: ApkDecoder,
        builder: ApkBuilderEngine,
        zipaligner: Zipaligner,
        signer: ApkSigner,
        signingKeyProvider: SigningKeyProvider,
        operations: Set<@JvmSuppressWildcards ApkOperation>
    ): ApkPipeline = ApkPipeline(
        decoder = decoder,
        builder = builder,
        zipaligner = zipaligner,
        signer = signer,
        layout = ApkEditorLayout(),
        signingKeyProvider = signingKeyProvider,
        operations = operations.sortedBy(ApkOperation::order),
        logger = BuildLogger { _, _ -> }
    )

    private const val TEST_KEYSTORE = "test-keystore.p12"
    private const val TEST_ALIAS = "vibe-design-test"
    private const val TEST_PASS = "vibetest"
}
