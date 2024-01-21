package net.sourceforge.plantuml.server;

import net.sourceforge.plantuml.OptionFlags;
import net.sourceforge.plantuml.code.NoPlantumlCompressionException;
import net.sourceforge.plantuml.code.Transcoder;
import net.sourceforge.plantuml.code.TranscoderUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;

@Controller
public class UIController {

    static final String DEFAULT_ENCODED_TEXT = "SyfFKj2rKt3CoKnELR1Io4ZDoSa70000";

    @GetMapping("/")
    public String index(Model model) {return "redirect:/uml/" + DEFAULT_ENCODED_TEXT;
    }

    @GetMapping("/uml/{encodedDiagram}")
    public String uml(Model model, @PathVariable("encodedDiagram") String encodedDiagram, UriComponentsBuilder uriBuilder) throws NoPlantumlCompressionException {
        updateModel(model, encodedDiagram, uriBuilder);
        return "uml";
    }

    @ExceptionHandler(value = {NoPlantumlCompressionException.class, IOException.class})
    public String error(Model model) {
        model.addAttribute("error", "ex.getMessage()");
        return "uml";
    }

    private void updateModel(Model model, String encodedDiagram, UriComponentsBuilder uriBuilder) throws NoPlantumlCompressionException {
        model.addAttribute("decodedDiagram", getTranscoder().decode(encodedDiagram));
        model.addAttribute("encodedDiagram", encodedDiagram);
        model.addAttribute("diagramImage", "/png/" + encodedDiagram);
        model.addAttribute("diagramUrl", uriBuilder.pathSegment("png").pathSegment(encodedDiagram).build());
        model.addAttribute("serverImpressum", "PlantUML"); // TODO: add version
    }

    @PostMapping(
        path = "/form",
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    public String form(Model model, UriComponentsBuilder uriBuilder, @RequestParam("text") String text) throws IOException {
        String encodedText;
        if (text != null) {
            encodedText = getTranscoder().encode(text);
        } else {
            encodedText = DEFAULT_ENCODED_TEXT;
        }
//        updateModel(model, encodedText, uriBuilder);
        return "redirect:/uml/"+encodedText;
    }

    @PostMapping(
        path = "/url",
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    public String url(Model model, UriComponentsBuilder uriBuilder, @RequestParam("url") String url) throws IOException {
        String encodedText = UriComponentsBuilder.fromHttpUrl(url).build().getPathSegments().stream().skip(1).findAny().orElse(DEFAULT_ENCODED_TEXT);
        return "redirect:/uml/"+encodedText;
    }

    private Transcoder getTranscoder() {
        return TranscoderUtil.getDefaultTranscoder();
    }
}
