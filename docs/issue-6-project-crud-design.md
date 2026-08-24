# 技术方案:基于文件系统的 Project CRUD(issue #6)

> 状态:草案
> 关联 Issue:[#6] [Feature] Add File System–Based CRUD for Projects](https://github.com/fjutxiake/android-vibe-design/issues/6)
> 分支:`feature/6-file-system-crud-projects`
> 基线:`bf98fd4`(含 #5 数据层、#11 ktlint/CI)

---

## 1. 背景与目标

当前 `ProjectsScreen` 是纯静态 UI:

- 项目列表为硬编码数据(`ProjectsScreen.kt:63-134`,共 9 个写死的 item);
- "创建项目"按钮只关闭底部弹窗,不落盘(`NewProjectBottomSheet.kt:124`);
- 读取 / 更新 / 删除项目均未实现;
- 用户创建的项目无法在重启后恢复。

**目标:** 以 `context.filesDir/projects/<projectId>/` 目录作为每个项目的唯一数据源(单一事实来源),实现 Project 的增删改查,并让项目在重启后依然可用。

**非目标:**

- 不为 Project 建立 Room 实体 / 表 / 索引(与 issue 明确要求一致);
- 不改动现有 Session 的 Room 实现(两者并行:Session 走 Room,Project 走文件系统);
- 不引入文件监听(`FileObserver`/`WatchService`)等过度设计。

---

## 2. 现状盘点

### 2.1 相关文件与现状

| 文件 | 现状 | 对本方案的影响 |
|---|---|---|
| `feature/projects/ProjectsScreen.kt` | 硬编码 9 个 `ProjectListItem`;新建按钮只 `showNewProjectSheet = true` | 列表改为从状态流渲染;按钮保持不变 |
| `feature/projects/NewProjectBottomSheet.kt` | 已收集 `name`/`description`/`iconUri`;"创建项目"按钮 `onClick = onDismiss`(L124),仅 `name.isNotBlank()` 才可点 | 增加 `onCreate(name, description, iconUri)` 回调;图标需落盘 |
| `feature/projects/ProjectListItem.kt` | 已支持 `iconUri: String?` 参数(L40),图标走 `AsyncImage` + fallback | UI 无需大改,只改数据来源 |
| `navigation/NavigationKeys.kt` | `ProjectChat(projectId: String, sessionId: String? = null)` 等,`projectId` 直接用字符串 | 与 UUID 字符串天然契合 |
| `navigation/AppNavigation.kt` | `ProjectPicker` → `ProjectsScreen(onProjectClick = { backStack.add(ProjectChat(it)) })`(L47-55) | 列表点击进入工作区的链路已通 |
| `feature/projectsettings/ProjectSettingsScreen.kt` | 空占位,仅显示 `"项目 $projectId 的设置区域"` | **Update 的落点**:改造为名称/描述/图标编辑表单 |
| `feature/workspace/ProjectActionsSheet.kt` | 4 个菜单项(构建/版本/项目设置/应用设置),无重命名/删除 | **Delete 的入口**:新增"删除项目"(含二次确认) |
| `feature/workspace/ProjectWorkspaceScreen.kt` | 顶部标题硬编码 `projectName = "未命名项目"`(L60) | 需从仓库按 `projectId` 读取真实项目名 |
| 各 `*ViewModel.kt` | 全部为空壳 `: ViewModel()` | 需新建真正持有状态的 `ProjectsViewModel` |
| `data/sessions/*` | Room 完整可用;`SessionDao.deleteSessionsForProject`(L42)已就绪 | 删除项目时级联清理其会话 |
| 测试 | `androidTest/.../ProjectsScreenTest.kt` 断言硬编码文案("日常发芽"等) | **必须重写**;仓库单测需新建 `app/src/test` |

### 2.2 依赖现状(`libs.versions.toml` / `app/build.gradle.kts`)

- 已引入 `kotlinx-serialization` 插件 → `project.json` 读写可用 `@Serializable` + `Json`;
- 已引入 `hilt`、`room3`、`ktlint`、`navigation3`、`coil3`、`material-icons-extended`;
- **关键缺口:Hilt 尚未接线。** 全仓库无 `@HiltAndroidApp` 的 `Application` 类、`MainActivity` 无 `@AndroidEntryPoint`、`AndroidManifest.xml` 的 `<application>` 无 `android:name`。当前只有 `di/AppModule.kt` 提供 `AppDatabase`/`SessionDao`,但没有任何消费方。**使用 `@HiltViewModel` / `@Inject` 前必须先补 Hilt 基础接线(见 §5)。**

### 2.3 ViewModel 获取方式(待确认点)

仓库当前没有任何 ViewModel 被 Compose 获取的先例。已引入 `androidx-lifecycle-viewmodel-navigation3`。**确认结论:**navigation3 下获取 `@HiltViewModel` 用 `androidx.hilt:hilt-lifecycle-viewmodel-compose`(包 `androidx.hilt.lifecycle.viewmodel.compose` 的 `hiltViewModel()`),**不要**引入 `androidx.hilt:hilt-navigation-compose`(那是 Navigation 2 的,会传递依赖 `androidx.navigation`);同时 `NavDisplay` 需加 `rememberViewModelStoreNavEntryDecorator()` 装饰器,`hiltViewModel()` 才能按 back stack entry 作用域获取 ViewModel。

---

## 3. 总体设计

### 3.1 目录结构

```
context.filesDir/
└── projects/
    └── <projectId>/            # 一个 UUID 一个目录
        ├── project.json        # 元数据(唯一真相)
        └── icon.<ext>          # 选中的图标,可选
```

- 项目列表 = 扫描 `projects/` 下所有子目录并解析各自的 `project.json`;
- 项目删除 = 递归删除整个目录;
- 未来需要导入/导出/备份时,直接拷贝目录即可(issue 的"Alternatives Considered"强调的优势)。

### 3.2 `project.json` schema

```json
{
  "id": "e3871c40-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "name": "周末去哪",
  "description": "根据心情生成短途路线",
  "icon": "icon.png",
  "createdAt": 1720000000000,
  "updatedAt": 1720000000000
}
```

- `icon` 为项目目录内图标文件名(相对路径),未选图标时为 `null`;
- `createdAt` / `updatedAt` 为 epoch 毫秒(`Long`),与 `SessionEntity` 的时间字段类型一致。

---

## 4. 数据模型

新建 `data/projects/Project.kt`:

```kotlin
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

不建 Room `@Entity`;`Project` 仅通过 `Json` 序列化落盘。

---

## 5. 数据层:`ProjectRepository`

新建 `data/projects/ProjectRepository.kt`,参照 `SessionRepository` 的薄封装风格,但直接操作文件系统。

### 5.1 可测试性设计:注入 projects 根目录

为避免 JVM 单测依赖 Android `Context`,**Repository 不直接依赖 `Context`**,而是注入项目根目录 `File`。由 Hilt 从 `@ApplicationContext` 派生并注入(见 §6):

```kotlin
@Singleton
class ProjectRepository @Inject constructor(
    private val projectsDir: File,        // = context.filesDir/projects
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    // ...
}
```

这样仓库逻辑在本地单测中用临时目录即可驱动,无需 Robolectric。

### 5.2 公共 API

```kotlin
fun observeProjects(): StateFlow<List<Project>>   // 供 UI 订阅
suspend fun getProject(id: String): Project?        // 工作区/设置页按 id 读取单个项目
suspend fun createProject(name: String, description: String, iconUri: String?): Project
suspend fun updateProject(id: String, name: String, description: String, iconUri: String?): Project
suspend fun deleteProject(id: String)
```

### 5.3 内部实现要点

- **扫描**:`listProjects()` 在 `Dispatchers.IO` 上遍历 `projectsDir` 子目录,逐个解析 `project.json`,过滤掉解析失败项(见 §5.5),按 `updatedAt` 降序排序;
- **观察机制**:内部持有一个 `MutableStateFlow<List<Project>>`;每次增/改/删落盘后重新扫描并 `update` 该 Flow;ViewModel 在 `init` 里触发首次 `refresh()`。**目录仍是唯一真相,Flow 只是目录的投影**,不维护第二份持久化状态,规避双写不一致;
- **Create**:`UUID.randomUUID().toString()` 生成 id → `mkdirs()` 建目录 → 写入 `project.json`(`createdAt = updatedAt = now`)→ 若有 `iconUri` 则拷贝图标 → 落盘完成后 `refresh()`;
- **Update**:读取旧 `Project` → 用新 name/description 覆写、`updatedAt = now` → 写回 `project.json` → 若有新 `iconUri` 则替换图标,否则保留原图标 → `refresh()`;
- **Delete**:`projectsDir/<id>.deleteRecursively()` → `refresh()`。

### 5.4 图标落盘

`NewProjectBottomSheet` 通过 `PickVisualMedia` 拿到的是 `content://` URI,不能直接持久化,需拷贝进项目目录:

1. 经 `ContentResolver` 解析 MIME / 扩展名(如 `image/png` → `png`),得到目标文件名 `icon.<ext>`;
2. `contentResolver.openInputStream(uri)` 拷贝到 `projectsDir/<id>/icon.<ext>`,原子写入(先写临时文件再 rename);
3. `Project.icon` 只存文件名;展示时由 UI 层把 `File(projectsDir/<id>, icon)` 转成可加载地址(Coil3 的 `ImageRequest.data()` 支持 `File` / `file://` Uri)。

### 5.5 容错(issue 明确要求)

- 某个 `project.json` 缺失或损坏(JSON 解析失败)时,**跳过该项目、记录日志,不影响其它有效项目展示**;
- 子目录下没有合法 `project.json` 的,整体忽略该目录;
- 所有文件 IO 用 `runCatching` 包裹并降级,避免单个坏文件导致整页崩溃。

---

## 6. DI 集成

### 6.1 补 Hilt 基础接线(前置条件)

1. 新建 `VibeDesignApplication : Application`,标注 `@HiltAndroidApp`;
2. `AndroidManifest.xml` 的 `<application>` 增加 `android:name=".VibeDesignApplication"`;
3. `MainActivity` 标注 `@AndroidEntryPoint`。

### 6.2 提供 Project 依赖

扩展 `di/AppModule.kt`(或新增 `ProjectModule`):

```kotlin
@Provides
@Singleton
fun provideProjectsDir(@ApplicationContext context: Context): File =
    File(context.filesDir, "projects").also { it.mkdirs() }

@Provides
@Singleton
fun provideProjectRepository(projectsDir: File): ProjectRepository =
    ProjectRepository(projectsDir)
```

---

## 7. ViewModel:`ProjectsViewModel`

新建 `feature/projects/ProjectsViewModel.kt`,作为列表与增删改的统一状态入口:

```kotlin
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,  // 删除项目时级联清理会话
) : ViewModel() {

    val projects: StateFlow<List<Project>>

    // 事件:创建成功/失败、更新成功/失败、删除确认等(供 UI 弹提示/收底部弹窗)
    // 通过 sealed UiEvent + 一次性 Channel 或 StateFlow 暴露

    fun createProject(name: String, description: String, iconUri: String?)
    fun updateProject(id: String, name: String, description: String, iconUri: String?)
    fun deleteProject(id: String)
}
```

**关键点:**

- 所有动作在 `viewModelScope` 内执行,`repository` 内部切 `Dispatchers.IO`;
- `deleteProject` 前先 `sessionRepository.deleteSessionsForProject(id)`,再删除项目目录(顺序:先删会话再删目录,避免目录已删而会话残留);
- 工作区 / 设置页按 `projectId` 读取单个项目,可复用同一 Repository(`getProject`),或单独建轻量 ViewModel。

---

## 8. UI 接线

### 8.1 Read —— `ProjectsScreen`

- 签名改为接收 `projects: List<Project>`(或直接 `viewModel.projects`)+ 动作回调;
- `LazyColumn` 由 `projects.forEach { ProjectListItem(...) }` 渲染,删除硬编码 9 个 item;
- `ProjectListItem` 的 `updatedAt` 由 `Long` 时间戳格式化为相对时间(与现有"刚刚/昨天/8月6日"风格一致),`iconUri` 由 `Project.icon` 解析为可加载地址;
- 空列表时展示空态(可选,本期可先做简单占位)。

### 8.2 Create —— `NewProjectBottomSheet`

- 增加 `onCreate: (name, description, iconUri) -> Unit` 参数;
- "创建项目"按钮 `onClick = { onCreate(name, description, iconUri) }`,成功后由父级关闭弹窗(`showNewProjectSheet = false`);
- 校验:仍以 `name.isNotBlank()` 作为可点条件(desc 可空,图标可空)。

### 8.3 Update —— `ProjectSettingsScreen`

- 由空占位改为"名称 / 描述 / 图标"编辑表单(复用 `NewProjectBottomSheet` 的输入与图标选择逻辑,可抽成公共组件或复制最小实现);
- 通过 `projectId` 读取当前项目回填,保存时调 `updateProject`。

### 8.4 Delete —— `ProjectActionsSheet`

- 新增"删除项目"菜单项(红色强调),点击弹二次确认对话框;
- 确认后调 `deleteProject`,删除成功返回项目列表(`onProjectPickerClick` 回退导航)。

### 8.5 工作区标题

- `ProjectWorkspaceScreen` / `ProjectTopBar` 不再硬编码"未命名项目",改为按 `projectId` 读取真实 `name`(读不到则回退到"未命名项目")。

---

## 9. 测试方案

### 9.1 仓库单元测试(新建 `app/src/test`)

`ProjectRepositoryTest` 用 `JUnit4` 临时目录(`@get:Rule TemporaryFolder` 或 `kotlin.io.path.createTempDirectory`)构造 `ProjectRepository(projectsDir)`:

- **创建**:创建后目录存在、`project.json` 字段正确、出现在列表;
- **加载**:预置若干目录 + 合法/损坏 `project.json`,断言合法项被列出、损坏项被跳过、按 `updatedAt` 降序;
- **更新**:更新 name/description 后 `updatedAt` 变大、内容持久化;
- **删除**:目录被递归删除、列表不再包含;
- **图标拷贝**:传入 `iconUri` 后目录内出现 `icon.<ext>` 且 `Project.icon` 正确(此条需 mock `ContentResolver`,可拆为独立用例或放到 instrumentation 测试)。

### 9.2 UI 测试(重写 `ProjectsScreenTest`)

- 移除对"日常发芽 / 周末去哪 / 专注计时器"硬编码文案的断言;
- 改为注入固定状态 / 假仓库:断言空态与若干项目渲染、点击列表项触发回调、创建流程写入后列表刷新;
- 若走全链路 instrumentation,用 Hilt 测试注入指向临时目录的 `ProjectRepository`,覆盖"创建 → 列表出现 → 删除 → 列表消失"主流程(issue 要求覆盖主要管理流程)。

---

## 10. 兼容性与注意事项

1. **ktlint 14.2.0**:新增代码必须过 ktlint(4 空格缩进、尾随逗号),否则 CI(`.github/workflows/check.yml`)会挂;
2. **图标持久化**:`content://` URI 不能跨重启依赖,必须拷贝字节进项目目录;
3. **会话级联**:删除项目时清理 `SessionDao.deleteSessionsForProject` 对应记录,避免孤儿会话;
4. **并发**:文件 IO 统一走 `Dispatchers.IO`,不阻塞主线程;`MutableStateFlow` 更新回主线程收集;
5. **时间字段**:统一 `Long` epoch 毫秒,与 `SessionEntity` 一致。

---

## 11. 边界情况与风险

| 场景 | 处理 |
|---|---|
| `project.json` 缺失 / 损坏 | 跳过该目录 + 日志,不影响其它项目 |
| `projects/` 目录不存在 | 首次访问前 `mkdirs()` 兜底 |
| 图标 URI 失效 / 不可读 | 拷贝失败则忽略图标(`icon = null`),不阻断创建 |
| 删除当前打开中的项目 | 删除成功后回退到列表;工作区若残留需能优雅降级("未命名项目"兜底) |
| 同名项目 | 允许(id 用 UUID 区分,无唯一性约束) |
| `updatedAt` 排序 | 默认降序,创建/更新时刷新 |

---

## 12. 未决问题(实现阶段确认)

1. navigation3 下 ViewModel 的获取 API 与作用域(`lifecycle-viewmodel-navigation3` 的具体用法),确认后再定 `ProjectsViewModel` 如何注入到各 Composable;
2. `NewProjectBottomSheet` 与 `ProjectSettingsScreen` 的编辑表单是否抽公共组件;
3. 相对时间格式化("刚刚/昨天/N 天前")是内联实现还是引入工具类;
4. 删除确认 UI 用 Material3 `AlertDialog` 还是自绘 BottomSheet。

---

## 13. 实施步骤(概要)

1. 补 Hilt 基础接线(Application + `@AndroidEntryPoint` + manifest);
2. `Project` 数据类 + `ProjectRepository` + DI;
3. `ProjectsViewModel` + `ProjectsScreen` 列表状态化(Read);
4. Create 接线(底部弹窗落盘 + 图标拷贝);
5. Update 接线(`ProjectSettingsScreen` 表单);
6. Delete 接线(`ProjectActionsSheet` + 确认 + 会话级联);
7. 工作区标题按 `projectId` 加载;
8. 测试:新增仓库单测 + 重写 UI 测试;
9. ktlint 全量检查 + 提交。
