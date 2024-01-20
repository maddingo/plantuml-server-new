package no.maddin.plantuml.server;

import net.sourceforge.plantuml.server.Application;
import org.htmlunit.TextPage;
import org.htmlunit.WebClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ContextConfiguration;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = Application.class)
public class LanguageTest {

    @LocalServerPort
    private int port;

    /**
     * Tests that the language for the current PlantUML server can be obtained through HTTP
     */
    @Test
    public void language() throws IOException {
        String appUrl = "http://localhost:" + port;

        try (WebClient webClient = new WebClient()) {
            TextPage indexPage = webClient.getPage(appUrl + "/language");
            assertThat(indexPage, hasProperty("content", containsString(";EOF")));
        }
    }
}
