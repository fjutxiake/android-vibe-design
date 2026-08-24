# Project File-System CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `ProjectsScreen` 从硬编码列表改为基于文件系统(`filesDir/projects/<uuid>/project.json`)的完整 CRUD,项目重启后仍可用。

**Architecture:** Project 走文件系统(目录 = 唯一真相),Session 继续走 Room。新增 `@Serializable Project` + `@Singleton ProjectRepository`(注入项目根目录 `File`,内部 `MutableStateFlow` 投影目录)+ `@HiltViewModel ProjectsViewModel`;Compose UI 只渲染状态、派发动作(状态提升)。Repository 通过注入 `IconCopier` 接口解耦 Android 的 `ContentResolver`,保持 JVM 可测。

**Tech Stack:** Kotlin 2.3.20、Jetpack Compose(BOM 2026.03.01)、Material3、Hilt 2.60.1 + androidx.hilt 1.4.0、kotlinx-serialization-json 1.7.3(需显式声明依赖——serialization 插件只带 core,不带 json runtime)、navigation3 1.0.1、coil3、JUnit4 + kotlinx-coroutines-test。

**Spec:** `docs/issue-6-project-crud-design.md`(本计划从该设计文档展开;执行者需同时阅读该文档)

## Global Constraints

- Kotlin 2.3.20 / JVM 17 / minSdk 26 / targetSdk 36 / compileSdk 37。
- **ktlint 14.2.0**:4 空格缩进、尾随逗号;CI(`.github/workflows/check.yml`)会跑 `ktlintCheck`,不过则挂。
- DI 注解用 `jakarta.inject.Inject` / `jakarta.inject.Singleton`(与现有 `SessionRepository`/`AppModule` 一致),`@HiltViewModel` 用 `dagger.hilt.android.lifecycle.HiltViewModel`。
- Project **不建 Room 实体**;Session 的 Room 实现不要改动。
- 时间字段统一 `Long` epoch 毫秒。
- 损坏/缺失的 `project.json` 必须跳过并记日志,不得让其它项目或整页崩溃。

---

### Task 1: Hilt 基础接线(前置)

**Files:**
- Create: `app/src/main/java/com/aeibi/design/VibeDesignApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/aeibi/design/MainActivity.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: 可用的 Hilt 运行时(`@HiltAndroidApp` Application + `@AndroidEntryPoint` Activity)、`androidx.hilt:hilt-lifecycle-viewmodel-compose:1.4.0` 依赖(`hiltViewModel()` 可用)。后续所有 Task 依赖此接线。

- [ ] **Step 1: 加 `hilt-lifecycle-viewmodel-compose` 版本号**

在 `gradle/libs.versions.toml` 的 `[versions]` 里,`hilt = "2.60.1"` 之后加一行:

```toml
androidxHilt = "1.4.0"
```

- [ ] **Step 2: 加依赖声明**

在 `[libraries]` 里,`hilt-compiler = { ... }` 之后加一行:

```toml
androidx-hilt-lifecycle-viewmodel-compose = { module = "androidx.hilt:hilt-lifecycle-viewmodel-compose", version.ref = "androidxHilt" }
```

- [ ] **Step 3: 引用依赖**

在 `app/build.gradle.kts` 的 `dependencies { }` 里,`ksp(libs.hilt.compiler)` 之后加:

```kotlin
implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
```

- [ ] **Step 4: 新建 Application 类**

创建 `app/src/main/java/com/aeibi/design/VibeDesignApplication.kt`:

```kotlin
package com.aeibi.design

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VibeDesignApplication : Application()
```

- [ ] **Step 5: 在 manifest 注册 Application**

`app/src/main/AndroidManifest.xml` 的 `<application ...>` 增加 `android:name` 属性:

```xml
<application
    android:name=".VibeDesignApplication"
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:supportsRtl="true"
    android:theme="@style/Theme.VibeDesign">
```

- [ ] **Step 6: MainActivity 加 `@AndroidEntryPoint`**

`app/src/main/java/com/aeibi/design/MainActivity.kt`:

```kotlin
package com.aeibi.design

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aeibi.design.navigation.AppNavigation
import com.aeibi.design.theme.VibeDesignTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            VibeDesignTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }
}
```

- [ ] **Step 7: 编译验证**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL(Hilt 处理器生成代码,无报错)。

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/aeibi/design/MainActivity.kt app/src/main/java/com/aeibi/design/VibeDesignApplication.kt
git commit -m "feat: bootstrap Hilt (Application + @AndroidEntryPoint + hilt-lifecycle-viewmodel-compose)"
```

---

### Task 2: Project 数据模型 + ProjectRepository + DI + 仓库单元测试

