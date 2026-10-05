# SS2A Monitor - Sub2API 安卓桌面小组件 (Android HomeScreen Widget)

专门为 **Sub2API**（https://ss2a.top）站长打造的原生 Android 桌面小组件应用。

无需频繁打开电脑或登录网页后台，放在手机桌面上即可随时一眼掌握**今日总消耗金额**、**今日 API 请求次数**、**Token 吞吐量**与服务运行状态。

---

## 📱 核心特性（全面优化升级版）

- **🔐 管理员一键免抓包登录 (新特性)**：
  - 支持直接在 App 内输入管理员账号和密码，一键自动调用 `/api/v1/auth/login` 完成登录并自动绑定 Token！
  - **Token 智能无感续期**：当后台 JWT Token 过期时（401/403），客户端会在后台静默自动重登并更新 Token，彻底告别频繁重新配置。
  - 依然完整保留手动输入 Admin Token / API Key 的高级选项。
- **🎨 科技暗黑极客 UI 设计**：
  - 核心主指标（今日总消耗金额）采用高饱和度**霓虹荧光绿**大字；
  - 次指标（今日调用次数、Token 吞吐量）采用**科技蓝**与**珊瑚橙**分区网格；
  - 顶部配备**实时状态指示灯**（🟢 正常运营 / 🔴 接口异常）与秒级同步反馈（点击即时显示“正在同步...”）。
- **⚡ 双向快捷交互**：
  - 点击小组件右上角 `⟳`：立即强制拉取中转站最新数据；
  - 点击小组件右上角 `⚙️`：一键调起 App 配置中心；
  - 点击小组件卡片主体：直接在手机浏览器打开中转管理后台（`https://ss2a.top/admin`）。
- **🔋 低功耗后台调度**：基于 Android 官方 `WorkManager` 定时调度（15m / 30m / 1h 可选），不占前台常驻服务，极度省电。
- **🛡️ 弱网与异常容错**：离线状态下保留上一次已知有效数据，展示错误摘要，不会闪退或留白。

---

## 🔑 如何获取 Sub2API 管理员 Token

1. 使用管理员账号登录你的中转后台：`https://ss2a.top/admin`；
2. 按 `F12` 打开浏览器开发者工具（或在手机 Kiwi / 带有审查工具的浏览器中）；
3. 切换到 **Network (网络)** 标签页，点击页面任意数据（例如刷新仪表盘）；
4. 查看任意 `/api/v1/admin/...` 请求的 **Request Headers (请求头)**，找到 `Authorization` 字段：
   - 复制 `Bearer ` 后面的那串长字符串（JWT Token）；
   - 或者如果你在后台配置了专用的 Admin API Key，也可直接填入。

---

## 🛠️ 项目文件结构与源码映射

本项目采用标准 Android 原生结构开发：

| 工作区文件 | 对应 Android Studio 目录位置 | 说明 |
| :--- | :--- | :--- |
| `MainActivity.kt` | `app/src/main/java/top/ss2a/widget/ui/` | 配置面板：输入域名、Token、测试连接与保存 |
| `Sub2ApiClient.kt` | `app/src/main/java/top/ss2a/widget/data/` | Sub2API 管理员接口拉取与数据解析客户端 |
| `WidgetDataStore.kt` | `app/src/main/java/top/ss2a/widget/data/` | 本地配置存储与离线数据缓存 |
| `Sub2WidgetProvider.kt` | `app/src/main/java/top/ss2a/widget/widget/` | 桌面小组件广播处理器与 RemoteViews 渲染 |
| `WidgetUpdateWorker.kt` | `app/src/main/java/top/ss2a/widget/widget/` | WorkManager 后台定时调度任务 |
| `widget_sub2_4x2.xml` | `app/src/main/res/layout/` | 4×2 横幅小组件暗黑毛玻璃 UI 布局 |
| `widget_sub2_2x2.xml` | `app/src/main/res/layout/` | 2×2 紧凑小组件 UI 布局 |
| `activity_main.xml` | `app/src/main/res/layout/` | 设置界面布局 |
| `widget_sub2_4x2_info.xml` | `app/src/main/res/xml/` | 小组件系统元数据描述 |
| `AndroidManifest.xml` | `app/src/main/` | 清单文件 (权限与组件注册) |
| `build.gradle.kts` | `app/` | 模块依赖配置 |

---

## 🚀 编译与打包 APK

### 方式 1：Android Studio（推荐）
1. 打开 Android Studio，选择 **Open** 打开本工程根目录；
2. 等待 Gradle 同步完成；
3. 点击菜单栏 **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**；
4. 构建生成的 APK 文件直接安装到手机即可。

### 方式 2：Gradle 命令行快速构建
```bash
./gradlew assembleDebug
```
生成的 APK 路径为：`app/build/outputs/apk/debug/app-debug.apk`。

---

## 📲 在手机上添加到桌面

1. 在手机上安装并打开应用，输入站点地址 `https://ss2a.top` 和你的管理员 Token；
2. 点击 **“测试连接”**，确认显示 ✅ 连接成功 并拉取到今日金额与请求数；
3. 点击 **“保存并同步”**；
4. 返回手机主屏幕，长按桌面空白处 -> 选择 **微件 / 小组件 (Widgets)**；
5. 找到 **【SS2A 监控】**，将小组件拖拽到屏幕上合适的位置即可！
