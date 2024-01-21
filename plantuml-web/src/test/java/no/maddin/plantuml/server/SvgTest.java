package no.maddin.plantuml.server;

import net.sourceforge.plantuml.code.TranscoderUtil;
import net.sourceforge.plantuml.server.Application;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = Application.class)
public class SvgTest {

    private static String bobAlice;

    @LocalServerPort
    private int port;

    @BeforeAll
    public static void init() throws Exception {
        bobAlice = TranscoderUtil.getDefaultTranscoder().encode("@startuml\nBob -> Alice : hello\n@enduml");
    }
    /**
     * Verifies the generation of the SVG for the Bob -> Alice sample
     */
    @Test
    void getSequenceDiagram() throws Exception {
        try (WebClient webClient = new WebClient()) {
            String appUrl = "http://localhost:" + port;
            HtmlPage svgImage = webClient.getPage(appUrl + "/svg/" + bobAlice);
            validateBobAliceSvg(svgImage, "");
        }
    }

    @Test
    void getSvgDiagram(@Autowired TestRestTemplate rest) {
        ResponseEntity<String> svgEntity = rest.getForEntity("http://localhost:" + port + "/svg/" + bobAlice, String.class);
        assertThat(svgEntity, hasProperty("statusCode", equalTo(HttpStatus.OK)));
        assertThat(svgEntity, hasProperty("body", startsWith("<?xml")));
    }

    static void validateBobAliceSvg(HtmlPage svgImage, String bobMarker) {
        assertEquals(svgImage.getWebResponse().getContentType(), "image/svg+xml");
        List<DomElement> bobElems = svgImage.getByXPath("//*[text()='Bob" + bobMarker + "']");
        assertThat(bobElems, allOf(is(iterableWithSize(2)), everyItem(hasProperty("textContent", containsString("Bob" + bobMarker)))));
        List<DomElement> aliceElems = svgImage.getByXPath("//*[text()='Alice']");
        assertThat(aliceElems, is(iterableWithSize(2)));
        List<DomElement> helloElems = svgImage.getByXPath("//*[text()='hello']");
        assertThat(helloElems, is(iterableWithSize(1)));
    }

    private WebRequest createPostRequest(String contentType) throws MalformedURLException {
        String appUrl = "http://localhost:" + port;
        WebRequest postRequest = new WebRequest(URI.create(appUrl + "/svg").toURL(), HttpMethod.POST);
        postRequest.setAdditionalHeader("Accept", "*/*");
        postRequest.setAdditionalHeader("Referer", appUrl);
        postRequest.setAdditionalHeader("Accept-Language", "en-US,en;q=0.8");
        postRequest.setAdditionalHeader("Accept-Encoding", "gzip,deflate,sdch");
        postRequest.setAdditionalHeader("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.3");
//            postRequest.setAdditionalHeader("X-Requested-With", "XMLHttpRequest");
        postRequest.setAdditionalHeader("Cache-Control", "no-cache");
        postRequest.setAdditionalHeader("Pragma", "no-cache");
        postRequest.setAdditionalHeader("Origin", appUrl);
        postRequest.setAdditionalHeader("Content-Type", contentType);

        return postRequest;
    }

    /**
     * Verifies the generation of the SVG for the Bob -> Alice sample, Form encoded, fails
     */
    @Test
    public void postedSequenceDiagramPlain() throws Exception {
        try (WebClient webClient = new WebClient()) {
            WebRequest postRequest = createPostRequest("text/plain");
            postRequest.setRequestBody("@startuml\nBob -> Alice : hello\n@enduml");

            HtmlPage svgImage = webClient.getPage(postRequest);
            validateBobAliceSvg(svgImage, "");
        }
    }

    /**
     * Verifies the generation of the SVG for the Bob -> Alice sample
     */
    @Test
    public void postedInvalidSequenceDiagram() throws Exception {
        try (WebClient webClient = new WebClient()) {
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
            WebRequest postRequest = createPostRequest("text/plain");
            postRequest.setRequestBody("@startuml\nBob |<-> Alice : hello\n@endxx");

            Page svgImage = webClient.getPage(postRequest);
            assertThat(svgImage.getWebResponse().getStatusCode(), is(equalTo(400)));
        }
    }

}