**Files:**
- Create: `app/src/main/java/com/aeibi/design/data/projects/Project.kt`
- Create: `app/src/main/java/com/aeibi/design/data/projects/IconCopier.kt`
- Create: `app/src/main/java/com/aeibi/design/data/projects/ContentResolverIconCopier.kt`
- Create: `app/src/main/java/com/aeibi/design/data/projects/ProjectRepository.kt`
- Create: `app/src/main/java/com/aeibi/design/di/ProjectModule.kt`
- Modify: `app/src/main/java/com/aeibi/design/di/AppModule.kt`
- Test: `app/src/test/java/com/aeibi/design/data/projects/ProjectRepositoryTest.kt`

**Interfaces:**
- Produces(供后续 Task 使用,签名以此为准):
  - `data class Project(id: String, name: String, description: String, icon: String? = null, createdAt: Long, updatedAt: Long)`
  - `fun interface IconCopier { fun copy(uri: String?, projectDir: File): String? }`
  - `class ProjectRepository(projectsDir: File, iconCopier: IconCopier, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`:
    - `val projects: StateFlow<List<Project>>`
    - `suspend fun refresh()`
    - `suspend fun getProject(id: String): Project?`
    - `suspend fun createProject(name: String, description: String, iconUri: String?): Project`
    - `suspend fun updateProject(id: String, name: String, description: String, iconUri: String?): Project`
    - `suspend fun deleteProject(id: String)`
    - `fun iconUri(project: Project): String?`

- [ ] **Step 1: 写失败测试**

创建 `app/src/test/java/com/aeibi/design/data/projects/ProjectRepositoryTest.kt`:

```kotlin
package com.aeibi.design.data.projects

import java.io.File
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val fakeIconCopier = IconCopier { uri, dir ->
        if (uri == null) null else File(dir, "icon.png").apply { writeText("fake") }.name
    }

    private fun repository(root: File) =
        ProjectRepository(root, fakeIconCopier, UnconfinedTestDispatcher())

    @Test
    fun createProject_writesJsonAndListsProject() = runTest {
        val repo = repository(tmp.newFolder())

        val created = repo.createProject("周末去哪", "短途路线", null)

        assertTrue(File(tmp.root, created.id).isDirectory)
        assertTrue(File(File(tmp.root, created.id), "project.json").exists())
        assertEquals("周末去哪", created.name)
        repo.refresh()
        assertEquals(listOf(created), repo.projects.value)
    }

    @Test
    fun listProjects_skipsCorruptedAndSortsByUpdatedAtDesc() = runTest {
        val root = tmp.newFolder()
        val older = File(root, "a").apply { mkdirs() }
        File(older, "project.json").writeText(
            """{"id":"a","name":"旧","description":"","icon":null,"createdAt":1,"updatedAt":100}""",
        )
        val newer = File(root, "b").apply { mkdirs() }
        File(newer, "project.json").writeText(
            """{"id":"b","name":"新","description":"","icon":null,"createdAt":1,"updatedAt":200}""",
        )
        val corrupt = File(root, "c").apply { mkdirs() }
        File(corrupt, "project.json").writeText("{not-json")

        val repo = repository(root)
        repo.refresh()

        assertEquals(listOf("b", "a"), repo.projects.value.map { it.id })
    }

    @Test
    fun updateProject_persistsChangesAndBumpsUpdatedAt() = runTest {
        val repo = repository(tmp.newFolder())
        val created = repo.createProject("旧名", "旧描述", null)

        val updated = repo.updateProject(created.id, "新名", "新描述", null)

        assertEquals("新名", updated.name)
        assertEquals("新描述", updated.description)
        assertTrue(updated.updatedAt >= created.updatedAt)
        assertEquals(updated, repo.getProject(created.id))
    }

    @Test
    fun deleteProject_removesDirectory() = runTest {
        val repo = repository(tmp.newFolder())
        val created = repo.createProject("待删", "", null)

        repo.deleteProject(created.id)

        assertTrue(!File(tmp.root, created.id).exists())
        repo.refresh()
        assertTrue(repo.projects.value.isEmpty())
    }

    @Test
    fun createProject_withIcon_copiesIconAndStoresFilename() = runTest {
        val repo = repository(tmp.newFolder())

        val created = repo.createProject("带图标", "", "content://fake/1")

        assertEquals("icon.png", created.icon)
        assertTrue(File(File(tmp.root, created.id), "icon.png").exists())
        assertTrue(repo.iconUri(created)!!.startsWith("file:"))
    }

    @Test
    fun getProject_missingOrCorrupt_returnsNull() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        assertNull(repo.getProject("nope"))

        val dir = File(root, "bad").apply { mkdirs() }
        File(dir, "project.json").writeText("{bad")
        assertNull(repo.getProject("bad"))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew testDebugUnitTest --tests "com.aeibi.design.data.projects.ProjectRepositoryTest"`
