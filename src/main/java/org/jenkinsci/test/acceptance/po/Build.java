package org.jenkinsci.test.acceptance.po;

import com.fasterxml.jackson.databind.JsonNode;

import org.apache.commons.io.IOUtils;
import org.hamcrest.Description;
import org.jenkinsci.test.acceptance.Matcher;
import org.jenkinsci.test.acceptance.Matchers;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class Build extends ContainerPageObject {
    public final Job job;

    private String result;

    /**
     * Console output. Cached.
     */
    private String console;
    private boolean success;

    public Build(Job job, int buildNumber) {
        super(job.injector,job.url("%d/",buildNumber));
        this.job = job;
    }

    public Build(Job job, String permalink) {
        super(job.injector,job.url(permalink+"/"));
        this.job = job;
    }

    public Build(Job job, URL url) {
        super(job.injector, url);
        this.job = job;
    }

    /**
     * "Casts" this object into a subtype by creating the specified type
     */
    public <T extends Build> T as(Class<T> type) {
        if (type.isInstance(this))
            return type.cast(this);
        return newInstance(type, job, url);
    }

    public Build waitUntilStarted() {
        waitForCond(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return hasStarted();
            }
        });
        return this;
    }

    public boolean hasStarted() {
        if (result!=null)
            return true;

        try {
            getJson();
            // we have json. Build has started.
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Build waitUntilFinished() {
        return waitUntilFinished(120);
    }

    public Build waitUntilFinished(int timeout) {
        waitUntilStarted();

        // while waiting, hit the console page, so that during the interactive development
        // one can see what the build is doing
        visit("console");

        waitForCond(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return !isInProgress();
            }
        },timeout);
        return this;
    }

    public boolean isInProgress() {
        if (result!=null)   return false;
        if (!hasStarted())  return false;

        JsonNode d = getJson();
        return d.get("building").booleanValue() || d.get("result")==null;
    }

    public int getNumber() {
        return getJson().get("number").asInt();
    }

    public URL getConsoleUrl() {
        return url("consoleFull");
    }

    public String getConsole() {
        if (console!=null)  return console;

        visit(getConsoleUrl());

        List<WebElement> a = all(by.xpath("//pre"));
        if (a.size()>1)
            console = find(by.xpath("//pre[@id='out']")).getText();
        else
            console = a.get(0).getText();

        return console;
    }

    public Build shouldContainsConsoleOutput(String fragment) {
        assertThat(this.getConsole(), Matchers.containsRegexp(fragment, Pattern.MULTILINE));
        return this;
    }

    public Build shouldNotContainsConsoleOutput(String fragment) {
        assertThat(this.getConsole(), not(Matchers.containsRegexp(fragment, Pattern.MULTILINE)));
        return this;
    }

    public boolean isSuccess() {
        return getResult().equals("SUCCESS");
    }

    public String getResult() {
        if (result!=null)   return result;

        waitUntilFinished();
        result = getJson().get("result").asText();
        return result;
    }

    public Artifact getArtifact(String artifact) {
        return new Artifact(this,url("artifact/%s",artifact));
    }

    public Build shouldSucceed() {
        assertThat(this, resultIs("SUCCESS"));
        return this;
    }

    public Build shouldFail() {
        assertThat(this, resultIs("FAILURE"));
        return this;
    }

    public Build shouldAbort() {
        assertThat(this, resultIs("ABORTED"));
        return this;
    }

    private Matcher<Build> resultIs(final String expected) {
        return new Matcher<Build>("Build result %s", expected) {
            @Override public boolean matchesSafely(Build item) {
                return item.getResult().equals(expected);
            }

            @Override public void describeMismatchSafely(Build item, Description dsc) {
                dsc.appendText("was ").appendText(item.getResult())
                        .appendText(". Console output:\n").appendText(getConsole())
                ;
            }
        };
    }

    public String getNode() {
        String n = getJson().get("builtOn").asText();
        if (n.length()==0)  return "master";
        return n;
    }

    /**
     * Does this object exist?
     */
    public void shouldExist() {
        try {
            IOUtils.toByteArray(url.openStream());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public void shouldNotExist() {
        try {
            HttpURLConnection con = (HttpURLConnection)url.openConnection();
            assertThat(con.getResponseCode(), is(404));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
