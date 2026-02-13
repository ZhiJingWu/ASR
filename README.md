# ASR 实时录音 · 语音转文字

前端实时录音，通过 WebSocket 将 PCM 流推送到后端，后端使用阿里云 NLS 进行流式语音识别，边说边出中间结果，停止后返回最终文本。
## 实现效果预览
![asr](https://github.com/user-attachments/assets/fb052910-8490-4e38-be5c-7ffbec9bfb8c)

## 技术栈

| 端   | 技术 |
|------|------|
| 后端 | JDK 11、Spring Boot 2.7.12、Spring WebSocket、阿里云 NLS SDK |
| 前端 | Vue 3、Vite 5 |

## 架构概览

- **通信**：仅 WebSocket，无 REST 接口。前端直连 `ws://localhost:8080/ws/asr`。
- **流程**：连接建立即启动识别 → 前端持续发送 16k PCM 二进制帧 → 后端实时推送 `type: partial` 中间结果 → 前端发送文本 `end` 结束 → 后端推送 `type: final` 最终结果。

## 环境要求

- JDK 11+
- Node.js（用于前端）
- 阿里云账号，并开通 [智能语音交互（一句话/实时语音识别）](https://www.aliyun.com/product/nls)，用于获取 AccessKey 与 AppKey

## 后端配置

在运行后端前设置环境变量（必填）：

| 变量名 | 说明 |
|--------|------|
| `ALIYUN_AK_ID` | 阿里云 AccessKey ID |
| `ALIYUN_AK_SECRET` | 阿里云 AccessKey Secret |
| `NLS_APP_KEY` | 语音识别应用 AppKey（在控制台创建项目后获得） |
| `NLS_GATEWAY_URL` | NLS WebSocket 网关地址,默认值:wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1 |

获取参考地址: https://help.aliyun.com/zh/isi/getting-started/start-here?spm=5176.12061031.J_5253785160.4.52f96822nKnpWs

## 快速运行

### 1. 启动后端

```bash
cd backend
# 设置环境变量后执行（示例为 Windows PowerShell）
# $env:ALIYUN_AK_ID="你的AK_ID"; $env:ALIYUN_AK_SECRET="你的AK_SECRET"; $env:NLS_APP_KEY="你的AppKey"
mvn spring-boot:run
```

后端监听 `http://localhost:8080`，WebSocket 路径：`/ws/asr`。

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认 `http://localhost:5173`。浏览器打开该地址即可使用。

### 3. 使用方式

1. 打开 `http://localhost:5173`
2. 点击「开始录音」，授权麦克风
3. 说话过程中可看到「识别中」的实时中间结果
4. 点击「停止录音」，等待「识别结果」中的最终文本

## WebSocket 协议说明（前后端约定）

- **前端 → 后端**
    - 二进制：16 kHz、16 bit、单声道 PCM 数据块
    - 文本：发送内容为 `end` 表示结束本次识别（停止送流并收最终结果）

- **后端 → 前端**（JSON 文本帧）
    - 中间结果：`{ "type": "partial", "text": "已识别内容" }`
    - 最终结果：`{ "type": "final", "text": "完整文本" }`
    - 错误：`{ "error": "错误信息" }`（或带 `type` 的失败响应）

## 项目结构

```
ASR/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/example/asr/
│   │   ├── AsrApplication.java
│   │   ├── config/             # WebSocket、CORS 配置
│   │   └── websocket/          # NLS 流式识别、WebSocket 处理
│   └── pom.xml
├── frontend/                   # Vue 3 + Vite 前端
│   ├── src/
│   │   ├── App.vue             # 录音页与 WebSocket 逻辑
│   │   └── main.js
│   ├── index.html
│   └── package.json
└── README.md
```

## 构建与部署

- **后端**：`mvn clean package`，运行 `java -jar target/asr-backend-1.0.0.jar`（需同样配置上述环境变量）。
- **前端**：`npm run build`，将 `dist` 部署到任意静态服务器；生产环境需将前端中的 `WS_URL` 改为实际后端 WebSocket 地址（如 `wss://your-domain/ws/asr`），并确保后端 CORS/域名允许该前端来源。

## 说明

- 当前识别引擎为 **阿里云 NLS 流式识别**，音频格式为 16k Hz、16 bit、PCM。前端从麦克风采集后会在浏览器内做重采样与 PCM 转换再发送。
- 后端已配置 CORS，允许 `http://localhost:*` 与 `http://127.0.0.1:*` 访问；生产环境建议在 `WebConfig` 中收紧 `AllowedOriginPatterns`。
