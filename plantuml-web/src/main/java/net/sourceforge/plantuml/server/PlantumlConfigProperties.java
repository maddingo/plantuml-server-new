package net.sourceforge.plantuml.server;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.Map;

@ConfigurationProperties(prefix = "plantuml")
@RefreshScope
@Data
public class PlantumlConfigProperties {
    Map<String, HttpAuthConfig> http;

    @Data
    public static class HttpAuthConfig {
        String url;
        String username;
        String password;
    }
}
