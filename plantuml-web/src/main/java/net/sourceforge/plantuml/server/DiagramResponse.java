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
import net.sourceforge.plantuml.*;
import net.sourceforge.plantuml.code.Base64Coder;
import net.sourceforge.plantuml.core.Diagram;
import net.sourceforge.plantuml.core.DiagramDescription;
import net.sourceforge.plantuml.core.ImageData;
import net.sourceforge.plantuml.error.PSystemError;
import net.sourceforge.plantuml.version.Version;
import org.springframework.http.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;


/**
 * Delegates the diagram generation from the UML source and the filling of the HTTP response with the diagram in the
 * right format. Its own responsibility is to produce the right HTTP headers.
 */
@Slf4j
class DiagramResponse {

    private static final String POWERED_BY = "PlantUML Version " + Version.versionString();

    private FileFormat format;
    private HttpServletRequest request;
    private static final Map<FileFormat, String> CONTENT_TYPE;
    static {
        CONTENT_TYPE = Map.of(
                FileFormat.PNG, "image/png",
                FileFormat.SVG, "image/svg+xml",
                FileFormat.EPS, "application/postscript",
                FileFormat.UTXT, "text/plain;charset=UTF-8",
                FileFormat.BASE64, "text/plain; charset=x-user-defined");
    }
    static {
        OptionFlags.ALLOW_INCLUDE = "true".equalsIgnoreCase(System.getenv("ALLOW_PLANTUML_INCLUDE"));
    }

    DiagramResponse(HttpServletResponse r, FileFormat f, HttpServletRequest rq) {
        format = f;
        request = rq;
    }

    ResponseEntity<?> sendDiagram(String uml, int idx) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.setContentType(MediaType.parseMediaType(getContentType()));
        SourceStringReader reader = new SourceStringReader(uml);
        if (format == FileFormat.BASE64) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DiagramDescription result = reader.outputImage(baos, idx, new FileFormatOption(FileFormat.PNG));
            baos.close();
            final String encodedBytes = "data:image/png;base64,"
                + Base64Coder.encodeLines(baos.toByteArray()).replaceAll("\\s", "");
            return new ResponseEntity<>(encodedBytes, headers, HttpStatus.OK);
        }
        final BlockUml blockUml = reader.getBlocks().get(0);
        if (notModified(blockUml)) {
            addHeaderForCache(headers, blockUml);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).headers(headers).build();
        }
        if (StringUtils.isDiagramCacheable(uml)) {
            addHeaderForCache(headers, blockUml);
        }
        final Diagram diagram = blockUml.getDiagram();
        if (diagram instanceof PSystemError) {
            PSystemError err = (PSystemError) diagram;
            log.error("Diagram generation Error: {} ({})", err.getDescription(), err.getLineLocation().toString());
            return ResponseEntity.badRequest().build();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final ImageData result = diagram.exportDiagram(baos, idx, new FileFormatOption(format));
        return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
    }

    @Deprecated
    private boolean notModified(BlockUml blockUml) {
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
        if (diagram instanceof PSystemError) {
            final PSystemError error = (PSystemError) diagram;
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

    private String getContentType() {
        return CONTENT_TYPE.get(format);
    }

}
