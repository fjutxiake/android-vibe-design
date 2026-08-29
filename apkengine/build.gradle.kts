plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.reandroid"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // ARSCLib 为第三方移植源码，lint 分析器在其上会内部崩溃
        // （NoSuchMethodError: JavaDocParser.parseDataItem），跳过 lint
        abortOnError = false
    }
}

// 第三方源码无需 Android lint 分析，跳过 lint 任务（lintAnalyzeDebug 崩溃为工具自身 bug）
// 注意：不能误伤 ktlint 任务（名称也含 "lint"）
tasks.configureEach {
    if (name.startsWith("lint") && !name.contains("ktlint", ignoreCase = true)) {
        enabled = false
    }
}
