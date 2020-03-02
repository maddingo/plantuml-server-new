package no.maddin.plantuml;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlHeading1;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertThat;

public class IndexPageIT {

    @Test
    public void indexPage() throws Exception {
        try (WebClient webClient = new WebClient()) {
            HtmlPage indexPage = webClient.getPage(System.getProperty("app.url"));
            final HtmlHeading1 h1 = (HtmlHeading1) indexPage.getByXPath("//div[@id='header']/h1").get(0);
            assertThat(h1, hasProperty("textContent", equalTo("PlantUML Server")));
        }
    }
}
