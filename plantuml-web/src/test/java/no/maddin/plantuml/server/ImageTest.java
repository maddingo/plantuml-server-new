package no.maddin.plantuml.server;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlImage;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import net.sourceforge.plantuml.code.TranscoderUtil;
import net.sourceforge.plantuml.server.Application;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ContextConfiguration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = Application.class)
public class ImageTest {

    private static String versionDiagram;
    private static String bobAlice;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void init() throws Exception {
        versionDiagram = TranscoderUtil.getDefaultTranscoder().encode("@startuml\nversion\n@enduml");
        bobAlice = TranscoderUtil.getDefaultTranscoder().encode("@startuml\nBob -> Alice : hello\n@enduml");
    }

    @Test
    void versionImage() throws Exception {
        try (WebClient webClient = new WebClient()) {
            String appUrl = "http://localhost:" + port;
            Page page = webClient.getPage(appUrl + "/uml/" + versionDiagram);

            byte[] white = new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff};
            byte[] burgundy = new byte[] {(byte) 0x99, (byte) 0x10, (byte) 0x39};
            HtmlImage img = ((HtmlPage)page).getFirstByXPath("//div[@id='diagram']/img");
            WebResponse webResponse = img.getWebResponse(true);
            assertThat(webResponse.getContentType(), equalTo("image/png"));
            try (InputStream is = webResponse.getContentAsStream()) {
                BufferedImage bimg = ImageIO.read(is);

                Object topLeft = bimg.getRaster().getDataElements(0, 0, null);
                assertThat(topLeft, is(equalTo(white)));

                // The format changes from release to release, remove assertions
//                int checkHeight = Math.min(bimg.getHeight() - 1, 305);
//                int checkWidth = Math.min(bimg.getWidth() - 1, 445);
//                Object logoPixel = bimg.getRaster().getDataElements(checkWidth, checkHeight, null);
//                assertThat(logoPixel, is(equalTo(burgundy)));
            }
        }
    }

    @Test
    public void diagramHttpHeader() throws Exception {
        try (WebClient webClient = new WebClient()) {
            String appUrl = "http://localhost:" + port;
            HtmlPage page = webClient.getPage(appUrl + "/uml/" + bobAlice);
            HtmlImage img = page.getFirstByXPath("//div[@id='diagram']/img");

            WebResponse webResponse = img.getWebResponse(true);
            assertThat(webResponse.getContentType(), equalTo("image/png"));

            String headerValue = webResponse.getResponseHeaderValue("Last-Modified");

            ZonedDateTime lastModified = ZonedDateTime.parse(headerValue, DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz"));

            assertTrue(lastModified.isBefore(ZonedDateTime.now()));
        }
    }
}
