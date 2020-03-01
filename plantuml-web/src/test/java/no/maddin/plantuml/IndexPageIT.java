package no.maddin.plantuml;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import org.junit.ClassRule;
import org.junit.Test;

import java.net.URI;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

public class IndexPageIT {

    @ClassRule
    public static JettyServerRule jettyServer = JettyServerRule.create(8888, "target/plantuml-web.war");

    @Test
    public void indexPage() throws Exception {
        URI serverUri = jettyServer.getServer().getURI();
        WebConversation wc = new WebConversation();
        WebRequest wr = new GetMethodWebRequest(serverUri.toString());
        WebResponse response = wc.getResponse(wr);
        assertThat(response.getText(), containsString("PlantUML"));
    }
}