Expected: FAIL(编译错误:`ProjectRepository`、`Project`、`IconCopier` 不存在)。

- [ ] **Step 3: 写 `Project` 数据类**

创建 `app/src/main/java/com/aeibi/design/data/projects/Project.kt`:

```kotlin
package com.aeibi.design.data.projects

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val name: String,
    val description: String,
    val icon: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
```

- [ ] **Step 4: 写 `IconCopier` 接口**

创建 `app/src/main/java/com/aeibi/design/data/projects/IconCopier.kt`:

```kotlin
package com.aeibi.design.data.projects

import java.io.File

/** 把 [uri] 的内容拷贝进 [projectDir],返回图标文件名(如 "icon.png");无图标或失败返回 null。 */
fun interface IconCopier {
    fun copy(uri: String?, projectDir: File): String?
}
```

- [ ] **Step 5: 写 `ProjectRepository`**

创建 `app/src/main/java/com/aeibi/design/data/projects/ProjectRepository.kt`:

```kotlin
package com.aeibi.design.data.projects

import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ProjectRepository(
    private val projectsDir: File,
    private val iconCopier: IconCopier,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    suspend fun refresh() {
        _projects.value = withContext(ioDispatcher) { listProjects() }
    }

    suspend fun getProject(id: String): Project? = withContext(ioDispatcher) {
        readProject(File(projectsDir, id))
    }

    suspend fun createProject(name: String, description: String, iconUri: String?): Project =
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val dir = File(projectsDir, id)
            check(dir.mkdirs()) { "无法创建项目目录" }
            val icon = iconCopier.copy(iconUri, dir)
            val project = Project(
                id = id,
                name = name,
                description = description,
                icon = icon,
                createdAt = now,
                updatedAt = now,
            )
            writeProject(dir, project)
            _projects.value = listProjects()
            project
        }

    suspend fun updateProject(id: String, name: String, description: String, iconUri: String?): Project =
        withContext(ioDispatcher) {
            val dir = File(projectsDir, id)
            val existing = readProject(dir) ?: error("项目不存在: $id")
            val icon = if (iconUri != null) {
                val newIcon = iconCopier.copy(iconUri, dir)
                if (newIcon != null && newIcon != existing.icon) {
                    existing.icon?.let { File(dir, it).delete() }
                }
                newIcon ?: existing.icon
            } else {
                existing.icon
            }
            val updated = existing.copy(
                name = name,
                description = description,
                icon = icon,
                updatedAt = System.currentTimeMillis(),
            )
            writeProject(dir, updated)
            _projects.value = listProjects()
            updated
        }

    suspend fun deleteProject(id: String) = withContext(ioDispatcher) {
        File(projectsDir, id).deleteRecursively()
        _projects.value = listProjects()
    }

    fun iconUri(project: Project): String? =
        project.icon?.let { File(projectsDir, project.id).resolve(it).toURI().toString() }

    private fun listProjects(): List<Project> =
        projectsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { readProject(it) }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()

    private fun readProject(dir: File): Project? = runCatching {
        val file = File(dir, PROJECT_JSON)
        if (!file.exists()) return@runCatching null
        json.decodeFromString(Project.serializer(), file.readText())
    }.getOrNull()

    private fun writeProject(dir: File, project: Project) {
        File(dir, PROJECT_JSON).writeText(json.encodeToString(Project.serializer(), project))
    }

    private companion object {
        const val PROJECT_JSON = "project.json"
    }
}
```

- [ ] **Step 6: 写 `ContentResolverIconCopier`**

创建 `app/src/main/java/com/aeibi/design/data/projects/ContentResolverIconCopier.kt`:

```kotlin
package com.aeibi.design.data.projects

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.File

@Singleton
class ContentResolverIconCopier @Inject constructor(
    @ApplicationContext private val context: Context,
) : IconCopier {

    override fun copy(uri: String?, projectDir: File): String? {
        if (uri == null) return null
        return runCatching {
            val resolved = Uri.parse(uri)
            val mime = context.contentResolver.getType(resolved)
            val ext = mime?.substringAfter("/")?.takeIf { it.isNotBlank() } ?: "png"
            val target = File(projectDir, "icon.$ext")
            context.contentResolver.openInputStream(resolved)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.name
        }.getOrNull()
    }
}
```

- [ ] **Step 7: 写 DI(ProjectModule + AppModule 增补)**

创建 `app/src/main/java/com/aeibi/design/di/ProjectModule.kt`:

