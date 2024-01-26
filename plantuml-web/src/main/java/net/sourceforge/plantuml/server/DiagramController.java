/* ========================================================================
 * PlantUML : a free UML diagram generator
 * ========================================================================
 *
 * Project Info:  http://plantuml.sourceforge.net
 *
 * This file is part of PlantUML.
 *
 * PlantUML is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PlantUML distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 */
package net.sourceforge.plantuml.server;

import com.jcraft.jsch.*;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.plantuml.*;
import net.sourceforge.plantuml.code.TranscoderUtil;
import net.sourceforge.plantuml.core.Diagram;
import net.sourceforge.plantuml.core.DiagramDescription;
import net.sourceforge.plantuml.core.ImageData;
import net.sourceforge.plantuml.error.PSystemError;
import net.sourceforge.plantuml.server.PlantumlConfigProperties.GitAuthConfig;
import net.sourceforge.plantuml.syntax.LanguageDescriptor;
import net.sourceforge.plantuml.version.Version;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;


/**
 * Common service servlet to produce diagram from compressed UML source contained in the end part of the requested URI.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DiagramController {

    private static final String POWERED_BY = "PlantUML Version " + Version.versionString();
    private static final Map<FileFormat, String> CONTENT_TYPE;
    static {
        CONTENT_TYPE = Map.of(
            FileFormat.PNG, "image/png",
            FileFormat.SVG, "image/svg+xml",
            FileFormat.EPS, "application/postscript",
            FileFormat.UTXT, "text/plain;charset=UTF-8",
            FileFormat.BASE64, "text/plain; charset=x-user-defined");
    }

    private final PlantumlConfigProperties config;

    @PostConstruct
    private void init() {
        SshSessionFactory.setInstance(new MemoryJschConfigSessionFactory(config.getGitAuth()) {});
    }

    @RequestMapping(
        path = "/svg/{encodedDiagram}",
        method = {RequestMethod.GET},
        produces = "image/svg+xml"
    )
    public ResponseEntity<?> getSvg(@PathVariable("encodedDiagram") String encodedDiagram, HttpServletRequest request, HttpServletResponse response) throws IOException {
        return doGet(encodedDiagram, request, FileFormat.SVG);
    }

    @RequestMapping(
        path = "/svg",
        method = {RequestMethod.POST},
        consumes = MediaType.TEXT_PLAIN_VALUE,
        produces = "image/svg+xml"
    )
    public ResponseEntity<?> postSvgPlain(@RequestBody String uml, HttpServletRequest request, HttpServletResponse response) throws IOException {
        return entity(FileFormat.SVG, request, uml, 0);
    }

    @RequestMapping(
        path = "/txt/{encodedDiagram}",
        method = {RequestMethod.GET},
        produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<?> getTxt(@PathVariable("encodedDiagram") String encodedDiagram, HttpServletRequest request, HttpServletResponse response) throws IOException {
        return doGet(encodedDiagram, request, FileFormat.UTXT);
    }

    @RequestMapping(
        path = "/png/{encodedDiagram}",
        method = {RequestMethod.GET},
        produces = MediaType.IMAGE_PNG_VALUE
    )
    public ResponseEntity<?> getPng(@PathVariable("encodedDiagram") String encodedDiagram, HttpServletRequest request, HttpServletResponse response) throws IOException {
        return doGet(encodedDiagram, request, FileFormat.PNG);
    }

    @RequestMapping(
        path = "/language",
        method = RequestMethod.GET,
        produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<?> getLanguage() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new LanguageDescriptor().print(new PrintStream(baos));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        return new ResponseEntity<>(baos.toString(), headers, HttpStatus.OK);
    }

    @RequestMapping(
        path = "/default-diagram",
        method = {RequestMethod.GET},
        produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> getDefaultDiagram(
        @RequestParam(name = "count", required = false, defaultValue = "4") int count,
        HttpServletRequest request, HttpServletResponse response
    ) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("Text Before\n");
            sb.append("@startuml\nBob").append(i).append(" -> Alice : hello\n@enduml\n");
            sb.append("Text After\n");
        }
        return new ResponseEntity<>(sb.toString(), HttpStatus.OK);
    }

    @RequestMapping(
        path = "/proxy",
        method = RequestMethod.GET
    )
    public ResponseEntity<?> getProxy(
        @RequestParam(name = "src") String src,
        @RequestParam(name = "fmt", required = false, defaultValue = "svg") String format,
        @RequestParam(name = "idx", required = false, defaultValue = "0") int index,
        HttpServletRequest request) throws IOException
    {
        FileFormat outputFormat = FileFormat.valueOf(format.toUpperCase());
        String uml = getDiagramSource(src, index);
        return entity(outputFormat, request, uml, 0);
    }

    private String getDiagramSource(String srcUriString, int index) throws IOException {
        URI srcUri = URI.create(srcUriString);
        String scheme = srcUri.getScheme();
        String sourceText = switch (scheme) {
            case "http", "https" -> getHttpSourceDocument(srcUri.toURL());
            case "git+ssh" -> getGitSourceDocument(srcUri);
            default -> throw new IllegalArgumentException("Unsupported scheme: " + scheme);
        };

        return findIndexedSource(sourceText, index);
    }

    private String findIndexedSource(String sourceText, int index) {
        int startIdx = sourceText.indexOf("@startuml");
        for (int i = 0; i < index && startIdx >= 0; i++) {
            startIdx = sourceText.indexOf("@startuml", startIdx + 8);
        }
        if (startIdx >= 0) {
            int endIdx = sourceText.indexOf("@enduml", startIdx);
            if (endIdx >= 0) {
                return sourceText.substring(startIdx, endIdx + 7) + "\n";
            }
        }

        return "@startuml\n@enduml";
    }

    private String getGitSourceDocument(URI srcUri) {
        String gitUri = repoUri(srcUri);
        String branch = branch(srcUri, "main");
        try (
            CloseableTempDir tempDir = new CloseableTempDir("plantuml-git-");
            Git git = Git.cloneRepository().setURI(gitUri).setBare(false).setBranch(branch).setCloneAllBranches(false).setDirectory(tempDir.getFile()).call()
        ) {
            String gitFile = srcUri.getFragment();
            Path gitPath = tempDir.getPath().resolve(gitFile);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                Files.copy(gitPath, baos);
                return baos.toString();
            }
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    private static String repoUri(URI srcUri) {
        try {
            String scheme = switch (srcUri.getScheme()) {
                case "git+ssh" -> "ssh";
                default -> srcUri.getScheme();
            };
            return new URI(scheme, srcUri.getUserInfo(), srcUri.getHost(), srcUri.getPort(), srcUri.getPath(), null, null).toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Extract the branch name from the query string parameter 'branch' or return the default branch name.
     */
    private static String branch(URI srcUri, String defaultBranch) {
        return Optional.ofNullable(srcUri.getQuery())
            .map(query -> query.split("&"))
            .flatMap(queries -> Stream.of(queries)
                .map(query -> query.split("="))
                .filter(query -> query.length == 2 && "branch".equals(query[0]))
                .map(query -> query[1])
                .findFirst()
            )
            .orElse(defaultBranch);
    }

    private String getHttpSourceDocument(URL srcUrl) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL);

        Optional<Authenticator> authConfig = getHttpAuthenticator(srcUrl);
        if (authConfig.isPresent()) {
            clientBuilder = clientBuilder.authenticator(authConfig.get());
        }
        try (HttpClient client = clientBuilder.build()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(srcUrl.toURI()).GET().build(), HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | URISyntaxException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<Authenticator> getHttpAuthenticator(URL srcUrl) {
        String authKey = getAuthKey(srcUrl);
        return Optional.ofNullable(config.getHttpAuth())
            .flatMap(auth -> Optional.ofNullable(auth.get(authKey)))
            .map(auth -> new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(auth.getUsername(), auth.getPassword().toCharArray());
                }
            });
    }

    private static String getAuthKey(URL srcUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append(srcUrl.getProtocol()).append("://").append(srcUrl.getHost());
        if (srcUrl.getPort() > 0) {
            sb.append(":").append(srcUrl.getPort());
        }
        return sb.toString();
    }

    private ResponseEntity<?> doGet(String encodedDiagram, HttpServletRequest request, FileFormat outputFormat) throws IOException {

        // build the UML source from the compressed request parameter
        try {
            String uml = TranscoderUtil.getDefaultTranscoder().decode(encodedDiagram);
            return entity(outputFormat, request, uml, 0);
        } catch (Exception e) {
            log.error("Extract UML source", e);
            return ResponseEntity.badRequest().build();
        }
    }

    static ResponseEntity<?> entity(FileFormat format, HttpServletRequest request, String uml, int idx) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.setContentType(MediaType.parseMediaType(CONTENT_TYPE.get(format)));
        SourceStringReader reader = new SourceStringReader(uml);
        if (format == FileFormat.BASE64) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DiagramDescription result = reader.outputImage(baos, idx, new FileFormatOption(FileFormat.PNG));
            baos.close();

            final String encodedBytes = "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(baos.toByteArray()).replaceAll("\\s", "");
            return new ResponseEntity<>(encodedBytes, headers, HttpStatus.OK);
        }
        final BlockUml blockUml = reader.getBlocks().getFirst();
        if (notModified(request, blockUml)) {
            addHeaderForCache(headers, blockUml);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).headers(headers).build();
        }
        if (StringUtils.isDiagramCacheable(uml)) {
            addHeaderForCache(headers, blockUml);
        }
        final Diagram diagram = blockUml.getDiagram();
        if (diagram instanceof PSystemError err) {
            log.error("Diagram generation Error: {} ({})", err.getDescription(), err.getLineLocation().toString());
            return ResponseEntity.badRequest().build();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final ImageData result = diagram.exportDiagram(baos, idx, new FileFormatOption(format));
        return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
    }

    private static boolean notModified(HttpServletRequest request, BlockUml blockUml) {
        final String ifNoneMatch = request.getHeader("If-None-Match");
        final long ifModifiedSince = request.getDateHeader("If-Modified-Since");
        if (ifModifiedSince != -1 && ifModifiedSince != blockUml.lastModified()) {
            return false;
        }
        final String etag = blockUml.etag();
        if (ifNoneMatch == null) {
            return false;
        }
        return ifNoneMatch.contains(etag);
    }

    private static void addHeaderForCache(HttpHeaders headers, BlockUml blockUml) {
        long today = System.currentTimeMillis();
        // Add http headers to force the browser to cache the image
        final int maxAge = 3600 * 24 * 5;
        headers.setExpires(today + 1000L * maxAge);
        headers.setDate(today);

        headers.setLastModified(blockUml.lastModified());
        headers.setCacheControl("public, max-age=" + maxAge);
        // response.addHeader("Cache-Control", "max-age=864000");
        headers.setETag("\"" + blockUml.etag() + "\"");
        final Diagram diagram = blockUml.getDiagram();
        headers.add("X-PlantUML-Diagram-Description", diagram.getDescription().getDescription());
        if (diagram instanceof PSystemError error) {
            for (ErrorUml err : error.getErrorsUml()) {
                headers.add("X-PlantUML-Diagram-Error", err.getError());
                headers.add("X-PlantUML-Diagram-Error-Line", "" + err.getLineLocation().getPosition());
            }
        }
        addHeaders(headers);
    }

    private static void addHeaders(HttpHeaders headers) {
        headers.add("X-Powered-By", POWERED_BY);
        headers.add("X-Patreon", "Support us on http://plantuml.com/patreon");
        headers.add("X-Donate", "http://plantuml.com/paypal");
    }

    /**
     * A {@link JschConfigSessionFactory} that uses a {@link Map} to store the ssh key.
     */
    private static class MemoryJschConfigSessionFactory extends JschConfigSessionFactory {

        private final Map<String, GitAuthConfig> authConfigMap;

        private MemoryJschConfigSessionFactory(Map<String, GitAuthConfig> authConfigMap) {
            this.authConfigMap = authConfigMap;
        }

        @Override
        public synchronized RemoteSession getSession(URIish uri, CredentialsProvider credentialsProvider, FS fs, int tms) throws TransportException {
            return Optional.ofNullable(authConfigMap.get(uri.toString()))
//                .map(PlantumlConfigProperties.GitAuthConfig::getSshKey)
                .flatMap(gitAuth -> createJsch(uri, gitAuth))
                .map(jsch -> {
                    try {
                        int port = uri.getPort();
                        if (port <= 0) {
                            port = 22;
                        }
                        Session session = jsch.getSession(uri.getUser(), uri.getHost(), port);
                        session.setConfig("PreferredAuthentications", "publickey");
                        session.setConfig("StrictHostKeyChecking", "no");
                        session.connect();
                        return session;
                    } catch (JSchException e) {
                        log.error("Failed to create session", e);
                        return null;
                    }
                })
                .map(s -> new JschSession(s,uri))
                .orElseThrow();
        }

        private Optional<JSch> createJsch(URIish requestUri, GitAuthConfig authConfig) {
            try {
                String identity = new URI(requestUri.getScheme(), requestUri.getUser(), requestUri.getHost(), requestUri.getPort(), null, null, null).toString();
                JSch jsch = new JSch();
                jsch.addIdentity(
                    identity,
                    gitConfig(authConfig, GitAuthConfig::getPrivateKey),
                    gitConfig(authConfig, GitAuthConfig::getPublicKey),
                    gitConfig(authConfig, GitAuthConfig::getPassPhrase));
                return Optional.of(jsch);
            } catch (JSchException | URISyntaxException e) {
                log.error("Failed to add identity", e);
                return Optional.empty();
            }
        }

        private byte[] gitConfig(GitAuthConfig authConfig, Function<GitAuthConfig, String> parameterFunction) {
            return Optional.ofNullable(authConfig)
                .map(parameterFunction)
                .map(String::getBytes)
                .orElse(null);
        }

        @Override
        public String getType() {
            return super.getType();
        }

        @Override
        protected Session createSession(OpenSshConfig.Host hc, String user, String host, int port, FS fs) throws JSchException {
            return super.createSession(hc, user, host, port, fs);
        }

        @Override
        protected void configureJSch(JSch jsch) {
            super.configureJSch(jsch);
        }

        @Override
        protected JSch getJSch(OpenSshConfig.Host hc, FS fs) throws JSchException {
            return super.getJSch(hc, fs);
        }

        @Override
        protected JSch createDefaultJSch(FS fs) throws JSchException {
            return super.createDefaultJSch(fs);
        }

        @Override
        public void releaseSession(RemoteSession session) {
            super.releaseSession(session);
        }

        @Override
        protected void configure(OpenSshConfig.Host hc, Session session) {
            super.configure(hc, session);
        }
    }

    private static class CloseableTempDir implements AutoCloseable {

        private final Path tempDir;

        private CloseableTempDir(String prefix) {
            try {
                this.tempDir = Files.createTempDirectory(prefix);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private Path getPath() {
            return tempDir;
        }

        private File getFile() {
            return tempDir.toFile();
        }

        @Override
        public void close() {
            deleteTempDir(tempDir);
        }

        private static void deleteTempDir(Path tempDir) {

            if (tempDir == null || !Files.exists(tempDir)) {
                return;
            }
            try ( Stream<Path> walkStream = Files.walk(tempDir)) {
                walkStream
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }
}