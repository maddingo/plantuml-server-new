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

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.OptionFlags;
import net.sourceforge.plantuml.server.utility.UmlExtractor;
import net.sourceforge.plantuml.syntax.LanguageDescriptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Common service servlet to produce diagram from compressed UML source contained in the end part of the requested URI.
 */
@Slf4j
@Controller
public class DiagramController {

    static {
        OptionFlags.ALLOW_INCLUDE = "true".equalsIgnoreCase(System.getenv("ALLOW_PLANTUML_INCLUDE"));
    }

    @RequestMapping(
        path = "/svg/{encodedDiagram}",
        method = {RequestMethod.GET},
        produces = "image/svg+xml"
    )
    public ResponseEntity<?> getSvg(@PathVariable String encodedDiagram, HttpServletRequest request, HttpServletResponse response) throws IOException {
        return doGet(encodedDiagram, request, response, FileFormat.SVG);
    }

    @RequestMapping(
        path = "/svg/",
        method = {RequestMethod.POST},
        produces = "image/svg+xml"
    )
    public ResponseEntity<?> postSvg(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return doPost(request, response, FileFormat.SVG);
    }


    @RequestMapping(
        path = "/txt/{encodedDiagram}",
        method = {RequestMethod.GET},
        produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<?> getTxt(@PathVariable String encodedDiagram, HttpServletRequest request, HttpServletResponse response) throws IOException {
        return doGet(encodedDiagram, request, response, FileFormat.UTXT);
    }

    @RequestMapping(
        path = "/png/{encodedDiagram}",
        method = {RequestMethod.GET},
        produces = MediaType.IMAGE_PNG_VALUE
    )
    public ResponseEntity<?> getPng(@PathVariable String encodedDiagram, HttpServletRequest request, HttpServletResponse response) throws IOException {
        return doGet(encodedDiagram, request, response, FileFormat.PNG);
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

    private ResponseEntity<?> doGet(String encodedDiagram, HttpServletRequest request, HttpServletResponse response, FileFormat outputFormat) throws IOException {

        // build the UML source from the compressed request parameter
        final String uml;
        try {
            uml = UmlExtractor.getUmlSource(encodedDiagram);
        } catch (Exception e) {
            log.error("Extract UML source", e);
            return ResponseEntity.badRequest().build();
        }

        return doDiagramResponse(request, response, uml, 0, outputFormat);
    }

    private ResponseEntity<?> doPost(HttpServletRequest request, HttpServletResponse response, FileFormat outputFormat) throws IOException {

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

        return doDiagramResponse(request, response, uml.toString(), idx, outputFormat);
    }

    private ResponseEntity<?> doDiagramResponse(
        HttpServletRequest request,
        HttpServletResponse response,
        String uml,
        int idx,
        FileFormat outputFormat)
        throws IOException
    {

        return new DiagramResponse(response, outputFormat, request).sendDiagram(uml, idx);
    }

    private static final Pattern RECOVER_UML_PATTERN = Pattern.compile("/\\w+/(\\d+/)?(.*)");

    /**
     * Extracts the compressed UML source from the HTTP URI.
     *
     * @return the compressed UML source
     */
    private final String[] getSourceAndIdx(HttpServletRequest request) {
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
