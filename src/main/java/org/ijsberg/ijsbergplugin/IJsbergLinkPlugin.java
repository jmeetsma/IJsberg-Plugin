package org.ijsberg.ijsbergplugin;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.ijsberg.iglu.util.io.FileSupport;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link IJsbergLinkPlugin} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #customerId})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class IJsbergLinkPlugin extends Builder {

    private final String customerId;
	private final String projectId;
	private final String monitorUploadDirectory;

	public static final String SNAPSHOT_TIMESTAMP_FORMAT = "yyyyMMdd_HH_mm";

	// Fields in config.jelly must match the parameter names in the "DataBoundConstructor"

	@DataBoundConstructor
    public IJsbergLinkPlugin(String customerId, String projectId, String monitorUploadDirectory) {
        this.customerId = customerId;
		this.projectId = projectId;
		this.monitorUploadDirectory = monitorUploadDirectory;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
	public String getCustomerId() {
		return customerId;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getMonitorUploadDirectory() {
		return monitorUploadDirectory;
	}

	@Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        // This is where you 'build' the project.
        // This also shows how you can consult the global configuration of the builder

		String uploadDir = monitorUploadDirectory;

        if (getDescriptor().getAlternativeUploadDirectory() != null && !"".equals(getDescriptor().getAlternativeUploadDirectory())) {
			uploadDir = getDescriptor().getAlternativeUploadDirectory();
		}
		listener.getLogger().println("got request to analyze project " + projectId + " for " + customerId);
		File uploadDirectory = new File(uploadDir);
		if(!uploadDirectory.exists()) {
			listener.getLogger().println("ERROR: upload directory " + uploadDirectory.getAbsolutePath() + " does not exist or is not accessible");
			return false;
		}
		if(!uploadDirectory.isDirectory()) {
			listener.getLogger().println("ERROR: " + uploadDirectory.getAbsolutePath() + " is not a directory");
			return false;
		}
		FilePath path = build.getWorkspace();
		listener.getLogger().println("zipping sources to " + uploadDirectory.getAbsolutePath());

		String destfileName = uploadDir + "/" + getSnapshotZipfileName(customerId, projectId, new Date()) + ".zip";
		File file = new File(destfileName);
		try {
			FileOutputStream tmpFileOutput = new FileOutputStream(file);
			path.zip(tmpFileOutput);
			FileSupport.createFile(destfileName + ".DONE");
		} catch (IOException e) {
			listener.getLogger().println("exception encountered during zip process with message: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
		catch (InterruptedException e) {
			listener.getLogger().println("exception encountered during zip process with message: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
		listener.getLogger().println("DONE ... created snapshot " + destfileName);
        return true;
    }


	public static String getSnapshotZipfileName(String customerId, String projectId, Date snapshotTimestamp) {
		return customerId + "." +
				projectId + "." + new SimpleDateFormat(SNAPSHOT_TIMESTAMP_FORMAT).format(snapshotTimestamp) + ".zip";
	}


	// Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link IJsbergLinkPlugin}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private String alterantiveUploadDirectory;

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        public FormValidation doCheckAlternativeUploadDirectory(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please provide upload directory");
			File file = new File(value);
			if(!file.exists()) {
				return FormValidation.error("Upload directory " + value + " does not exist");
			}
			if(!file.isDirectory()) {
				return FormValidation.error(value + " is not a directory");
			}
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Links to IJsberg static source code analysis";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            alterantiveUploadDirectory = formData.getString("alternativeUploadDirectory");
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req,formData);
        }

        /**
         */
        public String getAlternativeUploadDirectory() {
			return alterantiveUploadDirectory;
        }
    }


	public static void main(String[] args) throws Exception {
		FilePath path = new FilePath(new File("C:/util/keys"));


		String tempfileName = "C:/tmp//tmp_" + System.nanoTime() + ".zip";
		File tmpFile = new File(tempfileName);

		path.zip(new FilePath(tmpFile));

	}
}

