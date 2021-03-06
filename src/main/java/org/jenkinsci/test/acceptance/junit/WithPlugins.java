package org.jenkinsci.test.acceptance.junit;

import com.google.inject.Inject;
import org.jenkinsci.test.acceptance.controller.JenkinsController;
import org.jenkinsci.test.acceptance.po.Jenkins;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * Indicates that a test requires the presence of the specified plugins.
 *
 * <p>
 * When running tests, this annotation triggers {@link JenkinsAcceptanceTestRule}
 * to install all the plugins.
 *
 * We also want to use this to filter tests, especially for non-destructive tests.
 *
 * TODO: add a version constraint
 *
 * @author Kohsuke Kawaguchi
 */
@Retention(RUNTIME)
@Target({METHOD, TYPE})
@Inherited @Documented
@RuleAnnotation(WithPlugins.RuleImpl.class)
public @interface WithPlugins {
    String[] value();

    public class RuleImpl implements TestRule {
        @Inject
        Jenkins jenkins;

        @Inject
        JenkinsController controller;

        @Override
        public Statement apply(final Statement base, final Description d) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    installPlugins(d.getAnnotation(WithPlugins.class));
                    installPlugins(d.getTestClass().getAnnotation(WithPlugins.class));

                    base.evaluate();
                }

                private boolean installPlugins(WithPlugins wp) {
                    if (wp!=null)
                        jenkins.getPluginManager().installPlugin(wp.value());
                    return wp!=null;
                }
            };
        }
    }
}