```kotlin
package com.aeibi.design.di

import com.aeibi.design.data.projects.ContentResolverIconCopier
import com.aeibi.design.data.projects.IconCopier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProjectModule {

    @Binds
    abstract fun bindIconCopier(impl: ContentResolverIconCopier): IconCopier
}
```

在 `app/src/main/java/com/aeibi/design/di/AppModule.kt` 增加两个 provider(在 `provideSessionDao` 之后、`DATABASE_NAME` 之前):

```kotlin
    @Provides
    @Singleton
    fun provideProjectsDir(@ApplicationContext context: Context): File =
        File(context.filesDir, "projects").also { it.mkdirs() }

    @Provides
    @Singleton
    fun provideProjectRepository(projectsDir: File, iconCopier: IconCopier): ProjectRepository =
        ProjectRepository(projectsDir, iconCopier)
```

同时给 `AppModule.kt` 补两个 import:

```kotlin
import com.aeibi.design.data.projects.IconCopier
import com.aeibi.design.data.projects.ProjectRepository
import java.io.File
```

- [ ] **Step 8: 运行测试确认通过**

Run: `./gradlew testDebugUnitTest --tests "com.aeibi.design.data.projects.ProjectRepositoryTest"`
Expected: PASS(6 个用例全绿)。

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/aeibi/design/data/projects/ app/src/main/java/com/aeibi/design/di/ProjectModule.kt app/src/main/java/com/aeibi/design/di/AppModule.kt app/src/test/java/com/aeibi/design/data/projects/
git commit -m "feat(data): add Project file-system repository with tests"
```

---

### Task 3: ProjectsViewModel

**Files:**
- Create: `app/src/main/java/com/aeibi/design/feature/projects/ProjectsViewModel.kt`

**Interfaces:**
- Consumes: `ProjectRepository`(Task 2)、`SessionRepository`(已有,`deleteSessionsForProject(projectId): Int`)。
- Produces(后续 Task 依赖):
  - `class ProjectsViewModel @Inject constructor(projectRepository: ProjectRepository, sessionRepository: SessionRepository)`
  - `val projects: StateFlow<List<Project>>`
  - `fun observeProject(id: String): Flow<Project?>`
  - `fun createProject(name: String, description: String, iconUri: String?)`
  - `fun updateProject(id: String, name: String, description: String, iconUri: String?)`
  - `fun deleteProject(id: String)`
  - `fun iconUri(project: Project): String?`

- [ ] **Step 1: 写 ViewModel**

创建 `app/src/main/java/com/aeibi/design/feature/projects/ProjectsViewModel.kt`:

```kotlin
package com.aeibi.design.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.data.projects.Project
import com.aeibi.design.data.projects.ProjectRepository
import com.aeibi.design.data.sessions.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val projects: StateFlow<List<Project>> = projectRepository.projects

    init {
        viewModelScope.launch { projectRepository.refresh() }
    }

    fun observeProject(id: String): Flow<Project?> =
        projects.map { list -> list.firstOrNull { it.id == id } }

    fun createProject(name: String, description: String, iconUri: String?) {
        viewModelScope.launch {
            runCatching { projectRepository.createProject(name, description, iconUri) }
        }
    }

    fun updateProject(id: String, name: String, description: String, iconUri: String?, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { projectRepository.updateProject(id, name, description, iconUri) }
            onComplete()
        }
    }

    fun deleteProject(id: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { sessionRepository.deleteSessionsForProject(id) }
            runCatching { projectRepository.deleteProject(id) }
            onComplete()
        }
    }

    fun iconUri(project: Project): String? = projectRepository.iconUri(project)
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL(Hilt 能解析 `ProjectsViewModel` 的依赖)。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/aeibi/design/feature/projects/ProjectsViewModel.kt
git commit -m "feat: add ProjectsViewModel wrapping ProjectRepository"
```

---

### Task 4: 相对时间格式化 + 单元测试

**Files:**
- Create: `app/src/main/java/com/aeibi/design/feature/projects/ProjectTimeFormat.kt`
- Test: `app/src/test/java/com/aeibi/design/feature/projects/ProjectTimeFormatTest.kt`

**Interfaces:**
- Produces: `fun formatRelativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String`

- [ ] **Step 1: 写失败测试**

创建 `app/src/test/java/com/aeibi/design/feature/projects/ProjectTimeFormatTest.kt`:

```kotlin
package com.aeibi.design.feature.projects

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectTimeFormatTest {

    private val now = 1_000_000_000_000L

    @Test
    fun justNow() = assertEquals("刚刚", formatRelativeTime(now - 30_000L, now))

    @Test
    fun minutesAgo() = assertEquals("5 分钟前", formatRelativeTime(now - 5 * 60_000L, now))

    @Test
    fun hoursAgo() = assertEquals("3 小时前", formatRelativeTime(now - 3 * 3_600_000L, now))

    @Test
    fun daysAgo() = assertEquals("2 天前", formatRelativeTime(now - 2 * 86_400_000L, now))
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew testDebugUnitTest --tests "com.aeibi.design.feature.projects.ProjectTimeFormatTest"`
Expected: FAIL(`formatRelativeTime` 未定义)。

- [ ] **Step 3: 实现**

创建 `app/src/main/java/com/aeibi/design/feature/projects/ProjectTimeFormat.kt`:

```kotlin
package com.aeibi.design.feature.projects

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatRelativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - epochMillis
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute} 分钟前"
        diff < day -> "${diff / hour} 小时前"
        diff < 7 * day -> "${diff / day} 天前"
        else -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(epochMillis))
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew testDebugUnitTest --tests "com.aeibi.design.feature.projects.ProjectTimeFormatTest"`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/aeibi/design/feature/projects/ProjectTimeFormat.kt app/src/test/java/com/aeibi/design/feature/projects/ProjectTimeFormatTest.kt
git commit -m "feat: add relative time formatter for project list"
```

