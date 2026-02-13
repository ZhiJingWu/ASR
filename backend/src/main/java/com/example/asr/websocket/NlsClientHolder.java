package com.example.asr.websocket;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.NlsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;

/**
 * 全局 NlsClient，供流式识别复用。
 */
@Component
public class NlsClientHolder {

    private static final Logger log = LoggerFactory.getLogger(NlsClientHolder.class);
    private static final String ID = System.getenv().get("ALIYUN_AK_ID");
    private static final String SECRET = System.getenv().get("ALIYUN_AK_SECRET");
    private static final String URL = System.getenv().getOrDefault("NLS_GATEWAY_URL", "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1");

    private volatile NlsClient client;

    public synchronized NlsClient getClient() throws IOException {
        if (client == null) {
            AccessToken token = new AccessToken(ID, SECRET);
            token.apply();
            log.info("NLS token 获取成功, expire: {}", token.getExpireTime());
            client = new NlsClient(URL, token.getToken());
        }
        return client;
    }

    @PreDestroy
    public void shutdown() {
        if (client != null) {
            client.shutdown();
            client = null;
        }
    }
}
