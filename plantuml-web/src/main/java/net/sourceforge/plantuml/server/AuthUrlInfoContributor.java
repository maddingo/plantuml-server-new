package net.sourceforge.plantuml.server;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class AuthUrlInfoContributor implements InfoContributor {
    private final PlantumlConfigProperties plantumlConfigProperties;

    public AuthUrlInfoContributor(PlantumlConfigProperties plantumlConfigProperties) {
        this.plantumlConfigProperties = plantumlConfigProperties;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, PlantumlConfigProperties.HttpAuthConfig> http = plantumlConfigProperties.getHttp();
        if (http != null) {
            Map<String, Object> httpMap = new LinkedHashMap<>();
            http.forEach((key, value) -> httpMap.put(key, Map.of(
                    "url", value.getUrl(),
                    "username", value.getUsername(),
                    "password", Optional.ofNullable(value.getPassword()).map(p -> "****").orElse("empty")
            )));
            builder.withDetail("plantuml", Map.of("http", httpMap));
        }
    }
}
