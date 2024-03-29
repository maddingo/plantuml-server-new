package no.maddin.plantuml.server;

import net.sourceforge.plantuml.server.Application;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlHeading1;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTextArea;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ContextConfiguration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = Application.class)
@Tag("smoke")
public class IndexPageTest {

    @LocalServerPort
    private int port;

    @Test
    public void indexPage() throws Exception {
        try (WebClient webClient = new WebClient()) {
            String appUrl = "http://localhost:" + port;
            HtmlPage indexPage = webClient.getPage(appUrl);
            final HtmlHeading1 h1 = indexPage.getFirstByXPath("//div[@id='header']/div/h1");
            assertThat(h1, hasProperty("textContent", equalTo("PlantUML Server")));

            HtmlTextArea textArea = indexPage.getFirstByXPath("//textarea[@id='text']");
//            assertThat(textArea, hasProperty("displayed", equalTo(false)));
            assertThat(textArea, hasProperty("text", containsString("@startuml")));

            HtmlInput input = indexPage.getFirstByXPath("//input[@name='url']");
            assertThat(input, hasProperty("valueAttribute", startsWith(appUrl)));
        }
    }
}
