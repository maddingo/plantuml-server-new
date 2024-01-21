package no.maddin.plantuml.server;

import net.sourceforge.plantuml.server.Application;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.net.URI;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = Application.class)
@ActiveProfiles("test")
public class ProxyTest {

    @LocalServerPort
    private int port;

    @Test
    void httpSrcRequest() throws Exception {

        try (WebClient webClient = new WebClient()) {
            String appUrl = "http://localhost:" + port;

            WebRequest getRequest = new WebRequest(URI.create(appUrl + "/proxy").toURL(), HttpMethod.GET);
            getRequest.setAdditionalHeader("Accept", "*/*");
            getRequest.setAdditionalHeader("Referer", appUrl);
            getRequest.setRequestParameters(List.of(
                new NameValuePair("src", "http://localhost:" + port + "/default-diagram?count=3"),
                new NameValuePair("idx", "1")
            ));

            HtmlPage page = webClient.getPage(getRequest);

            SvgTest.validateBobAliceSvg(page, "1");
        }
    }
}
