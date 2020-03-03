package no.maddin.plantuml;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class IndexPageIT {

    @Test
    public void indexPage() throws Exception {
        try (WebClient webClient = new WebClient()) {
            String appUrl = System.getProperty("app.url");
            HtmlPage indexPage = webClient.getPage(appUrl);
            final HtmlHeading1 h1 = indexPage.getFirstByXPath("//div[@id='header']/h1");
            assertThat(h1, hasProperty("textContent", equalTo("PlantUML Server")));

            HtmlTextArea textArea = indexPage.getFirstByXPath("//textarea[@id='text']");
            assertThat(textArea, hasProperty("displayed", equalTo(false)));
            assertThat(textArea, hasProperty("text", containsString("@startuml")));

            HtmlInput input = indexPage.getFirstByXPath("//input[@name='url']");
            assertThat(input, hasProperty("valueAttribute", startsWith(appUrl)));
        }
    }
}
