package org.jenkinsci.test.acceptance.po;

import com.fasterxml.jackson.databind.JsonNode;
import org.jenkinsci.test.acceptance.Matchers;
import org.openqa.selenium.WebElement;

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

    public URL getConsoleUrl() {
        return url("console");
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

    private String getResult() {
        if (result!=null)   return result;

        waitUntilFinished();
        result = getJson().get("result").asText();
        return result;
    }

    public Artifact getArtifact(String artifact) {
        return new Artifact(this,url("artifact/%s",artifact));
    }

    public Build shouldSucceed() {
        String r = getResult();
        if (!r.equals("SUCCESS"))
            fail("Expected successful build but it was "+r+". Console output: " + getConsole());
        return this;
    }

    public Build shouldFail() {
        assertThat(getResult(), is("FAILURE"));
        return this;
    }

    public Build shouldAbort() {
        assertThat(getResult(), is("ABORTED"));
        return this;
    }

    public String getNode() {
        String n = getJson().get("builtOn").asText();
        if (n.length()==0)  return "master";
        return n;
    }
}
