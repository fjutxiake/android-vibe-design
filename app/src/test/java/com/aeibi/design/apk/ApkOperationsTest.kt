package com.aeibi.design.apk

import com.aeibi.design.apk.engine.ApkEditorLayout
import com.aeibi.design.apk.model.ApkBuildRequest
import com.aeibi.design.apk.operation.AbiCleanupOperation
import com.aeibi.design.apk.operation.AppLabelOperation
import com.aeibi.design.apk.operation.AssetInjectionOperation
import com.aeibi.design.apk.operation.ConfigJsonOperation
import com.aeibi.design.apk.operation.IconOperation
import com.aeibi.design.apk.operation.PackageNameOperation
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkOperationsTest {

    private val template: Path = Files.createTempDirectory("template")

    private fun manifest(packageName: String): Path = template.resolve("AndroidManifest.xml").also { file ->
        Files.writeString(
            file,
            """<?xml version='1.0' encoding='utf-8' ?>
          |<manifest package="$packageName">
          |  <application android:name="$packageName.App" />
          |</manifest>
          |
            """.trimMargin()
        )
    }

    private fun manifestWithPermissionAndAuthority(packageName: String): Path =
        template.resolve("AndroidManifest.xml").also { file ->
            Files.writeString(
                file,
                """
                |<?xml version='1.0' encoding='utf-8' ?>
                |<manifest package="$packageName">
                |  <permission android:name="$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
                |  <uses-permission android:name="$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
                |  <application android:name="$packageName.App">
                |    <provider android:authorities="$packageName.startup" />
                |  </application>
                |</manifest>
                |
                """.trimMargin()
            )
        }

    private fun stringsXml(label: String): Path =
        template.resolve("resources/package_1/res/values/strings.xml").also { file ->
            Files.createDirectories(file.parent)
            Files.writeString(
                file,
                """<?xml version='1.0' encoding='utf-8' ?>
          |<resources>
          |  <string name="app_name">$label</string>
          |</resources>
          |
                """.trimMargin()
            )
        }

    private fun request(vararg overrides: Pair<String, Any>): ApkBuildRequest {
        val values = overrides.toMap()
        return ApkBuildRequest(
            templateApk = template.resolve("shell.apk"),
            outputApk = template.resolve("out.apk"),
            packageName = values["packageName"] as String?,
            appLabel = values["appLabel"] as String?,
            iconFile = values["iconFile"] as Path?,
            frontendDir = values["frontendDir"] as Path?,
            config = values["config"] as Map<String, String>?,
            abiWhitelist = values["abiWhitelist"] as Set<String>?
        )
    }

    private fun context(request: ApkBuildRequest, log: (String) -> Unit = {}): ApkOperationContext =
        ApkOperationContext(
            decodedDir = template,
            layout = ApkEditorLayout(),
            request = request,
            log = log
        )

    @Test
    fun `修改包名-改package和权限声明但不动组件类名`() {
        val manifestFile = manifestWithPermissionAndAuthority("com.aeibi.design")
        val operation = PackageNameOperation()
        val logs = mutableListOf<String>()

        operation.apply(context(request("packageName" to "com.vibetest.demo"), logs::add))

        val content = Files.readString(manifestFile)
        assertTrue("package 属性未改:\n$content", content.contains("""package="com.vibetest.demo""""))
        // 权限声明/引用跟随新包名（避免与已安装旧包冲突）
        assertTrue(
            "permission 未跟随:\n$content",
            content.contains("""android:name="com.vibetest.demo.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"""")
        )
        // provider authorities 跟随
        assertTrue(
            "authorities 未跟随:\n$content",
            content.contains("""android:authorities="com.vibetest.demo.startup"""")
        )
        // 组件类名必须保留（dex 类未变）
        assertTrue("组件类名被误改:\n$content", content.contains("com.aeibi.design.App"))
        assertEquals(listOf("包名: com.aeibi.design → com.vibetest.demo（权限/authority 跟随，组件类名保持模板）"), logs)
    }

    @Test
    fun `修改包名-请求为null时跳过`() {
        val manifestFile = manifest("com.aeibi.design")
        val operation = PackageNameOperation()
        val logs = mutableListOf<String>()

        operation.apply(context(request(), logs::add))

        assertTrue(logs.isEmpty())
        assertTrue(Files.readString(manifestFile).contains("com.aeibi.design"))
    }

    @Test
    fun `修改应用名-替换app_name`() {
        stringsXml("Vibe Design")
        val operation = AppLabelOperation()

        operation.apply(context(request("appLabel" to "玩具测试")))

        val content = Files.readString(template.resolve("resources/package_1/res/values/strings.xml"))
        assertTrue(content.contains("<string name=\"app_name\">玩具测试</string>"))
        assertFalse(content.contains("Vibe Design"))
    }

    @Test
    fun `写入配置-生成app_config_json`() {
        val operation = ConfigJsonOperation()

        operation.apply(context(request("config" to mapOf("entry" to "index.html", "port" to "8080"))))

        val configFile = template.resolve("root/assets/app_config.json")
        assertTrue(Files.exists(configFile))
        val content = Files.readString(configFile)
        assertTrue(content.contains("\"entry\": \"index.html\""))
        assertTrue(content.contains("\"port\": \"8080\""))
    }

    @Test
    fun `注入前端产物-整棵目录复制`() {
        val frontend = Files.createTempDirectory("frontend")
        Files.writeString(frontend.resolve("index.html"), "<html/>")
        Files.createDirectories(frontend.resolve("assets"))
        Files.writeString(frontend.resolve("assets/logo.png"), "png")
        val operation = AssetInjectionOperation()

        operation.apply(context(request("frontendDir" to frontend)))

        val target = template.resolve("root/assets/frontend_app")
        assertTrue(Files.exists(target.resolve("index.html")))
        assertTrue(Files.exists(target.resolve("assets/logo.png")))
    }

    @Test
    fun `清理ABI-仅保留白名单`() {
        listOf("arm64-v8a", "armeabi-v7a", "x86").forEach { abi ->
            Files.createDirectories(template.resolve("root/lib/$abi"))
        }
        val operation = AbiCleanupOperation()

        operation.apply(context(request("abiWhitelist" to setOf("arm64-v8a"))))

        assertTrue(Files.isDirectory(template.resolve("root/lib/arm64-v8a")))
        assertFalse(Files.exists(template.resolve("root/lib/armeabi-v7a")))
        assertFalse(Files.exists(template.resolve("root/lib/x86")))
    }

    @Test
    fun `替换图标-覆盖全部密度目录的ic_launcher`() {
        listOf("mipmap-mdpi", "mipmap-xxhdpi").forEach { density ->
            Files.createDirectories(template.resolve("resources/package_1/res/$density"))
            Files.writeString(template.resolve("resources/package_1/res/$density/ic_launcher.webp"), "old")
        }
        val icon = Files.createTempFile("icon", ".png")
        Files.writeString(icon, "new-icon")
        val operation = IconOperation()

        operation.apply(context(request("iconFile" to icon)))

        listOf("mipmap-mdpi", "mipmap-xxhdpi").forEach { density ->
            assertEquals(
                "new-icon",
                Files.readString(template.resolve("resources/package_1/res/$density/ic_launcher.webp"))
            )
        }
    }

    @Test
    fun `替换图标-不触碰anydpi的adaptive图标xml`() {
        Files.createDirectories(template.resolve("resources/package_1/res/mipmap-anydpi"))
        Files.writeString(template.resolve("resources/package_1/res/mipmap-anydpi/ic_launcher.xml"), "<adaptive-icon/>")
        val icon = Files.createTempFile("icon", ".png")
        Files.writeString(icon, "new-icon")
        val operation = IconOperation()

        operation.apply(context(request("iconFile" to icon)))

        assertEquals(
            "<adaptive-icon/>",
            Files.readString(template.resolve("resources/package_1/res/mipmap-anydpi/ic_launcher.xml"))
        )
    }
}
