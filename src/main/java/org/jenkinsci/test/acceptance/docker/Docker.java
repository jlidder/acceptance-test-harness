package org.jenkinsci.test.acceptance.docker;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.test.acceptance.utils.SHA1Sum;
import org.jenkinsci.utils.process.CommandBuilder;
import org.jvnet.hudson.annotation_indexer.Index;

import com.google.inject.Inject;


/**
 * Entry point to the docker support.
 *
 * Use this subsystem by injecting this class into your test.
 *
 * @author Kohsuke Kawaguchi
 */
@Singleton
public class Docker {

    /**
     * Command to invoke docker.
     */
    @Inject(optional=true)
    @Named("docker")
    private static String stringDockerCmd="docker";

    private static List<String> dockerCmd;// = Arrays.asList("docker");

    /**
     * Injecting a portOffset will force the binding of dockerPorts to local Ports with an offset
     * (e.g. bind docker 22 to localhost port 40022,
     */
    @Inject(optional=true)
    @Named("dockerPortOffset")
    private static int portOffset= 0;

    public int getPortOffset() {
        return portOffset;
    }

    @Inject(optional=true)
    public ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    public Docker() {
        dockerCmd = Arrays.asList(stringDockerCmd);
    }

    public static CommandBuilder cmd(String cmd) {
        return new CommandBuilder(dockerCmd).add(cmd);
    }

    /**
     * Checks if docker is available on this system.
     */
    public boolean isAvailable() {
        try {
            return cmd("help").popen().waitFor()==0;
        } catch (InterruptedException|IOException e) {
            return false;
        }
    }

    /**
     * Builds a docker image.
     *
     * @param tag
     *      Name of the image to be built.
     * @param dir
     *      Directory that contains Dockerfile
     */
    public DockerImage build(String tag, File dir) throws IOException, InterruptedException {
        // check if the image already exists
        if (cmd("images").add("-q",tag).popen().verifyOrDieWith("failed to query the status of the image").trim().length()>0)
            return new DockerImage(tag);

        if (cmd("build").add("-t", tag, dir).system()!=0)
            throw new Error("Failed to build image: "+tag);
        return new DockerImage(tag);
    }

    public DockerImage build(Class<? extends DockerContainer> fixture) throws IOException, InterruptedException {
        if (fixture.getSuperclass()!=DockerContainer.class)
            build((Class)fixture.getSuperclass()); // build the base image first

        try {
            DockerFixture f = fixture.getAnnotation(DockerFixture.class);
            if (f==null)
                throw new AssertionError(fixture+" is missing @DockerFixture");

            File dir = File.createTempFile("Dockerfile", "dir");
            dir.delete();
            dir.mkdirs();

            try {
                URL resourceDir = classLoader.getResource(fixture.getName().replace('.', '/'));
                File dockerFileDir;
                try {
                    dockerFileDir = new File(resourceDir.toURI());
                } catch(URISyntaxException e) {
                    dockerFileDir = new File(resourceDir.getPath());
                }
                FileUtils.copyDirectory(dockerFileDir, dir);

                String dockerFileHash = getDockerFileHash(dockerFileDir);
                String shortedDockerFileHash = dockerFileHash.substring(0,12);

                return build("jenkins/" + f.id() + "_" +  shortedDockerFileHash, dir);
            } finally {
                FileUtils.deleteDirectory(dir);
            }
        } catch (InterruptedException|IOException e) {
            throw new IOException("Failed to build image: "+fixture,e);
        }
    }

    private String getDockerFileHash(File dockerFileDir) {
        File dockerFile = new File (dockerFileDir.getAbsolutePath()+"/Dockerfile");
        SHA1Sum dockerFileHash = new SHA1Sum(dockerFile);
        return dockerFileHash.getSha1String();
    }

    /**
     * Starts a container of the specific fixture type.
     * This builds an image if need be.
     */
    public <T extends DockerContainer> T start(Class<T> fixture, CommandBuilder options, CommandBuilder cmd) {
        try {
            return build(fixture).start(fixture, options, cmd,portOffset);
        } catch (InterruptedException|IOException e) {
            throw new AssertionError("Failed to start container "+fixture, e);
        }
    }

    public <T extends DockerContainer> T start(Class<T> fixture) {
        return start(fixture,null,null);
    }

    /**
     * Finds a fixture class that has the specified ID.
     *
     * @see org.jenkinsci.test.acceptance.docker.DockerFixture#id()
     */
    public Class<? extends DockerContainer> findFixture(String id) throws IOException {
        for (Class<?> t : Index.list(DockerFixture.class, classLoader, Class.class)) {
            if (t.getAnnotation(DockerFixture.class).id().equals(id))
                return t.asSubclass(DockerContainer.class);
        }
        throw new IllegalArgumentException("No such docker fixture found: "+id);
    }


}
