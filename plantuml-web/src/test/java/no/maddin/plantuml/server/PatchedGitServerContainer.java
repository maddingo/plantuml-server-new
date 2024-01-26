package no.maddin.plantuml.server;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.sparsick.testcontainers.gitserver.plain.GitServerContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

public class PatchedGitServerContainer extends GitServerContainer {
    /**
     * @param dockerImageName - name of the docker image
     */
    public PatchedGitServerContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        super.containerIsStarted(containerInfo);
        try {
            execInContainer("chmod", "600", "/home/git/.ssh/authorized_keys");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Could not fix file permissions on /home/git/.ssh/authorized_keys", e);
        }
    }
}