---

### Task 5: ProjectsScreen 状态化(Read)+ 空态 + UI 测试重写

**Files:**
- Modify: `app/src/main/java/com/aeibi/design/feature/projects/ProjectsScreen.kt`
- Modify: `app/src/main/java/com/aeibi/design/navigation/AppNavigation.kt`
- Test: `app/src/androidTest/java/com/aeibi/design/feature/projects/ProjectsScreenTest.kt`

**Interfaces:**
- Consumes: `Project`(Task 2)、`formatRelativeTime`(Task 4)、`ProjectsViewModel`(Task 3)、`hiltViewModel`(Task 1)。
- Produces: 状态提升后的 `ProjectsScreen(projects: List<Project>, modifier, onSettingsClick, onProjectClick, onCreateProject, resolveIconUri)`(Task 6 依赖其 `onCreateProject`)。

- [ ] **Step 1: 重写 UI 测试(先写失败测试)**

重写 `app/src/androidTest/java/com/aeibi/design/feature/projects/ProjectsScreenTest.kt`:

```kotlin
package com.aeibi.design.feature.projects

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.aeibi.design.data.projects.Project
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProjectsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val sample = listOf(
        Project("1", "日常发芽", "不焦虑的日常习惯记录", null, 1L, 1L),
        Project("2", "周末去哪", "根据心情生成短途路线", null, 1L, 2L),
    )

    @Test
    fun projectItems_render() {
        composeTestRule.setContent { ProjectsScreen(projects = sample) }

        composeTestRule.onNodeWithText("日常发芽").assertIsDisplayed()
        composeTestRule.onNodeWithText("周末去哪").assertIsDisplayed()
    }

    @Test
    fun emptyState_shownWhenNoProjects() {
        composeTestRule.setContent { ProjectsScreen(projects = emptyList()) }

        composeTestRule.onNodeWithTag("empty_projects").assertIsDisplayed()
    }

    @Test
    fun clickingItem_invokesOnProjectClick() {
        var clicked: String? = null
        composeTestRule.setContent {
            ProjectsScreen(projects = sample, onProjectClick = { clicked = it })
        }

        composeTestRule.onNodeWithText("日常发芽").performClick()

        assertEquals("1", clicked)
    }
}
```

- [ ] **Step 2: 重写 `ProjectsScreen`**

重写 `app/src/main/java/com/aeibi/design/feature/projects/ProjectsScreen.kt`(整文件替换):

```kotlin
package com.aeibi.design.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.aeibi.design.data.projects.Project
import com.aeibi.design.theme.VibeDesignTheme
import com.aeibi.design.theme.spacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProjectsScreen(
    projects: List<Project>,
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {},
    onProjectClick: (String) -> Unit = {},
    onCreateProject: (name: String, description: String, iconUri: String?) -> Unit = { _, _, _ -> },
    resolveIconUri: (Project) -> String? = { null },
) {
    val spacing = MaterialTheme.spacing
    var showNewProjectSheet by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Vibe Design") },
                actions = {
                    IconButton(
                        onClick = { showNewProjectSheet = true },
                        modifier = Modifier.testTag("new_project_button"),
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "新建项目")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(imageVector = Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            items(projects, key = { it.id }) { project ->
                ProjectListItem(
                    name = project.name,
                    description = project.description,
                    updatedAt = formatRelativeTime(project.updatedAt),
                    iconUri = resolveIconUri(project),
                    onClick = { onProjectClick(project.id) },
                )
            }
            if (projects.isEmpty()) {
                item { EmptyProjectsState(modifier = Modifier.fillParentMaxSize()) }
            }
        }
    }

    if (showNewProjectSheet) {
        NewProjectBottomSheet(
            onDismiss = { showNewProjectSheet = false },
            onCreate = { name, description, iconUri ->
                onCreateProject(name, description, iconUri)
                showNewProjectSheet = false
            },
        )
    }
}

@Composable
private fun EmptyProjectsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.testTag("empty_projects"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "还没有项目,点击右上角 + 创建第一个",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProjectsScreenPreview() {
    VibeDesignTheme(dynamicColor = false) { ProjectsScreen(projects = emptyList()) }
}
```

