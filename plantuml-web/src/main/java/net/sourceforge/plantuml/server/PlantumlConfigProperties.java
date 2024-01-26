package net.sourceforge.plantuml.server;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "plantuml")
@Data
public class PlantumlConfigProperties {
    Map<String, HttpAuthConfig> httpAuth;
    Map<String, GitAuthConfig> gitAuth;

    @Data
    public static class HttpAuthConfig {
        String username;
        String password;
    }

    @Data
    public static class GitAuthConfig {
        String privateKey;
        String publicKey;
        String passPhrase;
    }
}
