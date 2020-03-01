package no.maddin.plantuml;

import org.apache.jasper.runtime.JspFactoryImpl;
import org.eclipse.jetty.quickstart.PreconfigureQuickStartWar;
import org.eclipse.jetty.quickstart.QuickStartWebApp;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.JarResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import javax.servlet.jsp.JspFactory;
import java.io.File;
import java.net.Inet4Address;

public class JettyServerRule extends TemporaryFolder {
    private final int port;
    private final File warFile;
    private Server server;

    private JettyServerRule(int port, File warFile) {
        this.port = port;
        this.warFile = warFile;
    }

    public Server getServer() {
        return server;
    }

    /**
     * see https://examples.javacodegeeks.com/enterprise-java/jetty/jetty-jsp-example/
     */
    @Override
    protected void before() throws Throwable {
        super.before();
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv4Addresses", "true");
        server = new Server(port);

        Resource war = Resource.newResource(warFile);

        WebAppContext webApp = new WebAppContext(war, "/");
        webApp.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",".*/[^/]*jstl.*\\.jar$");

        //4. Enabling the Annotation based configuration
        Configuration.ClassList classlist = Configuration.ClassList.setServerDefault(server);
        classlist.addAfter("org.eclipse.jetty.webapp.FragmentConfiguration", "org.eclipse.jetty.plus.webapp.EnvConfiguration", "org.eclipse.jetty.plus.webapp.PlusConfiguration");
        classlist.addBefore("org.eclipse.jetty.webapp.JettyWebXmlConfiguration", "org.eclipse.jetty.annotations.AnnotationConfiguration");

        server.setHandler(webApp);
        server.start();
    }

    @Override
    protected void after() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                throw new IllegalStateException("Cannot stop server");
            }
            server.destroy();
        }
        super.after();
    }

    public static JettyServerRule   create(int port, String warFile) {
        File warFileChecked = new File(warFile).getAbsoluteFile();
        if (!warFileChecked.canRead())
            throw new IllegalArgumentException(warFile);
        return new JettyServerRule(port, warFileChecked);
    }
}
