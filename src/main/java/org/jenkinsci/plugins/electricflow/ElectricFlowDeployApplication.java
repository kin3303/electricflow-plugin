
// ElectricFlowDeployApplication.java --
//
// ElectricFlowDeployApplication.java is part of ElectricCommander.
//
// Copyright (c) 2005-2017 Electric Cloud, Inc.
// All rights reserved.
//

package org.jenkinsci.plugins.electricflow;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jenkinsci.plugins.electricflow.Utils.addParametersToJson;
import static org.jenkinsci.plugins.electricflow.Utils.formatJsonOutput;

public class ElectricFlowDeployApplication
    extends Recorder
    implements SimpleBuildStep
{

    //~ Static fields/initializers ---------------------------------------------

    private static final Log log = LogFactory.getLog(
            ElectricFlowStartRelease.class);

    //~ Instance fields --------------------------------------------------------

    private String configuration;
    private String projectName;
    private String applicationName;
    private String applicationProcessName;
    private String environmentName;
    private String deployParameters;

    //~ Constructors -----------------------------------------------------------

    @DataBoundConstructor public ElectricFlowDeployApplication() { }

    //~ Methods ----------------------------------------------------------------

    @Override public void perform(
            @Nonnull Run<?, ?>    run,
            @Nonnull FilePath     filePath,
            @Nonnull Launcher     launcher,
            @Nonnull TaskListener taskListener)
        throws InterruptedException, IOException
    {
        boolean isSuccess = runProcess(run, taskListener);
        if (!isSuccess) {
            run.setResult(Result.FAILURE);
        }
    }

    private boolean runProcess(
            @Nonnull Run<?, ?>    run,
            @Nonnull TaskListener taskListener)
    {
        ElectricFlowClient efClient = new ElectricFlowClient(configuration);
        PrintStream        logger   = taskListener.getLogger();

        logger.println("Project name: "
                + projectName
                + ", Application name: " + applicationName
                + ", Application process name: " + applicationProcessName
                + ", Environment name: " + environmentName);

        JSONObject runProcess = JSONObject.fromObject(deployParameters)
                                          .getJSONObject("runProcess");
        JSONArray  parameter  = JSONArray.fromObject(runProcess.getString(
                    "parameter"));

        try {
            logger.println("Preparing to run process...");

            String     result  = efClient.runProcess(projectName,
                    applicationName, applicationProcessName, environmentName,
                    parameter);
            JSONObject process = efClient.getProcess(projectName,
                    applicationName, applicationProcessName);

            if (process == null || process.isEmpty()) {
                return false;
            }

            String              processId = process.getJSONObject("process")
                                                   .getString("processId");
            Map<String, String> args      = new HashMap<>();

            args.put("applicationName", applicationName);
            args.put("processName", applicationProcessName);
            args.put("processId", processId);
            args.put("result", result);

            String            summaryHtml = getSummaryHtml(efClient, parameter,
                    args);
            SummaryTextAction action      = new SummaryTextAction(run,
                    summaryHtml);

            run.addAction(action);
            run.save();
            logger.println("Deploy application result: "
                    + formatJsonOutput(result));
        }
        catch (Exception e) {
            logger.println(e.getMessage());
            log.error(e.getMessage(), e);

            return false;
        }

        return true;
    }

    public String getApplicationName()
    {
        return applicationName;
    }

    public String getApplicationProcessName()
    {
        return applicationProcessName;
    }

    public String getConfiguration()
    {
        return configuration;
    }

    public String getDeployParameters()
    {
        return deployParameters;
    }

    public String getEnvironmentName()
    {
        return environmentName;
    }

    public String getProjectName()
    {
        return projectName;
    }

    @Override public BuildStepMonitor getRequiredMonitorService()
    {
        return BuildStepMonitor.NONE;
    }

    private String getSummaryHtml(
            ElectricFlowClient  configuration,
            JSONArray           parameters,
            Map<String, String> args)
    {
        String result          = args.get("result");
        String applicationName = args.get("applicationName");
        String processId       = args.get("processId");
        String jobId           = JSONObject.fromObject(result)
                                           .getString("jobId");
        String applicationUrl  = configuration.getElectricFlowUrl()
                + "/flow/#applications/applications";
        String deployRunUrl    = configuration.getElectricFlowUrl()
                + "/flow/#applications/" + processId + "/" + jobId
                + "/runningProcess";
        String summaryText     = "<h3>ElectricFlow Deploy Application</h3>"
                + "<table cellspacing=\"2\" cellpadding=\"4\"> \n"
                + "  <tr>\n"
                + "    <td>Application Name:</td>\n"
                + "    <td><a href='" + applicationUrl + "'>" + applicationName
                + "</a></td>   \n"
                + "  </tr>\n"
                + "  <tr>\n"
                + "    <td>Deploy run URL:</td>\n"
                + "    <td><a href='" + deployRunUrl + "'>" + deployRunUrl
                + "</a></td>   \n"
                + "  </tr>";

        summaryText = Utils.getParametersHTML(parameters, summaryText,
                "actualParameterName", "value");
        summaryText = summaryText + "</table>";

        return summaryText;
    }

    @DataBoundSetter public void setApplicationName(String applicationName)
    {
        this.applicationName = applicationName;
    }

    @DataBoundSetter public void setApplicationProcessName(
            String applicationProcessName)
    {
        this.applicationProcessName = applicationProcessName;
    }

    @DataBoundSetter public void setConfiguration(String configuration)
    {
        this.configuration = configuration;
    }

    @DataBoundSetter public void setDeployParameters(String deployParameters)
    {
        this.deployParameters = deployParameters;
    }

    @DataBoundSetter public void setEnvironmentName(String environmentName)
    {
        this.environmentName = environmentName;
    }

    @DataBoundSetter public void setProjectName(String projectName)
    {
        this.projectName = projectName;
    }

    //~ Inner Classes ----------------------------------------------------------

    @Extension public static final class DescriptorImpl
        extends BuildStepDescriptor<Publisher>
    {

        //~ Instance fields ----------------------------------------------------

        //~ Constructors -------------------------------------------------------

        public DescriptorImpl()
        {
            load();
        }

        //~ Methods ------------------------------------------------------------

        public FormValidation doCheckConfiguration(
                @QueryParameter String value)
        {
            return Utils.validateConfiguration(value);
        }

        public FormValidation doCheckProjectName(@QueryParameter String value)
        {
            return Utils.validateValueOnEmpty(value, "Project name");
        }

        public ListBoxModel doFillApplicationNameItems(
                @QueryParameter String projectName,
                @QueryParameter String configuration)
            throws IOException
        {
            ListBoxModel m = new ListBoxModel();

            m.add("Select application", "");

            if (!configuration.isEmpty() && !projectName.isEmpty()) {
                ElectricFlowClient client = new ElectricFlowClient(configuration);

                List<String> applications = client.getApplications(projectName);

                for (String application : applications) {
                    m.add(application);
                }
            }

            return m;
        }

        public ListBoxModel doFillApplicationProcessNameItems(
                @QueryParameter String configuration,
                @QueryParameter String projectName,
                @QueryParameter String applicationName)
            throws IOException
        {
            ListBoxModel m = new ListBoxModel();

            m.add("Select Application process name", "");

            if (!configuration.isEmpty() && !projectName.isEmpty() && !applicationName.isEmpty()) {
                ElectricFlowClient client = new ElectricFlowClient(configuration);
                List<String> processes = client.getProcesses(projectName,
                        applicationName);

                for (String process : processes) {
                    m.add(process);
                }
            }

            return m;
        }

        public ListBoxModel doFillConfigurationItems()
        {
            return Utils.fillConfigurationItems();
        }

        public ListBoxModel doFillDeployParametersItems(
                @QueryParameter String configuration,
                @QueryParameter String projectName,
                @QueryParameter String applicationName,
                @QueryParameter String applicationProcessName,
                @QueryParameter String deployParameters)
            throws IOException
        {
            ListBoxModel m = new ListBoxModel();

            if (configuration.isEmpty() || projectName.isEmpty()
                    || applicationName.isEmpty()
                    || applicationProcessName.isEmpty()) {
                m.add("{}");

                return m;
            }

            ElectricFlowClient client = new ElectricFlowClient(configuration);

            // During reload if at least one value filled, return old values
            if (!deployParameters.isEmpty() && !"{}".equals(deployParameters)) {
                JSONObject json      = JSONObject.fromObject(deployParameters);
                JSONObject jsonArray = json.getJSONObject("runProcess");

                if (applicationName.equals(jsonArray.get("applicationName"))
                        && applicationProcessName.equals(
                            jsonArray.get("applicationProcessName"))) {
                    m.add(deployParameters);

                    return m;
                }
            }

            List<String> parameters = client.getFormalParameters(projectName,
                    applicationName, applicationProcessName);
            JSONObject   main       = JSONObject.fromObject(
                    "{'runProcess':{'applicationName':'" + applicationName
                        + "', 'applicationProcessName':'"
                        + applicationProcessName
                        + "',   'parameter':[]}}");
            JSONArray    ja         = main.getJSONObject("runProcess")
                                          .getJSONArray("parameter");

            addParametersToJson(parameters, ja, "actualParameterName", "value");
            m.add(main.toString());

            if (m.isEmpty()) {
                m.add("{}");
            }

            return m;
        }

        public ListBoxModel doFillEnvironmentNameItems(
                @QueryParameter String configuration,
                @QueryParameter String projectName)
            throws IOException
        {
            ListBoxModel m = new ListBoxModel();

            m.add("Select Environment name", "");

            if (!configuration.isEmpty() && !projectName.isEmpty()) {
                ElectricFlowClient client = new ElectricFlowClient(configuration);
                List<String> environments = client.getEnvironments(projectName);

                for (String environment : environments) {
                    m.add(environment);
                }
            }

            return m;
        }

        public ListBoxModel doFillProjectNameItems(
                @QueryParameter String configuration)
            throws IOException
        {
            return Utils.getProjects(configuration);
        }

        @Override public String getDisplayName()
        {
            return "ElectricFlow - Deploy Application";
        }

        @Override public String getId()
        {
            return "electricFlowDeployApplication";
        }

        @Override public boolean isApplicable(
                Class<? extends AbstractProject> aClass)
        {
            return true;
        }
    }
}
