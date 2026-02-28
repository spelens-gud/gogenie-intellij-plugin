# gogenie-intellij-plugin

`gogenie-intellij-plugin` 是一个面向 GoLand/IntelliJ IDEA Go 插件生态的 `gogenie` 语义增强插件。

它提供注解补全、高亮、弱校验、Gutter 快捷生成、注解跳转以及 `.gogenie/config.yaml` 可视化配置能力，帮助你在 IDE 内完成 gogenie 常用开发流程。

## 功能特性

- `gogenie` 注解自动补全
  - 在 Go 注释中输入 `@` 自动提示注解名
  - 支持动态注解名（按项目 `.gogenie` 配置解析）
  - 参数键补全（如 `@http(method=...,route=...)`）

- 注解语法高亮与弱校验
  - 已识别注解/参数着色
  - 未识别注解给出弱提示（不阻塞编码）

- Gutter 快捷命令
  - `impl` / `enum` / `http` / `mount` 等注解提供左侧快速执行入口
  - 支持按注解触发对应 `gogenie` 子命令

- 跳转能力
  - 注解到生成结果的跳转（含 `enum`、`http route`、`mount` 链路）
  - `Ctrl/Cmd + Click` 跳转到关联符号

- 配置可视化
  - 右侧 ToolWindow 提供 `global` + `commands.*` 的可视化编辑
  - 支持加载/保存/重置模板
  - 配置文件路径：`.gogenie/config.yaml`

- 编辑器右键菜单
  - `gogenie 生成数据库模型`（执行 `db2struct`）

## 支持的核心注解场景

- `@autowire`, `@autowire.init`, `@autowire.config`
- `@enum(...)`
- `@service/@dao/@grpc` 及配置驱动的 impl 动态注解
- `@http(...)` / `@http.get(...)` 等
- `@mount(...)` 与 mount 注册别名注解
- `@rule` / `@rule-hash`
- Swagger 注解集合（`@Summary/@Param/@Router` 等）

## 环境要求

- GoLand / IntelliJ IDEA（含 Go 插件）
- IDE 平台版本：`2025.2+`（`since-build: 252`）
- 项目内可用 `gogenie` 命令（建议在系统 `PATH` 可执行）

## 安装方式

- IDE 本地安装
  1. `Settings/Preferences -> Plugins -> ⚙ -> Install Plugin from Disk...`
  2. 选择构建产物 `build/distributions/*.zip`

- Marketplace 安装
  - 发布后可在 JetBrains Marketplace 搜索 `gogenie-intellij-plugin`

## 使用说明（快速）

1. 打开 Go 项目并确保项目根存在 `.gogenie/config.yaml`（可选）
2. 在 Go 注释输入 `@` 触发注解补全
3. 使用注解左侧 Gutter 图标快速执行生成命令
4. 使用右侧 `gogenie` ToolWindow 可视化调整配置并保存

## 开发与构建

```bash
./gradlew test
./gradlew buildPlugin
```

产物示例：

```text
build/distributions/gogenie-intellij-plugin-<version>.zip
```

## 发布到 JetBrains Marketplace

先设置发布令牌：

```bash
export PUBLISH_TOKEN=<your_marketplace_token>
```

然后执行：

```bash
./gradlew publishPlugin
```

## 插件描述（Marketplace 提取区）

<!-- Plugin description -->
Gogenie adds first-class gogenie workflow support for Go projects in GoLand and IntelliJ IDEA.
It provides annotation completion, highlighting, weak validation, gutter quick generation actions,
cross-file navigation, and visual editing for `.gogenie/config.yaml`.
<!-- Plugin description end -->

## 许可证

遵循仓库默认许可证策略。
