package com.example.asr.websocket;

import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechRecognizer;
import com.alibaba.nls.client.protocol.asr.SpeechRecognizerListener;
import com.alibaba.nls.client.protocol.asr.SpeechRecognizerResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import java.io.IOException;
import java.util.Map;

/**
 * 单次流式识别会话：连接建立时 start，收到 PCM 就 send，收到 end 时 stop，通过 WebSocket 推送中间/最终结果。
 */
public class NlsStreamingSession {

    private static final Logger log = LoggerFactory.getLogger(NlsStreamingSession.class);
    private static final String APP_KEY = System.getenv().get("NLS_APP_KEY");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebSocketSession wsSession;
    private final NlsClient client;
    private volatile SpeechRecognizer recognizer;

    public NlsStreamingSession(WebSocketSession wsSession, NlsClient client) {
        this.wsSession = wsSession;
        this.client = client;
    }

    public void start() throws Exception {
        SpeechRecognizerListener listener = new SpeechRecognizerListener() {
            @Override
            public void onRecognitionResultChanged(SpeechRecognizerResponse response) {
                try {
                    if (response != null && response.getRecognizedText() != null) {
                        sendJson("partial", response.getRecognizedText(), null);
                    }
                } catch (Exception e) {
                    log.warn("onRecognitionResultChanged 处理异常", e);
                }
            }
            @Override
            public void onRecognitionCompleted(SpeechRecognizerResponse response) {
                try {
                    String raw = response != null ? response.getRecognizedText() : null;
                    if (raw == null) raw = "";
                    log.info("录音结束，输出结果：{}", raw);
                    sendJson("final", raw, null);
                } catch (Exception e) {
                    log.warn("onRecognitionCompleted 处理异常", e);
                    try { sendJson("final", "", null); } catch (Exception ignored) {}
                }
            }
            @Override
            public void onStarted(SpeechRecognizerResponse response) {
                try {
                    log.info("NLS 流式识别已启动 taskId={}", response != null ? response.getTaskId() : null);
                } catch (Exception e) {
                    log.warn("onStarted 处理异常", e);
                }
            }
            @Override
            public void onFail(SpeechRecognizerResponse response) {
                try {
                    log.warn("NLS 识别失败 taskId={} status={} {}", response != null ? response.getTaskId() : null, response != null ? response.getStatus() : null, response != null ? response.getStatusText() : null);
                    sendJson(null, null, response != null ? response.getStatusText() : "识别失败");
                } catch (Exception e) {
                    log.warn("onFail 处理异常", e);
                }
            }
        };
        recognizer = new SpeechRecognizer(client, listener);
        recognizer.setAppKey(APP_KEY);
        recognizer.setFormat(InputFormatEnum.PCM);
        recognizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
        recognizer.setEnableIntermediateResult(true);
        recognizer.addCustomedParam("enable_voice_detection", true);
        recognizer.start();
    }

    public void send(byte[] data, int len) {
        if (recognizer == null) return;
        try {
            recognizer.send(data, len);
        } catch (Exception e) {
            log.warn("NLS send 失败", e);
        }
    }

    public void stop() {
        if (recognizer != null) {
            try {
                recognizer.stop();
            } catch (Exception e) {
                log.warn("NLS stop 异常", e);
            }
            try {
                recognizer.close();
            } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    private void sendJson(String type, String text, String error) {
        if (!wsSession.isOpen()) return;
        try {
            Map<String, String> body = new java.util.HashMap<>();
            if (type != null) body.put("type", type);
            if (text != null) body.put("text", text);
            if (error != null) body.put("error", error);
            wsSession.sendMessage(new TextMessage(JSON.writeValueAsString(body)));
        } catch (IOException e) {
            log.warn("推送识别结果失败", e);
        }
    }
}
