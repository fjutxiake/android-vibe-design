# Contributing to Android Vibe Design

Thank you for your interest in Android Vibe Design.

Android Vibe Design is currently in an early stage of development. Contributions of all kinds are welcome, including bug fixes, new features, documentation improvements, and user experience enhancements.

## How to Contribute

You can contribute by:

- Reporting bugs
- Suggesting new features
- Fixing existing issues
- Improving code quality
- Improving documentation
- Improving the user experience

## Development Setup

### Requirements

Before getting started, make sure you have:

- The latest version of Android Studio
- JDK 17 or later
- Android SDK

### Clone the Repository

```bash
git clone https://github.com/fjutxiake/android-vibe-design.git
cd android-vibe-design
```

Open the project in Android Studio and wait for Gradle to finish syncing dependencies.

## Branch Guidelines

Create a new branch from `master` before making changes.

Recommended branch naming conventions:

```text
feature/<description>
fix/<description>
docs/<description>
refactor/<description>
```

Examples:

```text
feature/add-export-ui
fix/preview-crash
docs/update-readme
```

## Commit Convention

This project follows the [Conventional Commits](https://www.conventionalcommits.org/) specification.

Commit messages should use the following format:

```text
<type>: <description>
```

Common commit types:

```text
feat:     A new feature
fix:      A bug fix
docs:     Documentation changes
style:    Code style or formatting changes
refactor: Code changes that do not fix bugs or add features
perf:     Performance improvements
test:     Adding or updating tests
chore:    Build, tooling, or maintenance changes
```

Examples:

```text
feat: add AI layout generation
fix: resolve preview rendering issue
docs: update contributing guide
refactor: simplify ui component structure
```

Keep commit messages concise and focused on a single change whenever possible.

## Pull Request Guidelines

Before submitting a Pull Request, make sure:

- The project builds successfully
- Relevant tests have been completed
- The changes are consistent with the direction of the project
- Commit messages follow the Conventional Commits specification
- The PR description clearly explains what was changed and why

A Pull Request description should ideally include:

- What was changed
- Why the change is needed
- How the change was tested
- Related issues, if applicable

## Issue Guidelines

When reporting a bug, please include as much relevant information as possible:

- A clear description of the problem
- Steps to reproduce
- Environment information
- Expected behavior
- Actual behavior
- Relevant screenshots or logs

For feature requests, please describe:

- The use case
- The problem you want to solve
- A possible implementation approach, if you have one

## Code Style

Kotlin and Kotlin DSL formatting is enforced by ktlint using the rules in the root `.editorconfig` file.

Format the project:

```bash
./gradlew ktlintFormat
```

Check formatting without changing files:

```bash
./gradlew ktlintCheck
```

Pull Requests are verified by CI. Run the same formatting, lint, and test checks locally before opening a Pull Request when possible:

```bash
./gradlew ktlintCheck check assembleDebug
```

### Optional Git Pre-commit Hook

For earlier local feedback, you can install the ktlint pre-commit hook:

```bash
./gradlew addKtlintCheckGitPreCommitHook
```

The hook checks staged Kotlin and Kotlin DSL files before each commit. It is optional, local to the current clone, and does not replace the required CI checks.

In addition, keep the code:

- Clear and readable
- Consistent with Android development conventions
- Free from unnecessary complexity
- Properly documented when the logic is not obvious

Try to keep changes focused and avoid unrelated refactoring in the same Pull Request.

## Review Process

Pull Requests will be reviewed before being merged.

Reviews may focus on:

- Correctness
- Code quality
- Maintainability
- Consistency with the rest of the project

You may be asked to make changes based on review feedback before the Pull Request is merged.

## Community

Thank you for contributing to Android Vibe Design.

If you have questions or suggestions, feel free to open a GitHub Issue or Pull Request.
