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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.code.TranscoderUtil;
import net.sourceforge.plantuml.syntax.LanguageDescriptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Common service servlet to produce diagram from compressed UML source contained in the end part of the requested URI.
 */
@Slf4j
@Controller
public class DiagramController {

    @RequestMapping(
        path = "/svg/{encodedDiagram}",
        method = {RequestMethod.GET},
        produces = "image/svg+xml"
    )
    public ResponseEntity<?> getSvg(@PathVariable("encodedDiagram") String encodedDiagram, HttpServletRequest request, HttpServletResponse response) throws IOException {
        return doGet(encodedDiagram, request, FileFormat.SVG);
    }

    @RequestMapping(
        path = "/svg/",
        method = {RequestMethod.POST},
        produces = "image/svg+xml"
    )
    public ResponseEntity<?> postSvg(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return doPost(request, FileFormat.SVG);
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
        return doDiagramResponse(request, uml, 0, outputFormat);
    }

    private String getDiagramSource(String srcUri, int index) throws IOException {
        URL srcUrl = URI.create(srcUri).toURL();
        String scheme = srcUrl.getProtocol();
        String sourceText = switch (scheme) {
            case "http", "https" -> getHttpSourceDocument(srcUrl);
            case "git" -> getGitSourceDocument(srcUrl);
            default -> throw new IllegalArgumentException("Unsupported scheme: " + scheme);
        };

        return findIndexedSource(sourceText, index);
    }

    private String findIndexedSource(String sourceText, int index) {
        int startIdx = sourceText.indexOf("@startuml", 0);;
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

    private String getGitSourceDocument(URL srcUrl) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private String getHttpSourceDocument(URL srcUrl) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL);

        Optional<Authenticator> authConfig = getAuthenticator(srcUrl);
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

    // TODO get auth parameters from URL
    private Optional<Authenticator> getAuthenticator(URL srcUrl) {
        return Optional.empty();
    }

    private ResponseEntity<?> doGet(String encodedDiagram, HttpServletRequest request, FileFormat outputFormat) throws IOException {

        // build the UML source from the compressed request parameter
        final String uml;
        try {
            uml = TranscoderUtil.getDefaultTranscoder().decode(encodedDiagram);
        } catch (Exception e) {
            log.error("Extract UML source", e);
            return ResponseEntity.badRequest().build();
        }

        return doDiagramResponse(request, uml, 0, outputFormat);
    }

    private ResponseEntity<?> doPost(HttpServletRequest request, FileFormat outputFormat) throws IOException {

        // build the UML source from the compressed request parameter
        final String[] sourceAndIdx = getSourceAndIdx(request);
        final int idx = Integer.parseInt(sourceAndIdx[1]);

        final StringBuilder uml = new StringBuilder();
        final BufferedReader in = request.getReader();
        while (true) {
            final String line = in.readLine();
            if (line == null) {
                break;
            }
            uml.append(line).append('\n');
        }

        return doDiagramResponse(request, uml.toString(), idx, outputFormat);
    }

    private ResponseEntity<?> doDiagramResponse(
        HttpServletRequest request,
        String uml,
        int idx,
        FileFormat outputFormat)
        throws IOException
    {

        return new DiagramResponse(outputFormat, request).entity(uml, idx);
    }

    private static final Pattern RECOVER_UML_PATTERN = Pattern.compile("/\\w+/(\\d+/)?(.*)");

    /**
     * Extracts the compressed UML source from the HTTP URI.
     *
     * @return the compressed UML source
     */
    private String[] getSourceAndIdx(HttpServletRequest request) {
        final Matcher recoverUml = RECOVER_UML_PATTERN.matcher(
            request.getRequestURI().substring(
            request.getContextPath().length()));
        // the URL form has been submitted
        if (recoverUml.matches()) {
            final String data = recoverUml.group(2);
            if (data.length() >= 4) {
                String idx = recoverUml.group(1);
                if (idx == null) {
                    idx = "0";
                } else {
                    idx = idx.substring(0, idx.length() - 1);
                }
                return new String[]{data, idx };
            }
        }
        return new String[]{"", "0" };
    }

}
