package org.jenkinsci.plugins.jvctb;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.ProxyConfiguration;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.jvctb.config.ViolationsToBitbucketServerConfig;
import org.kohsuke.stapler.DataBoundConstructor;
import se.bjurr.violations.comments.bitbucketserver.lib.ViolationCommentsToBitbucketServerApi;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;

import static hudson.tasks.BuildStepMonitor.NONE;
import static org.jenkinsci.plugins.jvctb.perform.JvctbPerformer.jvctsPerform;
import static se.bjurr.violations.comments.bitbucketserver.lib.ViolationCommentsToBitbucketServerApi.violationCommentsToBitbucketServerApi;

public class ViolationsToBitbucketServerRecorder extends Recorder implements SimpleBuildStep {

  public static final BuildStepDescriptor<Publisher> DESCRIPTOR =
      new ViolationsToBitbucketServerDescriptor();

  private ViolationsToBitbucketServerConfig config;

  public ViolationsToBitbucketServerRecorder() {}

  @DataBoundConstructor
  public ViolationsToBitbucketServerRecorder(ViolationsToBitbucketServerConfig config) {
    this.config = config;
  }

  public ViolationsToBitbucketServerConfig getConfig() {
    return config;
  }

  @Override
  public BuildStepDescriptor<Publisher> getDescriptor() {
    return DESCRIPTOR;
  }

  @Override
  public BuildStepMonitor getRequiredMonitorService() {
    return NONE;
  }

  @Override
  public void perform(
      @NonNull Run<?, ?> build,
      @NonNull FilePath filePath,
      @NonNull Launcher launcher,
      @NonNull TaskListener listener)
      throws InterruptedException, IOException {

    ViolationsToBitbucketServerConfig combinedConfig =
        new ViolationsToBitbucketServerConfig(this.config);
    ViolationsToBitbucketServerGlobalConfiguration defaults =
        ViolationsToBitbucketServerGlobalConfiguration.get()
            .or(new ViolationsToBitbucketServerGlobalConfiguration());

    combinedConfig.applyDefaults(defaults);
    ViolationCommentsToBitbucketServerApi violationCommentsToBitbucketServerApi =
        violationCommentsToBitbucketServerApi();
    ProxyConfigDetails proxyConfigDetails =
        createProxyConfigDetails(listener.getLogger(), config.getBitbucketServerUrl());
    jvctsPerform(proxyConfigDetails, combinedConfig, filePath, build, listener);
  }

  /** The Jenkins.getInstance() will return null if not run on master! */
  private ProxyConfigDetails createProxyConfigDetails(
      final PrintStream logger, final String urlString) throws MalformedURLException {
    final Jenkins jenkins = Jenkins.getInstance();
    if (jenkins == null) {
      logger.println("Not using proxy, no Jenkins instance.");
      return null;
    }

    final ProxyConfiguration proxyConfig = jenkins.proxy;
    if (proxyConfig == null) {
      logger.println("Not using proxy, no proxy configured.");
      return null;
    }

    final Proxy proxy = proxyConfig.createProxy(new URL(urlString).getHost());
    if (proxy == null || proxy.type() != Proxy.Type.HTTP) {
      logger.println("Not using proxy, not HTTP.");
      return null;
    }

    final SocketAddress addr = proxy.address();
    if (addr == null || !(addr instanceof InetSocketAddress)) {
      logger.println("Not using proxy, addr not InetSocketAddress.");
      return null;
    }

    final InetSocketAddress proxyAddr = (InetSocketAddress) addr;
    final String proxyHost = proxyAddr.getAddress().getHostAddress();
    final int proxyPort = proxyAddr.getPort();
    ProxyConfigDetails proxyConfigDetails = new ProxyConfigDetails(proxyHost, proxyPort);

    final String proxyUser = proxyConfig.getUserName();
    if (proxyUser != null) {
      final String proxyPass = proxyConfig.getPassword();
      proxyConfigDetails.setUser(proxyUser);
      proxyConfigDetails.setPass(proxyPass);
    }
    return proxyConfigDetails;
  }

  public void setConfig(ViolationsToBitbucketServerConfig config) {
    this.config = config;
  }
}
