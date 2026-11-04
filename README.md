# 答题锁 TV · QuizLock TV

> 一个给小米/安卓智能电视的「答题解锁看电视」App。孩子开机后必须先答题、看电视每隔几分钟再弹一次全屏题，**答对才能继续看、答错就追加新题直到答对**。题目由 AI（如 WorkBuddy / DeepSeek / 智谱 GLM 等）生成，App 本身不内置任何 AI、不需要 API Key、不花一分钱。
>
> 同一 WiFi 下，手机浏览器打开电视内置的控制台网页，即可**随时更新题库、调整弹窗规则、手动弹题**——控制权在家长手机里，孩子拿遥控器完全绕不过。

**作者：[Gavin-Space](https://github.com/Gavin-Space)**

---

## ✨ 功能特性

- **开机先答题**：电视开机自动弹一道全屏锁屏题，答对前看不了电视。
- **每 N 分钟弹一题**：观看过程中每隔 10 分钟（间隔可调 1–1440 分钟）自动弹全屏题。
- **全屏锁屏、防绕过**：覆盖电视画面、屏蔽返回键/Home 键、`FLAG_SECURE` 防截屏，无法用遥控器跳过。
- **单选 + 判断**：每次只弹一道题，题型混用。
- **答对解锁，答错追加**：答对则放行并重新计时；答错追加一道新题，直到答对为止。
- **防"闭眼乱点蒙对"**：
  - 默认需**连续答对 3 题**才解锁，答错计数清零重来（乱点蒙过率从单题 25% 降到约 1.6%）；连对数可调 1–10。
  - 单选题**选项每次随机打乱**，杜绝"肌肉记忆点第一个/固定选 A"。
- **手机远程控制（同 WiFi）**：电视内置局域网 HTTP 服务器，手机/平板/电脑浏览器打开即用，安卓和 iPhone 通吃，无需安装任何手机端 App。
- **纯离线、零成本**：不绑定任何大模型或付费接口，题目格式开放，谁来出题都行。

## 🎯 它解决的真实问题

新闻里那种"爸爸魔改电视、儿子看 10 分钟答一题"的效果，普通家庭也能复刻：把"看电视"变成"闯关答题"，且锁屏、计时、放题的开关都在家长手机里，孩子断电重启、按返回键、乱点选项都突破不了。

## 📦 安装

1. 在 Releases / Actions 产物里下载 `app-debug.apk`。
2. 电视：`设置 → 关于 → 连续点"型号/版本号" 7 次` 进入开发者模式，打开 **USB/网络调试**。
3. 电脑装 adb，`adb connect 电视IP:5555`（首次需在电视上点"允许调试"），再 `adb install app-debug.apk`。
4. 首次使用进电视 `设置 → 应用 → 答题锁TV → 权限`，授予 **悬浮窗 + 自启动 + 忽略电池优化**，否则待机时弹窗可能被系统清理。

## 🚀 快速开始

1. 打开 App，主页会显示 **📱 手机控制台地址**，形如 `http://192.168.1.50:8770`。
2. 手机连**和电视同一个 WiFi**，浏览器打开该地址 → 进入控制台。
3. 用 AI 生成题库（见下方提示词模板），把 JSON 粘进控制台"更新题库"框 → 推送。
4. 之后：开机自动弹题、每隔设定分钟弹题、随时手动弹窗，全部生效。

> ⚠️ 手机打不开控制台，多半是路由器开了「AP 隔离 / 访客网络」把设备隔开了，换主 WiFi 即可。

## 📝 题库格式

```json
{
  "questions": [
    {"type": "single", "stem": "下列函数中，在 (0,+∞) 上为减函数的是？",
     "options": ["y = x²", "y = 2x", "y = 1/x", "y = √x"], "answer": 2, "analysis": "y=1/x 在 (0,+∞) 递减"},
    {"type": "judge", "stem": "0 是自然数。", "answer": true, "analysis": "现行教材规定 0∈N"}
  ]
}
```

| 字段 | 说明 |
|---|---|
| `type` | `"single"` 单选 / `"judge"` 判断 |
| `stem` | 题干 |
| `options` | 单选的选项数组（判断题省略或留空） |
| `answer` | 单选填**正确项下标**（从 0 数）；判断填 `true` / `false` |
| `analysis` | 解析（答错时显示给孩子看，可选） |

## 🤖 喂 AI 出题的提示词模板

直接复制，把【知识点】换成高一内容即可：

```
你是高一老师。请按知识点"【填：如 函数单调性】"出 12 道题，8 道单选、4 道判断，难度贴合高一课本进度。
只输出一个 JSON、不要任何多余文字，严格用这个结构：
{"questions":[
 {"type":"single","stem":"题干","options":["A项","B项","C项","D项"],"answer":正确项下标(0~3),"analysis":"解析"},
 {"type":"judge","stem":"题干","answer":true或false,"analysis":"解析"}
]}
单选 answer 是正确选项下标(从0数)；判断题 answer 是 true/false。
```

任何 AI 都行（WorkBuddy、DeepSeek、智谱 GLM、通义千问、Kimi、豆包……），只要让它按上面结构输出即可。`sample/题库示例-灵犀.json` 是一份可直接粘贴试用的高一数学示例。

## 🧩 架构

```
电视 App（Android）
├── MainActivity        管理主页 / 显示手机控制台地址
├── QuizLockActivity    全屏锁屏出题（连对解锁、选项乱序、屏蔽返回键）
├── LockService         前台常驻服务：每 N 分钟闹钟 + 拉起控制服务器
├── BootReceiver        开机广播 → 弹首题 + 启动服务
├── AlarmReceiver       定时闹钟 → 弹窗
├── ControlServer       内置局域网 HTTP 服务器（端口 8770）
│     GET  /            → 手机控制台网页（assets/console.html）
│     GET  /api/status   → 题库数 / 间隔 / 连对 / IP
│     POST /api/bank     → 接收题库 JSON，校验后整库替换
│     POST /api/config   → 改弹窗间隔 / 连对数
│     POST /api/popup    → 立即弹全屏锁屏
├── QuizStore           SharedPreferences 持久化题库 / 配置 / 进度
├── QuizParser          题库 JSON 解析校验
└── QuizSync            备用：从 URL 拉取题库
```

- `minSdk 21`（Android 5.0）兼容绝大多数国行小米电视（MIUI TV / 澎湃 OS 多为 Android 9~11）；`targetSdk 30` 放宽后台弹全屏锁屏限制。
- 控制服务器用纯 JDK `ServerSocket` 实现，不引入第三方 HTTP 库，兼容性最稳。

## 🔨 自行编译

本仓库带 GitHub Actions（`.github/workflows/build.yml`），推送到 main 即自动云编译出 debug APK；下载 Actions 的 artifact `QuizLockTV-debug` 即可，无需本机装 JDK/Android SDK。

本地编译则需要 JDK 17 + Android SDK，执行：

```bash
gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 📌 说明

- 当前为 **debug 版** APK，可直接安装使用；长期正式使用建议配 release 签名。
- 题目内容与使用者自行生成的题库相关，本工具不涉及任何第三方会员/盗版内容。

## 📄 License

[MIT](LICENSE)

---

Author: **[Gavin-Space](https://github.com/Gavin-Space)**
