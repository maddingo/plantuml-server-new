package no.maddin.plantuml.server;

import com.github.sparsick.testcontainers.gitserver.GitServerVersions;
import com.github.sparsick.testcontainers.gitserver.plain.GitServerContainer;
import net.sourceforge.plantuml.server.Application;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = Application.class)
@ActiveProfiles("test")
@Testcontainers
public class ProxyTest {

    @LocalServerPort
    private int port;

    @Container
    static GitServerContainer gitServer =
        new GitServerContainer(GitServerVersions.V2_43.getDockerImageName())
            .withSshKeyAuth()
            .withGitRepo("plantuml")
            .withCopyExistingGitRepoToContainer(new File(".").getAbsoluteFile().getParentFile().getParent())
        ;

    @DynamicPropertySource
    static void registerGitAuthProperties(DynamicPropertyRegistry registry) {
        //plantuml.git-auth[ssh://git@github.com/Martin-Goldhahn_lyse/lyt-architecture.git]
        String key = String.format("plantuml.git-auth[%s]", gitServer.getGitRepoURIAsSSH().toString());
        registry.add(key + ".private-key", () -> new String(gitServer.getSshClientIdentity().getPrivateKey()));
        registry.add(key + ".pass-phrase", () -> new String(gitServer.getSshClientIdentity().getPassphrase()));
    }

    @Test
    @Tag("smoke")
    void httpSrcRequest() throws Exception {

        try (
            WebClient webClient = new WebClient()
        ) {
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

    @Test
    void gitSrcRequest() throws Exception {

        try (WebClient webClient = new WebClient()) {
            String appUrl = "http://localhost:" + port;
            String currentBranch = "HEAD";
            WebRequest getRequest = new WebRequest(URI.create(appUrl + "/proxy").toURL(), HttpMethod.GET);
            getRequest.setAdditionalHeader("Accept", "*/*");
            getRequest.setAdditionalHeader("Referer", appUrl);
            String srcUri = String.format("git+%s?branch=%s#plantuml-web/src/test/resources/bob.puml", gitServer.getGitRepoURIAsSSH().toString(), currentBranch);
            getRequest.setRequestParameters(List.of(
                new NameValuePair("src", srcUri)
            ));

            HtmlPage page = webClient.getPage(getRequest);

            SvgTest.validateBobAliceSvg(page, "");
        }
    }

    @Test
    void gitSrcRequestFullUri() throws Exception {

        try (WebClient webClient = new WebClient()) {
            String appUrl = "http://localhost:" + port;
            String currentBranch = "HEAD";
            String srcUri = String.format("git+%s?branch=%s#plantuml-web/src/test/resources/bob.puml", gitServer.getGitRepoURIAsSSH().toString(), currentBranch);
            URI requestUri = URI.create(appUrl + "/proxy?src=" + URLEncoder.encode(srcUri, StandardCharsets.UTF_8));
            WebRequest getRequest = new WebRequest(requestUri.toURL(), HttpMethod.GET);
            getRequest.setAdditionalHeader("Accept", "*/*");
            getRequest.setAdditionalHeader("Referer", appUrl);
            getRequest.setRequestParameters(List.of(
                new NameValuePair("src", srcUri)
            ));

            HtmlPage page = webClient.getPage(getRequest);

            SvgTest.validateBobAliceSvg(page, "");
        }
    }
}
