package no.maddin.plantuml;

import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlHeading1;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class LanguageIT {
    /**
     * Tests that the language for the current PlantUML server can be obtained through HTTP
     */
    @Test
    public void language() throws IOException {
        try (WebClient webClient = new WebClient()) {
            TextPage indexPage = webClient.getPage(System.getProperty("app.url") + "/language");
            assertThat(indexPage, hasProperty("content", containsString(";EOF")));
        }
    }
}
