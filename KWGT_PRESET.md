# ⚡ 免编译 30 秒手机快速上屏指南 (基于 KWGT)

如果你当前手头暂时没有电脑安装 Android Studio 编译 APK，但想**立即在安卓手机桌面上看到 `ss2a.top` 今日用量**，可以使用安卓最著名的小组件神器 **KWGT** 实现！

---

## 步骤 1：安装 KWGT
在手机应用商店或酷安下载安装 **KWGT Kustom Widget Maker**。

---

## 步骤 2：在桌面新建组件并粘贴数据公式
1. 在手机桌面空白处长按，添加一个 **4×2** 或 **2×2** 的 KWGT 小组件；
2. 点击刚添加的空组件进入 KWGT 编辑器；
3. 新建一个 **文本 (Text)** 图层，进入文本内容编辑，粘贴以下公式：

### 1. 今日消耗金额 ($)：
```kustom
$wg("https://ss2a.top/api/v1/admin/dashboard", url, "Authorization", "Bearer 你的ADMIN_TOKEN", json, .data.today_cost)$
```
*(如果数据在顶层，可将 `.data.today_cost` 改为 `.today_cost`)*

### 2. 今日调用次数：
```kustom
$wg("https://ss2a.top/api/v1/admin/dashboard", url, "Authorization", "Bearer 你的ADMIN_TOKEN", json, .data.today_requests)$ 次
```

### 3. 最后更新时间：
```kustom
更新于 $df("HH:mm")$
```

---

## 步骤 3：保存退出
点击右上角的 💾 保存图标，返回桌面即可立刻看到实时数据刷新！

> 💡 **提示**：当你需要打包出专属独立 App 时，随时使用工程根目录的代码在 Android Studio 中编译 APK 即可！