- [ ] **Step 3: 改 `NewProjectBottomSheet` 签名(为 Task 5 编译通过)**

`app/src/main/java/com/aeibi/design/feature/projects/NewProjectBottomSheet.kt` 中,把函数签名和"创建项目"按钮改为:

函数签名(第 45 行):

```kotlin
fun NewProjectBottomSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, iconUri: String?) -> Unit,
) {
```

按钮(第 124 行):

```kotlin
                Button(onClick = { onCreate(name, description, iconUri) }, enabled = name.isNotBlank()) {
                    Text("创建项目")
                }
```

- [ ] **Step 4: 接线 navigation**

先给 `app/src/main/java/com/aeibi/design/navigation/AppNavigation.kt` 的 `NavDisplay` 加 `entryDecorators`(Navigation 3 需要显式声明 ViewModelStore 装饰器,否则 `hiltViewModel()` 拿不到 entry 作用域的 ViewModel):

```kotlin
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(rememberViewModelStoreNavEntryDecorator()),
        entryProvider =
        entryProvider {
```

再把 `entry<ProjectPicker> { ... }` 改为:

```kotlin
            entry<ProjectPicker> {
                val viewModel = hiltViewModel<ProjectsViewModel>()
                val projects by viewModel.projects.collectAsState()
                ProjectsScreen(
                    projects = projects,
                    modifier = Modifier.fillMaxSize(),
                    onSettingsClick = { backStack.add(ApplicationSettings) },
                    onProjectClick = { projectId -> backStack.add(ProjectChat(projectId)) },
                    onCreateProject = viewModel::createProject,
                    resolveIconUri = viewModel::iconUri,
                )
            }
```

并给 `AppNavigation.kt` 补 import:

```kotlin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import com.aeibi.design.feature.projects.ProjectsViewModel
```

- [ ] **Step 5: 跑单测与 UI 测试**

Run: `./gradlew testDebugUnitTest --tests "com.aeibi.design.feature.projects.ProjectTimeFormatTest"`
Expected: PASS(仍绿)。

Run: `./gradlew connectedDebugAndroidTest --tests "com.aeibi.design.feature.projects.ProjectsScreenTest"`
Expected: PASS(3 个 UI 用例全绿;若本机无模拟器/设备,可先只跑 `assembleDebugAndroidTest` 验证编译,并在提交说明里注明 UI 测试需设备)。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/aeibi/design/feature/projects/ProjectsScreen.kt app/src/main/java/com/aeibi/design/feature/projects/NewProjectBottomSheet.kt app/src/main/java/com/aeibi/design/navigation/AppNavigation.kt app/src/androidTest/java/com/aeibi/design/feature/projects/ProjectsScreenTest.kt
git commit -m "feat: render project list from file system (state hoisted)"
```

---

### Task 6: Create 接线(新建项目落盘 + 图标拷贝)

**Files:**
- Modify: `app/src/androidTest/java/com/aeibi/design/feature/projects/ProjectsScreenTest.kt`(新增用例)
- (NewProjectBottomSheet 已在 Task 5 改好,本 Task 补测试覆盖创建流程)

**Interfaces:**
- Consumes: `ProjectsScreen.onCreateProject`(Task 5)、`NewProjectBottomSheet.onCreate`(Task 5)。

- [ ] **Step 1: 补 UI 测试(先写失败测试)**

在 `ProjectsScreenTest.kt` 里新增:

```kotlin
    @Test
    fun createSheet_invokesOnCreateProject() {
        var created: Triple<String, String, String?>? = null
        composeTestRule.setContent {
            ProjectsScreen(projects = emptyList(), onCreateProject = { n, d, i ->
                created = Triple(n, d, i)
            })
        }

        composeTestRule.onNodeWithTag("new_project_button").performClick()
        composeTestRule.onNodeWithTag("project_name_input").performTextInput("新项目")
        composeTestRule.onNodeWithTag("project_description_input").performTextInput("描述")
        composeTestRule.onNodeWithText("创建项目").performClick()

        assertEquals("新项目", created?.first)
        assertEquals("描述", created?.second)
        assertNull(created?.third)
    }
