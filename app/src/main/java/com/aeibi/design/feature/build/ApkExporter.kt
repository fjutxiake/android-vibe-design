package com.aeibi.design.feature.build

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.reandroid.apk.ApkModule
import com.reandroid.app.AndroidManifest
import com.reandroid.archive.FileInputSource
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate

class ApkExporter(private val context: Context) {
    fun export(
        workspace: File,
        packageName: String,
        appName: String,
        versionCode: Int,
        versionName: String,
        output: File,
        projectIcon: File? = null
    ): File {
        val template = File.createTempFile("shell-", ".apk", context.cacheDir)
        val unsigned = File.createTempFile("unsigned-", ".apk", context.cacheDir)
        try {
            context.assets.open("shell.apk").use { input ->
                template.outputStream().use { output -> input.copyTo(output) }
            }

            ApkModule.loadApkFile(template).use { apk ->
                val templatePackageName = apk.androidManifest.packageName
                apk.setPackageName(packageName)
                rewriteDynamicReceiverPermission(
                    apk = apk,
                    templatePackageName = templatePackageName,
                    packageName = packageName
                )
                rewriteStartupProviderAuthority(
                    apk = apk,
                    templatePackageName = templatePackageName,
                    packageName = packageName
                )
                apk.androidManifest.apply {
                    setApplicationLabel(appName)
                    setVersionCode(versionCode)
                    setVersionName(versionName)
                }
                projectIcon?.takeIf(File::isFile)?.let { icon ->
                    val iconEntry = apk.tableBlock.pickOne()
                        .getOrCreate("", "drawable", PROJECT_ICON_RESOURCE_NAME)
                    iconEntry.setValueAsString(PROJECT_ICON_RESOURCE_PATH)
                    apk.androidManifest.apply {
                        setIconResourceId(iconEntry.resourceId)
                        setRoundIconResourceId(iconEntry.resourceId)
                    }
                    apk.add(FileInputSource(icon, PROJECT_ICON_RESOURCE_PATH))
                }

                apk.removeDir("META-INF")
                apk.setApkSignatureBlock(null)
                apk.removeDir("assets/frontend_app")

                workspace.walkTopDown()
                    .filter(File::isFile)
                    .forEach { file ->
                        val path = file.relativeTo(workspace).invariantSeparatorsPath
                        apk.add(FileInputSource(file, "assets/frontend_app/$path"))
                    }

                File(workspace, "vibe.config.json")
                    .takeIf(File::isFile)
                    ?.let { apk.add(FileInputSource(it, "assets/vibe.config.json")) }

                apk.writeApk(unsigned)
            }

            output.parentFile?.mkdirs()
            sign(unsigned, output)
            return output
        } finally {
            template.delete()
            unsigned.delete()
        }
    }

    private fun rewriteDynamicReceiverPermission(apk: ApkModule, templatePackageName: String?, packageName: String) {
        if (templatePackageName.isNullOrEmpty() || templatePackageName == packageName) return

        val oldPermission = "$templatePackageName.$DYNAMIC_RECEIVER_PERMISSION_SUFFIX"
        val newPermission = "$packageName.$DYNAMIC_RECEIVER_PERMISSION_SUFFIX"
        with(apk.androidManifest) {
            listOf(AndroidManifest.TAG_permission, AndroidManifest.TAG_uses_permission).forEach { tag ->
                getNamedElement(AndroidManifest.PATH_MANIFEST.element(tag), oldPermission)
                    ?.getOrCreateAndroidAttribute(AndroidManifest.NAME_name, AndroidManifest.ID_name)
                    ?.setValueAsString(newPermission)
            }
        }
    }

    private fun rewriteStartupProviderAuthority(apk: ApkModule, templatePackageName: String?, packageName: String) {
        if (templatePackageName.isNullOrEmpty() || templatePackageName == packageName) return

        val oldAuthority = "$templatePackageName.$STARTUP_PROVIDER_AUTHORITY_SUFFIX"
        val newAuthority = "$packageName.$STARTUP_PROVIDER_AUTHORITY_SUFFIX"
        apk.androidManifest.listApplicationElementsByTag(AndroidManifest.TAG_provider)
            .forEach { provider ->
                provider.searchAttributeByResourceId(AndroidManifest.ID_authorities)
                    ?.takeIf { it.valueAsString == oldAuthority }
                    ?.setValueAsString(newAuthority)
            }
    }

    private fun signingConfig(): ApkSigner.SignerConfig {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE).apply {
                initialize(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                    )
                        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                        .setSignaturePaddings(
                            KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                            KeyProperties.SIGNATURE_PADDING_RSA_PSS
                        )
                        .setKeySize(2048)
                        .build()
                )
                generateKeyPair()
            }
        }

        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        val certificates = entry.certificateChain.map { it as X509Certificate }
        return ApkSigner.SignerConfig.Builder(KEY_ALIAS, entry.privateKey, certificates).build()
    }

    private fun sign(input: File, output: File) {
        ApkSigner.Builder(listOf(signingConfig()))
            .setInputApk(input)
            .setOutputApk(output)
            .setMinSdkVersion(26)
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "vibe_design_apk_exporter_v1"
        const val PROJECT_ICON_RESOURCE_NAME = "project_icon"
        const val PROJECT_ICON_RESOURCE_PATH = "res/drawable/project_icon.png"
        const val DYNAMIC_RECEIVER_PERMISSION_SUFFIX = "DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        const val STARTUP_PROVIDER_AUTHORITY_SUFFIX = "androidx-startup"
    }
}
