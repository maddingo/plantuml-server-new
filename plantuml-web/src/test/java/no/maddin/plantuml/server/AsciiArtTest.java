package no.maddin.plantuml.server;

import net.sourceforge.plantuml.code.TranscoderUtil;
import net.sourceforge.plantuml.server.Application;
import net.sourceforge.plantuml.version.Version;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = Application.class)
public class AsciiArtTest {

    private static String versionDiagram;
    private static String bobAlice;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @BeforeAll
    static void init() throws Exception {
        versionDiagram = TranscoderUtil.getDefaultTranscoder().encode("@startuml\nversion\n@enduml");
        bobAlice = TranscoderUtil.getDefaultTranscoder().encode("@startuml\nBob -> Alice : hello\n@enduml");
    }

    @Test
    void versionText() {
        ResponseEntity<String> txtResponse = rest.getForEntity("http://localhost:" + port + "/txt/" + versionDiagram, String.class);
        assertThat(txtResponse, hasProperty("headers", hasProperty("contentType", compatibleMediaType(MediaType.TEXT_PLAIN))));
        assertThat(txtResponse, hasProperty("body", containsString("PlantUML version " + Version.versionString())));
    }

    @Test
    void bobAliceText() {
        ResponseEntity<String> txtResponse = rest.getForEntity("http://localhost:" + port + "/txt/" + bobAlice, String.class);
        assertThat(txtResponse, hasProperty("headers", hasProperty("contentType", compatibleMediaType(MediaType.TEXT_PLAIN))));
        assertThat(txtResponse, hasProperty("body", containsString("Bob")));
    }

    private static Matcher<MediaType> compatibleMediaType(MediaType mediaType) {
        return new TypeSafeDiagnosingMatcher<MediaType>() {
            @Override
            protected boolean matchesSafely(MediaType item, Description mismatchDescription) {
                return item.isCompatibleWith(mediaType);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("is compatible with MediaType ").appendValue(mediaType);
            }
        };
    }
}