```

并补 import:`import androidx.compose.ui.test.performTextInput`、`import org.junit.Assert.assertNull`。

- [ ] **Step 2: 运行确认失败/通过**

Run: `./gradlew connectedDebugAndroidTest --tests "com.aeibi.design.feature.projects.ProjectsScreenTest"`
Expected: 新用例 PASS(无设备时:先 `assembleDebugAndroidTest` 验证编译)。

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/aeibi/design/feature/projects/ProjectsScreenTest.kt
git commit -m "test: cover create-project flow in ProjectsScreenTest"
```

---

### Task 7: Update 接线(ProjectSettingsScreen 表单)

**Files:**
- Modify: `app/src/main/java/com/aeibi/design/feature/projectsettings/ProjectSettingsScreen.kt`

**Interfaces:**
- Consumes: `ProjectsViewModel.observeProject(id): Flow<Project?>`、`updateProject(id, name, description, iconUri)`(Task 3)。

- [ ] **Step 1: 重写 `ProjectSettingsScreen`**

整文件替换 `app/src/main/java/com/aeibi/design/feature/projectsettings/ProjectSettingsScreen.kt`:

```kotlin
package com.aeibi.design.feature.projectsettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aeibi.design.feature.projects.ProjectsViewModel
import com.aeibi.design.theme.spacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProjectSettingsScreen(
    projectId: String,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val project by viewModel.observeProject(projectId).collectAsState(initial = null)
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(project) {
        val current = project
        if (current != null && name.isBlank()) {
            name = current.name
            description = current.description
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("项目设置") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(MaterialTheme.spacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") },
                singleLine = true,
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("描述") },
                minLines = 3,
                maxLines = 4,
            )
            Button(
                onClick = {
                    viewModel.updateProject(projectId, name, description, iconUri = null) { onBackClick() }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
        }
    }
}
```

> 说明:本期 Update 只覆盖名称/描述;图标更新可复用 `NewProjectBottomSheet` 的图标选择逻辑,作为后续增强(设计文档 §8.3 已注明)。此处 `updateProject` 的 `iconUri = null` 会保留原图标。

