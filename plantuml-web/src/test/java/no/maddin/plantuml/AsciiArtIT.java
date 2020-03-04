package no.maddin.plantuml;

import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.WebClient;
import net.sourceforge.plantuml.code.TranscoderUtil;
import net.sourceforge.plantuml.version.Version;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class AsciiArtIT {
    private static String versionDiagram;
    private static String bobAlice;

    @BeforeClass
    public static void init() throws Exception {
        versionDiagram = TranscoderUtil.getDefaultTranscoder().encode("@startuml\nversion\n@enduml");
        bobAlice = TranscoderUtil.getDefaultTranscoder().encode("@startuml\nBob -> Alice : hello\n@enduml");
    }

    @Test
    public void versionText() throws Exception {
        try (WebClient webClient = new WebClient()) {
            String appUrl = System.getProperty("app.url", "http://localhost:8080");
            TextPage page = webClient.getPage(appUrl + "/txt/" + versionDiagram);

            assertThat(page.getWebResponse().getContentType(), equalTo("text/plain"));
            assertThat(page.getContent(), containsString("PlantUML version " + Version.versionString()));
        }
    }

    @Test
    public void bobAliceText() throws Exception {
        try (WebClient webClient = new WebClient()) {
            String appUrl = System.getProperty("app.url", "http://localhost:8080");
            TextPage page = webClient.getPage(appUrl + "/txt/" + bobAlice);

            assertThat(page.getWebResponse().getContentType(), equalTo("text/plain"));
            assertThat(page.getContent(), containsString("Bob"));
        }
    }
}
