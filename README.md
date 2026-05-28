# 京东Cookie

京东 Cookie 管理工具，通过 WebView 加载京东登录页，自动捕获 Cookie 并同步至青龙面板。

## 功能

- **Cookie 捕获** — 内置 WebView 加载京东移动端登录页，登录后自动提取 `pt_key` 和 `pt_pin`
- **一键复制** — 将 Cookie 复制到剪贴板
- **青龙同步** — 提交 Cookie 至青龙面板环境变量，支持新增和更新
- **自动启用** — 提交成功后自动启用该环境变量

## 环境要求

- Android 8.0 (API 27) 及以上
- 青龙面板（需配置 `client_id`、`client_secret`、IP 地址）

## 使用说明

1. 点击工具栏 **设置**，填写青龙面板的 Client ID、Client Secret 和 IP:端口
2. 在登录页完成京东账号登录
3. 登录成功后自动弹出 Cookie，选择 **复制** 或 **提交** 至青龙

## 构建

```bash
./gradlew assembleRelease
```

APK 输出路径: `app/build/outputs/apk/release/`

## 技术栈

- **UI**: Material Design 3 + Android Views
- **网络**: OkHttp 5
- **JSON**: FastJSON2
- **WebView**: 系统 WebView (Chromium)

## 开源协议

本项目仅供学习交流使用，请勿用于商业用途。
