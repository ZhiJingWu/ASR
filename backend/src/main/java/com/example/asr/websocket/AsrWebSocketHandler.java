package com.example.asr.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
/**
 * 流式 ASR：连接建立即启动 NLS 识别，收到 PCM 就送识别引擎，收到 "end" 结束并回传最终结果；
 * 中间结果通过 type=partial 实时推送。
 */
public class AsrWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AsrWebSocketHandler.class);
    private static final String END_SIGNAL = "end";
    private static final String SESSION_KEY = "nls.streaming.session";
    private final NlsClientHolder nlsClientHolder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AsrWebSocketHandler(NlsClientHolder nlsClientHolder) {
        this.nlsClientHolder = nlsClientHolder;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket 已连接: sessionId={}, 启动流式识别", session.getId());
        try {
            NlsStreamingSession nlsSession = new NlsStreamingSession(session, nlsClientHolder.getClient());
            nlsSession.start();
            session.getAttributes().put(SESSION_KEY, nlsSession);
        } catch (Exception e) {
            log.error("启动 NLS 流式识别失败", e);
            sendResult(session, null, "启动识别失败: " + e.getMessage());
        }
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        NlsStreamingSession nlsSession = (NlsStreamingSession) session.getAttributes().remove(SESSION_KEY);
        if (nlsSession != null) {
            nlsSession.stop();
        }
        if (status.getCode() == 1000) {
            log.info("WebSocket 已关闭(正常): sessionId={}, code=1000, reason={} (多为前端主动关闭：停止录音、离开页面或 5s 超时)", session.getId(), status.getReason());
        } else {
            log.info("WebSocket 已关闭: sessionId={}, code={}, reason={}", session.getId(), status.getCode(), status.getReason());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket 传输异常: sessionId={}", session.getId(), exception);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        NlsStreamingSession nlsSession = (NlsStreamingSession) session.getAttributes().get(SESSION_KEY);
        if (nlsSession == null) return;
        ByteBuffer payload = message.getPayload();
        byte[] arr = new byte[payload.remaining()];
        payload.get(arr);
        nlsSession.send(arr, arr.length);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (!END_SIGNAL.equals(message.getPayload())) {
            return;
        }
        NlsStreamingSession nlsSession = (NlsStreamingSession) session.getAttributes().get(SESSION_KEY);
        if (nlsSession != null) {
            nlsSession.stop();
        }
    }

    private void sendResult(WebSocketSession session, String text, String error) throws IOException {
        Map<String, String> body = error != null
                ? Map.of("error", error)
                : Map.of("text", text != null ? text : "");
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(body)));
    }
}
