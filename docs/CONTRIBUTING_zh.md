# Contributing to Android Vibe Design

感谢你对 Android Vibe Design 的关注和支持。

Android Vibe Design 目前处于早期开发阶段，我们欢迎社区贡献者参与项目建设，包括功能开发、问题修复、文档完善和体验优化。

## How to Contribute

你可以通过以下方式参与项目：

- 提交 Bug 报告
- 提出功能建议
- 修复已有问题
- 改进代码质量
- 完善项目文档
- 优化用户体验

## Development Setup

### Requirements

开始开发前，请确保你的环境满足：

- Android Studio 最新版本
- JDK 17 或更高版本
- Android SDK

### Clone Repository

```bash
git clone https://github.com/fjutxiake/android-vibe-design.git
cd android-vibe-design
```

使用 Android Studio 打开项目，并等待 Gradle 完成依赖同步。

## Branch Guidelines

请基于 `master` 分支创建新的开发分支。

推荐使用以下命名方式：

```
feature/<description>
fix/<description>
docs/<description>
refactor/<description>
```

例如：

```
feature/add-export-ui
fix/preview-crash
docs/update-readme
```

## Commit Convention

本项目使用 Conventional Commits 规范。

Commit message 格式：

```
<type>: <description>
```

常用类型：

```
feat:     新功能
fix:      Bug 修复
docs:     文档修改
style:    代码格式调整
refactor: 重构代码
perf:     性能优化
test:     测试相关
chore:    构建或工具链修改
```

示例：

```bash
feat: add AI layout generation
fix: resolve preview rendering issue
docs: update contributing guide
refactor: simplify ui component structure
```

## Pull Request Guidelines

提交 Pull Request 前，请确认：

- 代码可以正常编译
- 已完成必要测试
- 修改内容符合项目设计方向
- Commit message 符合 Conventional Commits 规范
- PR 描述清楚说明修改内容和原因

PR 描述建议包含：

- 修改内容
- 解决的问题
- 测试方式
- 相关 Issue（如果有）

## Issue Guidelines

提交 Issue 时，请尽量提供：

- 问题描述
- 复现步骤
- 运行环境
- 预期行为
- 实际行为
- 相关截图或日志

提交功能建议时，请说明：

- 使用场景
- 期望解决的问题
- 可能的实现方案（如果有）

## Code Style

请保持代码：

- 简洁易读
- 遵循 Android 官方开发规范
- 避免不必要的复杂设计
- 为复杂逻辑添加必要注释

## Review Process

所有 Pull Request 都会经过审核。

审核主要关注：

- 功能正确性
- 代码质量
- 可维护性
- 项目整体一致性

根据反馈进行修改后即可继续推进合并。

## Community

感谢每一位参与 Android Vibe Design 建设的贡献者。

如果你有任何问题或建议，欢迎通过 GitHub Issue 或 Pull Request 与我们交流。