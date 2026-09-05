import java.net.URL

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.aeibi.design"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.aeibi.design"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndkVersion = libs.versions.ndk.get()
        ndk {
            // 原型阶段只出 arm64（真机）与 x86_64（模拟器）两个 ABI。
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("zh", "en")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

// libgit2 源码不入库;首次构建自动下载解压,CMake 直接可用。干净克隆与 CI
// 无需任何手工步骤。需要代理的机器在用户级 gradle.properties 配 systemProp。
val fetchLibgit2 by tasks.registering {
    // 局部变量而非脚本顶层属性:配置缓存禁止 doLast 捕获脚本对象引用。
    val targetDir: File = file("src/main/cpp/libgit2")
    val version = "1.9.7"
    val archiveFile: File = File(projectDir, "build/tmp/libgit2-$version.tar.gz")
    outputs.dir(targetDir)
    onlyIf { !targetDir.resolve("include/git2.h").isFile }
    doLast {
        if (!archiveFile.isFile) {
            println("Downloading libgit2 v$version source ...")
            URL("https://github.com/libgit2/libgit2/archive/refs/tags/v$version.tar.gz")
                .openStream().use { input -> archiveFile.outputStream().use { output -> input.copyTo(output) } }
        }
        targetDir.deleteRecursively()
        targetDir.mkdirs()
        // 系统 tar(Windows 10+ 自带 bsdtar)解压并剥掉顶层目录。tests/ 夹具含符号链接
        // 和异形文件名,Windows 上解不开且构建不需要(BUILD_TESTS=OFF)。bsdtar 对
        // strip 后的名字做排除匹配,GNU tar 对原始名字,两种模式都传以兼容两者。
        val extract = ProcessBuilder(
            "tar",
            "-xzf",
            archiveFile.absolutePath,
            "-C",
            targetDir.absolutePath,
            "--strip-components=1",
            "--exclude=tests",
            "--exclude=tests/*",
            "--exclude=libgit2-$version/tests",
            "--exclude=libgit2-$version/tests/*"
        )
            .redirectErrorStream(true)
            .start()
        val output = extract.inputStream.bufferedReader().readText()
        extract.waitFor()
        // Windows bsdtar 对 tests/ 里个别异形字节名的夹具仍会报错(目录整体已排除,
        // 但该条目名字本身无法匹配排除模式),tar 因此非零退出。构建只依赖源码树,
        // 校验必需路径完整即视为成功;缺失才是真失败。
        val essential = listOf(
            "CMakeLists.txt",
            "include/git2.h",
            "src/libgit2/commit.c",
            "src/util/CMakeLists.txt",
            "deps/llhttp/CMakeLists.txt",
            "deps/zlib/CMakeLists.txt"
        )
        val missing = essential.filter { !targetDir.resolve(it).isFile }
        check(missing.isEmpty()) { "解压 libgit2 失败(缺失 ${missing.joinToString()}): $output" }
        if (extract.exitValue() != 0) {
            println("libgit2 解压有非致命报错(tar exit=${extract.exitValue()}): $output")
        }
    }
}
tasks.named("preBuild") { dependsOn(fetchLibgit2) }

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    val ktorBom = platform(libs.ktor.bom)
    implementation(composeBom)
    implementation(ktorBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)
    implementation(libs.ucrop)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    // Serialization
    implementation(libs.kotlinx.serialization.json)
    // Agent
    implementation(libs.koog.agents.core)
    implementation(libs.koog.agents.features.event.handler)
    implementation(libs.koog.prompt.executor.openai.client)
    implementation(libs.koog.prompt.executor.deepseek.client)
    implementation(libs.koog.http.client.ktor)
    implementation(libs.ktor.client.okhttp)
    // Local static file server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Local tests: jUnit, coroutines, Android runner
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koog.agents.test)

    // Instrumented tests: jUnit rules and runners
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Local database
    implementation(libs.androidx.room3.runtime)
    ksp(libs.androidx.room3.compiler)

    // Dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
}
