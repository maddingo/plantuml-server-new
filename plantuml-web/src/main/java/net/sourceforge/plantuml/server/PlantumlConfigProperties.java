package net.sourceforge.plantuml.server;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "plantuml")
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
