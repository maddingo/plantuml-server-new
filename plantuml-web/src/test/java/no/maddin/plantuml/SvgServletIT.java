package no.maddin.plantuml;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import net.sourceforge.plantuml.code.TranscoderUtil;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class SvgServletIT {

    private static String versionDiagram;
    private static String bobAlice;

    @BeforeClass
    public static void init() throws Exception {
        versionDiagram = TranscoderUtil.getDefaultTranscoder().encode("@startuml\nversion\n@enduml");
        bobAlice = TranscoderUtil.getDefaultTranscoder().encode("@startuml\nBob -> Alice : hello\n@enduml");
    }
    /**
     * Verifies the generation of the SVG for the Bob -> Alice sample
     */
    @Test
    public void getSequenceDiagram() throws Exception {
        try (WebClient webClient = new WebClient()) {
            String appUrl = System.getProperty("app.url", "http://localhost:8080");
            HtmlPage svgImage = webClient.getPage(appUrl + "/svg/" + bobAlice);
            validateBobAliceSvg(svgImage);
        }
    }

    private void validateBobAliceSvg(HtmlPage svgImage) {
        assertEquals(svgImage.getWebResponse().getContentType(), "image/svg+xml");
        List<DomElement> bobElems = svgImage.getByXPath("//*[text()='Bob']");
        assertThat(bobElems, is(iterableWithSize(2)));
        List<DomElement> aliceElems = svgImage.getByXPath("//*[text()='Alice']");
        assertThat(aliceElems, is(iterableWithSize(2)));
        List<DomElement> helloElems = svgImage.getByXPath("//*[text()='hello']");
        assertThat(helloElems, is(iterableWithSize(1)));
    }

    /**
     * Verifies the generation of the SVG for the Bob -> Alice sample, Form encoded, fails
     */
    @Test
    @Ignore("Form-encoded content is not yet supported")
    public void postedSequenceDiagramFormEncoded() throws Exception {
        try (WebClient webClient = new WebClient()) {
            WebRequest postRequest = createPostRequest("application/x-www-form-urlencoded; charset=UTF-8");
            postRequest.setRequestBody(URLEncoder.encode("@startuml\nBob -> Alice : hello\n@enduml", StandardCharsets.UTF_8));

            HtmlPage svgImage = webClient.getPage(postRequest);
            validateBobAliceSvg(svgImage);
        }
    }

    private WebRequest createPostRequest(String contentType) throws MalformedURLException {
        String appUrl = System.getProperty("app.url", "http://localhost:8080");
        WebRequest postRequest = new WebRequest(new URL(appUrl + "/svg/"), HttpMethod.POST);
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
            validateBobAliceSvg(svgImage);
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

            HtmlPage svgImage = webClient.getPage(postRequest);
            assertThat(svgImage.getWebResponse().getStatusCode(), is(equalTo(400)));
        }
    }
}
