package com.example.asr.config;

import com.example.asr.websocket.AsrWebSocketHandler;
import com.example.asr.websocket.NlsClientHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AsrWebSocketHandler asrWebSocketHandler;

    public WebSocketConfig(NlsClientHolder nlsClientHolder) {
        this.asrWebSocketHandler = new AsrWebSocketHandler(nlsClientHolder);
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean bean = new ServletServerContainerFactoryBean();
        bean.setMaxBinaryMessageBufferSize(4 * 1024 * 1024);
        bean.setMaxTextMessageBufferSize(64 * 1024);
        bean.setMaxSessionIdleTimeout(300_000L);
        return bean;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(asrWebSocketHandler, "/ws/asr")
                .setAllowedOriginPatterns("*");
    }
}
