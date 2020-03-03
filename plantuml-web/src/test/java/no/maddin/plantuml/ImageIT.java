package no.maddin.plantuml;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlImage;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import net.sourceforge.plantuml.code.TranscoderUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ImageIT {

    private static String versionDiagram;
    private static String bobAlice;

    @BeforeClass
    public static void init() throws Exception {
        versionDiagram = TranscoderUtil.getDefaultTranscoder().encode("@startuml\nversion\n@enduml");
        bobAlice = TranscoderUtil.getDefaultTranscoder().encode("@startuml\nBob -> Alice : hello\n@enduml");
    }

    @Test
    public void versionImage() throws Exception {
        try (WebClient webClient = new WebClient()) {
            String appUrl = System.getProperty("app.url", "http://localhost:8080");
            HtmlPage page = webClient.getPage(appUrl + "/uml/" + versionDiagram);

            byte[] white = new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff};
            byte[] burgundy = new byte[] {(byte) 0x99, (byte) 0x10, (byte) 0x39};
            HtmlImage img = page.getFirstByXPath("//p[@id='diagram']/img");
            WebResponse webResponse = img.getWebResponse(true);
            assertThat(webResponse.getContentType(), equalTo("image/png"));
            try (InputStream is = webResponse.getContentAsStream()) {
                BufferedImage bimg = ImageIO.read(is);

                Object topLeft = bimg.getRaster().getDataElements(0, 0, null);
                assertThat(topLeft, is(equalTo(white)));

                Object logoPixel = bimg.getRaster().getDataElements(445, 305, null);
                assertThat(logoPixel, is(equalTo(burgundy)));

            }
        }
    }

    @Test
    public void diagramHttpHeader() throws Exception {
        try (WebClient webClient = new WebClient()) {
            String appUrl = System.getProperty("app.url", "http://localhost:8080");
            HtmlPage page = webClient.getPage(appUrl + "/uml/" + bobAlice);
            HtmlImage img = page.getFirstByXPath("//p[@id='diagram']/img");

            WebResponse webResponse = img.getWebResponse(true);
            assertThat(webResponse.getContentType(), equalTo("image/png"));

            String headerValue = webResponse.getResponseHeaderValue("Last-Modified");

            ZonedDateTime lastModified = ZonedDateTime.parse(headerValue, DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz"));

            assertTrue(lastModified.isBefore(ZonedDateTime.now()));
        }
    }
}