- [ ] **Step 2: 编译验证**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/aeibi/design/feature/projectsettings/ProjectSettingsScreen.kt
git commit -m "feat: add project settings form (name/description update)"
```

---

### Task 8: Delete 接线 + 工作区标题加载真实项目名

**Files:**
- Modify: `app/src/main/java/com/aeibi/design/feature/workspace/ProjectActionsSheet.kt`
- Modify: `app/src/main/java/com/aeibi/design/feature/workspace/ProjectWorkspaceScreen.kt`

**Interfaces:**
- Consumes: `ProjectsViewModel.deleteProject(id)`、`observeProject(id)`(Task 3)。

- [ ] **Step 1: `ProjectActionsSheet` 加"删除项目"项**

在 `ProjectActionsSheet` 增加参数与菜单项。

签名(第 21-27 行)加 `onDeleteClick: () -> Unit`:

```kotlin
fun ProjectActionsSheet(
    onDismiss: () -> Unit,
    onBuildClick: () -> Unit,
    onVersionsClick: () -> Unit,
    onProjectSettingsClick: () -> Unit,
    onAppSettingsClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
```

在 `Column` 末尾(应用设置项之后)加:

```kotlin
            ListItem(
                headlineContent = { Text("删除项目") },
                leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                colors = ListItemDefaults.colors(
                    headlineColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.clickable {
                    onDismiss()
                    onDeleteClick()
                },
            )
```

并补 import:

```kotlin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
```

- [ ] **Step 2: `ProjectWorkspaceScreen` 加载真实标题 + 删除确认**

`ProjectWorkspaceScreen` 改为(关键改动:加 `viewModel`,标题用 `observeProject`,加删除确认 `AlertDialog`):

```kotlin
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProjectWorkspaceScreen(
    projectId: String,
    sessionId: String?,
    modifier: Modifier = Modifier,
    onProjectPickerClick: () -> Unit = {},
    onNewChatClick: () -> Unit = {},
    onSessionSelected: (String) -> Unit = {},
    onPreviewClick: () -> Unit = {},
    onBuildClick: () -> Unit = {},
    onVersionsClick: () -> Unit = {},
    onProjectSettingsClick: () -> Unit = {},
    onAppSettingsClick: () -> Unit = {},
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showProjectActions by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val project by viewModel.observeProject(projectId).collectAsState(initial = null)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawer(
                selectedSessionId = sessionId,
                onNewChatClick = {
                    scope.launch { drawerState.close() }
                    onNewChatClick()
                },
                onSessionSelected = { selectedSessionId ->
                    scope.launch { drawerState.close() }
                    onSessionSelected(selectedSessionId)
                },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) {
        Scaffold(
            topBar = {
                ProjectTopBar(
                    projectName = project?.name ?: "未命名项目",
                    onBackClick = onProjectPickerClick,
                    onSessionsClick = { scope.launch { drawerState.open() } },
                    onPreviewClick = onPreviewClick,
                    onMoreClick = { showProjectActions = true },
                )
            },
        ) { innerPadding ->
            ChatScreen(
                projectId = projectId,
                sessionId = sessionId,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }

    if (showProjectActions) {
        ProjectActionsSheet(
            onDismiss = { showProjectActions = false },
            onBuildClick = onBuildClick,
            onVersionsClick = onVersionsClick,
            onProjectSettingsClick = onProjectSettingsClick,
            onAppSettingsClick = onAppSettingsClick,
            onDeleteClick = { showDeleteConfirm = true },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除项目") },
            text = { Text("将删除该项目及其全部会话,此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteProject(projectId) { onProjectPickerClick() }
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}
```

并给 `ProjectWorkspaceScreen.kt` 补 import:

```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aeibi.design.feature.projects.ProjectsViewModel
```

> 注意:`androidx.compose.runtime.getValue` 原文件已导入,勿重复添加;`Text`、`MaterialTheme`、`AlertDialog`、`TextButton` 原文件均未导入,需如上新增。

- [ ] **Step 3: 编译验证**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/aeibi/design/feature/workspace/ProjectActionsSheet.kt app/src/main/java/com/aeibi/design/feature/workspace/ProjectWorkspaceScreen.kt
git commit -m "feat: add delete project with confirmation and real workspace title"
```

---

### Task 9: 全量校验 + 收尾

**Files:** 无新增,收尾校验。

- [ ] **Step 1: 跑全部单测**

Run: `./gradlew testDebugUnitTest`
Expected: 全部 PASS(ProjectRepositoryTest 6 例 + ProjectTimeFormatTest 4 例)。

- [ ] **Step 2: 跑 ktlint**

Run: `./gradlew ktlintCheck`
Expected: PASS。若有违规,按 ktlint 输出逐条修正(4 空格缩进、尾随逗号、import 顺序),再重跑。

- [ ] **Step 3: 编译 + 打包校验**

Run: `./gradlew assembleDebug assembleDebugAndroidTest`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 有设备则跑 UI 测试**

Run: `./gradlew connectedDebugAndroidTest`
Expected: PASS(ProjectsScreenTest 4 例)。无设备则跳过并在提交说明注明。

- [ ] **Step 5: 提交收尾(如有 ktlint 修正)**

```bash
# 只提交被 ktlint 修正的源码文件(若有);严禁 git add -A / git add .,避免误提交本地镜像配置
git add <ktlint 修正过的具体文件>
git commit -m "chore: ktlint compliance and final checks"
```

---

## 自检(Self-Review)

**1. Spec 覆盖:**
- Create(建 UUID 目录 + 写 project.json + 拷图标)→ Task 2 仓库 `createProject` + Task 6 UI;✅
- Read(去硬编码、文件系统加载、按 updatedAt 降序)→ Task 2 扫描排序 + Task 5 列表;✅
- Update(改名/描述/图标持久化)→ Task 2 `updateProject` + Task 7 表单;✅(图标更新列为后续增强,设计文档已注明)
- Delete(确认后递归删目录)→ Task 2 `deleteProject` + Task 8 UI;✅
- `ProjectRepository` + ViewModel 暴露状态、UI 只渲染+派发 → Task 2/3/5;✅
- 不建 Project Room 实体、Session 走 Room 不动 → 全程未加 Entity;✅
- 重启后可用(从文件系统加载)→ 文件系统持久化;✅
- 损坏 json 不阻断其它项目 → `readProject` runCatching 跳过,Task 2 测试覆盖;✅
- 仓库测试覆盖 create/load/update/delete → Task 2;✅ UI 测试覆盖主流程 → Task 5/6;✅

**2. Placeholder 扫描:** 无 TBD/TODO;所有代码步骤均给出完整代码块。

**3. 类型一致性:**
- `Project` 字段 `id/name/description/icon/createdAt/updatedAt` 与 `project.json` schema、测试 JSON、`ProjectListItem` 传参一致;✅
- `ProjectRepository` 公开方法签名在 Task 2 定义,Task 3/5/7/8 引用一致(`observeProject`/`createProject`/`updateProject`/`deleteProject`/`iconUri`);✅
- `NewProjectBottomSheet.onCreate` 签名 `(String, String, String?) -> Unit` 与 `ProjectsScreen.onCreateProject` 一致;✅
- `formatRelativeTime(epochMillis, now)` 在 Task 4 定义,Task 5 使用 `formatRelativeTime(project.updatedAt)`;✅
