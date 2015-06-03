/**
 * Copyright (C) 2009-2015 Bonitasoft S.A.
 * Bonitasoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.0 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.bonitasoft.studio.engine;

import static org.bonitasoft.studio.engine.server.ClientBonitaHomeBuildler.newClientBonitaHomeBuilder;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Date;

import org.bonitasoft.engine.session.APISession;
import org.bonitasoft.studio.common.BonitaHomeUtil;
import org.bonitasoft.studio.common.FileUtil;
import org.bonitasoft.studio.common.ProjectUtil;
import org.bonitasoft.studio.common.extension.BonitaStudioExtensionRegistryManager;
import org.bonitasoft.studio.common.log.BonitaStudioLog;
import org.bonitasoft.studio.common.platform.tools.PlatformUtil;
import org.bonitasoft.studio.common.repository.Repository;
import org.bonitasoft.studio.common.repository.RepositoryAccessor;
import org.bonitasoft.studio.designer.UIDesignerPlugin;
import org.bonitasoft.studio.designer.core.WorkspaceResourceServerManager;
import org.bonitasoft.studio.engine.i18n.Messages;
import org.bonitasoft.studio.engine.preferences.EnginePreferenceConstants;
import org.bonitasoft.studio.engine.server.PortConfigurator;
import org.bonitasoft.studio.preferences.BonitaPreferenceConstants;
import org.bonitasoft.studio.preferences.BonitaStudioPreferencesPlugin;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jst.server.tomcat.core.internal.ITomcatServer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.wst.server.core.IRuntime;
import org.eclipse.wst.server.core.IRuntimeType;
import org.eclipse.wst.server.core.IRuntimeWorkingCopy;
import org.eclipse.wst.server.core.IServer;
import org.eclipse.wst.server.core.IServerType;
import org.eclipse.wst.server.core.IServerWorkingCopy;
import org.eclipse.wst.server.core.ServerCore;
import org.eclipse.wst.server.core.internal.ProjectProperties;
import org.eclipse.wst.server.core.util.SocketUtil;

/**
 * Provides all methods to manage Tomcat server in BonitaStudio.
 *
 * @author Romain Bioteau
 * @author Celine Souchet
 */
public class BOSWebServerManager {

    private static final String BONITA_TOMCAT_SERVER_ID = "bonita-tomcat-server-id";
    private static final String BONITA_TOMCAT_RUNTIME_ID = "bonita-tomcat-runtime-id";
    public static final String SERVER_CONFIGURATION_PROJECT = "server_configuration";
    private static final String LOGINSERVICE_PATH = "/bonita/loginservice?";
    protected static final String WEBSERVERMANAGER_EXTENSION_D = "org.bonitasoft.studio.engine.bonitaWebServerManager";
    protected static final String TOMCAT_SERVER_TYPE = "org.eclipse.jst.server.tomcat.70";
    protected static final String TOMCAT_RUNTIME_TYPE = "org.eclipse.jst.server.tomcat.runtime.70";
    protected static final String START_TIMEOUT = "start-timeout";

    protected static final String TMP_DIR = ProjectUtil.getBonitaStudioWorkFolder().getAbsolutePath();
    protected final String tomcatInstanceLocation = new File(ResourcesPlugin
            .getWorkspace().getRoot().getLocation().toFile(), "tomcat")
            .getAbsolutePath();
    private static final String TOMCAT_LOG_FILE = "tomcat.log";

    public static int WATCHDOG_PORT = 6969;
    private static final int MAX_SERVER_START_TIME = 300000;
    private static final int MAX_LOGGING_TRY = 50;

    private ServerSocket watchdogServer;
    private static BOSWebServerManager INSTANCE;
    private IServer tomcat;
    private PortConfigurator portConfigurator;

    public synchronized static BOSWebServerManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = createInstance();
        }
        return INSTANCE;
    }

    protected static BOSWebServerManager createInstance() {
        for (final IConfigurationElement element : BonitaStudioExtensionRegistryManager.getInstance().getConfigurationElements(WEBSERVERMANAGER_EXTENSION_D)) {
            try {
                return (BOSWebServerManager) element.createExecutableExtension("class");
            } catch (final CoreException e) {
                BonitaStudioLog.error(e, EnginePlugin.PLUGIN_ID);
            }
        }

        return new BOSWebServerManager();
    }

    protected BOSWebServerManager() {

    }

    public void copyTomcatBundleInWorkspace(final IProgressMonitor monitor) {
        File tomcatFolder = null;
        try {
            final File targetFolder = new File(tomcatInstanceLocation);
            final File tomcatLib = new File(targetFolder, "lib");
            if (!tomcatLib.exists()) {
                BonitaStudioLog.debug("Copying tomcat bundle in workspace...", EnginePlugin.PLUGIN_ID);
                final URL url = ProjectUtil.getConsoleLibsBundle().getResource("tomcat");
                tomcatFolder = new File(FileLocator.toFileURL(url).getFile());
                PlatformUtil.copyResource(targetFolder, tomcatFolder, monitor);
                BonitaStudioLog.debug("Tomcat bundle copied in workspace.",
                        EnginePlugin.PLUGIN_ID);
                addPageBuilderWar(targetFolder, monitor);
            }
        } catch (final IOException e) {
            BonitaStudioLog.error(e, EnginePlugin.PLUGIN_ID);

        }

    }

    protected void addPageBuilderWar(final File targetFolder, final IProgressMonitor monitor) throws IOException {
        BonitaStudioLog.debug("Copying Designer war in tomcat/webapps...", EnginePlugin.PLUGIN_ID);
        final URL url = Platform.getBundle(UIDesignerPlugin.PLUGIN_ID).getResource("webapp");
        final File pageBuilderWarFile = new File(FileLocator.toFileURL(url).getFile(), "designer.war");
        PlatformUtil.copyResource(new File(targetFolder, "webapps"), pageBuilderWarFile, monitor);
        BonitaStudioLog.debug("Designer war copied in tomcat/webapps.",
                EnginePlugin.PLUGIN_ID);
    }

    /**
     * Start the Server.
     *
     * @param monitor
     */
    public synchronized void startServer(final IProgressMonitor monitor) {
        if (!serverIsStarted()) {
            BonitaHomeUtil.initBonitaHome();
            copyTomcatBundleInWorkspace(monitor);
            monitor.subTask(Messages.startingWebServer);
            if (BonitaStudioLog.isLoggable(IStatus.OK)) {
                BonitaStudioLog.debug("Starting tomcat...",
                        EnginePlugin.PLUGIN_ID);
            }
            startWatchdog();
            try {
                WorkspaceResourceServerManager.getInstance().start(org.eclipse.jdt.launching.SocketUtil.findFreePort());
            } catch (final Exception e1) {
                BonitaStudioLog.error(e1);
            }
            if (tomcat != null) {
                try {
                    tomcat.delete();
                } catch (final CoreException e) {
                    BonitaStudioLog.error(e, EnginePlugin.PLUGIN_ID);
                }
            }
            updateRuntimeLocationIfNeeded();
            final IRuntimeType type = ServerCore.findRuntimeType(TOMCAT_RUNTIME_TYPE);

            try {
                final IProject confProject = createServerConfigurationProject(monitor);
                final IRuntime runtime = createServerRuntime(type, monitor);
                tomcat = createServer(monitor, confProject, runtime);
                createLaunchConfiguration(tomcat, monitor);
                confProject.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
                tomcat.start("run", monitor);
                waitServerRunning(monitor);
            } catch (final CoreException e) {
                handleCoreExceptionWhileStartingTomcat(e);
            }

        }
    }

    private void handleCoreExceptionWhileStartingTomcat(final CoreException e) {
        BonitaStudioLog.error(e, EnginePlugin.PLUGIN_ID);
        Display.getDefault().syncExec(new Runnable() {

            @Override
            public void run() {
                MessageDialog.openWarning(Display.getDefault().getActiveShell(), "Tomcat server startup error", e.getMessage());
            }
        });
    }

    private void updateRuntimeLocationIfNeeded() {
        for (final IRuntime runtime : ServerCore.getRuntimes()) {
            if (runtime instanceof org.eclipse.wst.server.core.internal.Runtime && runtime.getLocation() != null
                    && !runtime.getLocation().toFile().getAbsolutePath().equals(tomcatInstanceLocation)) {
                final IRuntimeWorkingCopy copy = runtime.createWorkingCopy();
                final String oldLocaiton = copy.getLocation().toFile()
                        .getAbsolutePath();
                copy.setLocation(Path.fromOSString(tomcatInstanceLocation));
                final File serverXmlFile = new File(tomcatInstanceLocation, "conf" + File.separatorChar + "server.xml");
                // for Windows, we need to escape \
                FileUtil.replaceStringInFile(serverXmlFile, oldLocaiton,
                        tomcatInstanceLocation.replaceAll("\\\\", "\\\\\\\\"));
                try {
                    copy.save(true, Repository.NULL_PROGRESS_MONITOR);
                } catch (final CoreException e) {
                    BonitaStudioLog.error(e, EnginePlugin.PLUGIN_ID);
                }
            }
        }
    }

    private void waitServerRunning(final IProgressMonitor monitor) {
        int totalTime = 0;
        while (totalTime < MAX_SERVER_START_TIME && tomcat != null
                && tomcat.getServerState() != IServer.STATE_STARTED) {
            try {
                Thread.sleep(1000);
                totalTime = totalTime + 1000;
            } catch (final InterruptedException e) {
                BonitaStudioLog.error(e, EnginePlugin.PLUGIN_ID);
            }
        }
        if (BonitaStudioLog.isLoggable(IStatus.OK)) {
            if (tomcat.getServerState() == IServer.STATE_STARTED) {
                BonitaStudioLog.debug("Tomcat server started.",
                        EnginePlugin.PLUGIN_ID);
            } else {
                BonitaStudioLog.debug("Tomcat failed to start.",
                        EnginePlugin.PLUGIN_ID);
                Display.getDefault().syncExec(new Runnable() {

                    @Override
                    public void run() {
                        MessageDialog.openInformation(PlatformUI.getWorkbench()
                                .getActiveWorkbenchWindow().getShell(),
                                Messages.cannotStartTomcatTitle,
                                Messages.cannotStartTomcatMessage);
                    }
                });
                return;
            }
        }

        connectWithRetries();
    }

    private void connectWithRetries() {
        int loginTry = 0;
        APISession session = null;
        while (session == null && loginTry < MAX_LOGGING_TRY) {
            try {
                session = BOSEngineManager.getInstance().getLoginAPI().login(BOSEngineManager.BONITA_TECHNICAL_USER, BOSEngineManager.BONITA_TECHNICAL_USER);
            } catch (final Exception e) {
                loginTry++;
                try {
                    Thread.sleep(500);
                } catch (final InterruptedException ex) {
                    BonitaStudioLog.error(ex, EnginePlugin.PLUGIN_ID);
                }
            }
        }
        if (session != null) {
            try {
                BOSEngineManager.getInstance().getLoginAPI().logout(session);
            } catch (final Exception e) {
                BonitaStudioLog.error(e);
            }
        } else {
            BonitaStudioLog.error("Failed to login to engine after " + MAX_LOGGING_TRY + " tries", EnginePlugin.PLUGIN_ID);
        }
    }

    protected void createLaunchConfiguration(final IServer server, final IProgressMonitor monitor) throws CoreException {
        ILaunchConfiguration conf = server.getLaunchConfiguration(false, Repository.NULL_PROGRESS_MONITOR);
        if (conf == null) {
            conf = server.getLaunchConfiguration(true,
                    Repository.NULL_PROGRESS_MONITOR);
        }
        ILaunchConfigurationWorkingCopy workingCopy = conf.getWorkingCopy();
        final String args = workingCopy.getAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, "");
        if (!args.contains(tomcatInstanceLocation)) {
            conf = server.getLaunchConfiguration(true,
                    Repository.NULL_PROGRESS_MONITOR);
            workingCopy = conf.getWorkingCopy();
        }
        final RepositoryAccessor repositoryAccessor = new RepositoryAccessor();
        repositoryAccessor.init();
        workingCopy.setAttribute(
                IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS,
                getTomcatVMArgsBuilder(repositoryAccessor).getVMArgs(tomcatInstanceLocation));
        workingCopy.setAttribute(IDebugUIConstants.ATTR_CAPTURE_IN_FILE,
                getTomcatLogFile());
        workingCopy.setAttribute(IDebugUIConstants.ATTR_APPEND_TO_FILE, true);
        workingCopy.doSave();
    }

    protected TomcatVmArgsBuilder getTomcatVMArgsBuilder(final RepositoryAccessor repositoryAccessor) {
        return new TomcatVmArgsBuilder(repositoryAccessor);
    }

    protected String getTomcatLogFile() {
        final File parentDir = Platform.getLogFileLocation().toFile().getParentFile();
        return new File(parentDir, TOMCAT_LOG_FILE).getAbsolutePath();
    }

    protected IServer createServer(final IProgressMonitor monitor, final IProject confProject, final IRuntime runtime) throws CoreException {
        final IServerType sType = ServerCore.findServerType(TOMCAT_SERVER_TYPE);
        final IFile file = confProject.getFile("bonitaTomcatServerSerialization");

        final IFolder configurationFolder = confProject
                .getFolder("tomcat_conf");
        final File sourceFolder = new File(tomcatInstanceLocation, "conf");
        PlatformUtil.copyResource(configurationFolder.getLocation().toFile(),
                sourceFolder, Repository.NULL_PROGRESS_MONITOR);
        configurationFolder.refreshLocal(IResource.DEPTH_INFINITE,
                Repository.NULL_PROGRESS_MONITOR);
        final IServer server = configureServer(runtime, sType, file,
                configurationFolder, monitor);
        portConfigurator = newPortConfigurator(server);
        final IStatus h2ServerStatus = portConfigurator.canStartH2Server(monitor);
        if (!h2ServerStatus.isOK()) {
            throw new CoreException(h2ServerStatus);
        }
        portConfigurator
                .configureServerPort(Repository.NULL_PROGRESS_MONITOR);
        return server;
    }

    private PortConfigurator newPortConfigurator(final IServer server) {
        return new PortConfigurator(server, newClientBonitaHomeBuilder(), BonitaStudioPreferencesPlugin.getDefault().getPreferenceStore());
    }

    protected IServer configureServer(final IRuntime runtime, final IServerType sType, final IFile file, final IFolder configurationFolder,
            final IProgressMonitor monitor)
            throws CoreException {
        final IServer server = ServerCore.findServer(BONITA_TOMCAT_SERVER_ID);
        IServerWorkingCopy serverWC = null;
        if (server != null) {
            serverWC = server.createWorkingCopy();
        }
        if (serverWC == null) {
            serverWC = sType.createServer(BONITA_TOMCAT_SERVER_ID, file, runtime, monitor);
        }
        serverWC.setServerConfiguration(configurationFolder);
        serverWC.setAttribute(ITomcatServer.PROPERTY_INSTANCE_DIR,
                tomcatInstanceLocation);
        serverWC.setAttribute(ITomcatServer.PROPERTY_DEPLOY_DIR,
                tomcatInstanceLocation + File.separatorChar + "webapps");
        serverWC.setAttribute(START_TIMEOUT, 300);
        return serverWC.save(true, monitor);
    }

    protected IRuntime createServerRuntime(final IRuntimeType type, final IProgressMonitor monitor) throws CoreException {
        IRuntime runtime = ServerCore.findRuntime(BONITA_TOMCAT_RUNTIME_ID);
        if (runtime == null) {
            runtime = type.createRuntime(BONITA_TOMCAT_RUNTIME_ID, monitor);
        }
        final IRuntimeWorkingCopy tomcatRuntimeWC = runtime.createWorkingCopy();
        tomcatRuntimeWC.setLocation(Path.fromOSString(tomcatInstanceLocation));
        final IStatus status = tomcatRuntimeWC.validate(null);
        if (!status.isOK()) {
            throw new RuntimeException("Failed to create a tomcat server : "
                    + status.getMessage());
        }
        return tomcatRuntimeWC.save(true, monitor);
    }

    protected IProject createServerConfigurationProject(final IProgressMonitor monitor) throws CoreException {
        final IWorkspace workspace = ResourcesPlugin.getWorkspace();
        final IProject confProject = workspace.getRoot().getProject(SERVER_CONFIGURATION_PROJECT);
        if (!confProject.exists()) {
            confProject.create(Repository.NULL_PROGRESS_MONITOR);
            confProject.open(Repository.NULL_PROGRESS_MONITOR);
            final ProjectProperties projectProperties = new ProjectProperties(confProject);
            confProject.getWorkspace().run(new IWorkspaceRunnable() {

                @Override
                public void run(final IProgressMonitor monitor) throws CoreException {
                    projectProperties.setServerProject(true, monitor);
                }
            }, monitor);
        }
        confProject.open(Repository.NULL_PROGRESS_MONITOR);
        return confProject;
    }

    protected void startWatchdog() {
        if (watchdogServer == null) {
            final Thread server = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        if (SocketUtil.isPortInUse(WATCHDOG_PORT)) {
                            final int oldPort = WATCHDOG_PORT;
                            WATCHDOG_PORT = SocketUtil.findUnusedPort(PortConfigurator.MIN_PORT_NUMBER, PortConfigurator.MAX_PORT_NUMBER);
                            BonitaStudioLog
                                    .debug("Port "
                                            + oldPort
                                            + " is not availble for server watchdog, studio will use next available port : "
                                            + WATCHDOG_PORT,
                                            EnginePlugin.PLUGIN_ID);
                        }
                        watchdogServer = new ServerSocket(WATCHDOG_PORT, 0,
                                InetAddress.getByName("localhost"));
                        if (BonitaStudioLog.isLoggable(IStatus.OK)) {
                            BonitaStudioLog.debug(
                                    "Starting studio watchdog on "
                                            + WATCHDOG_PORT,
                                    EnginePlugin.PLUGIN_ID);
                        }
                        while (watchdogServer != null) {
                            final Socket connection = watchdogServer.accept();
                            connection.close();
                        }
                        if (BonitaStudioLog.isLoggable(IStatus.OK)) {
                            BonitaStudioLog.debug("Studio watchdog shutdown",
                                    EnginePlugin.PLUGIN_ID);
                        }
                    } catch (final SocketException e1) {

                    } catch (final IOException e) {
                        BonitaStudioLog.error(e, EnginePlugin.PLUGIN_ID);
                    }

                }
            });
            server.setDaemon(true);
            server.setName("BonitaBPM Studio server watchdog");
            server.start();
        }
    }

    protected void stopWatchdog() {
        if (watchdogServer != null) {
            try {
                if (BonitaStudioLog.isLoggable(IStatus.OK)) {
                    BonitaStudioLog.debug("Shuttingdown watchdog...", EnginePlugin.PLUGIN_ID);
                }
                watchdogServer.close();
                watchdogServer = null;
                if (BonitaStudioLog.isLoggable(IStatus.OK)) {
                    BonitaStudioLog.debug("Watchdog shutdown ...", EnginePlugin.PLUGIN_ID);
                }
            } catch (final IOException e) {
                BonitaStudioLog.error(e, EnginePlugin.PLUGIN_ID);
            }
        }
    }

    private void waitServerStopped(final IProgressMonitor monitor) throws CoreException {
        while (tomcat != null
                && tomcat.getServerState() != IServer.STATE_STOPPED || portConfigurator != null && portConfigurator.h2PortInUse(monitor)) {
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                BonitaStudioLog.error(e, EnginePlugin.PLUGIN_ID);
            }
        }
    }

    public boolean serverIsStarted() {
        return tomcat != null && tomcat.getServerState() == IServer.STATE_STARTED;
    }

    public void resetServer(final IProgressMonitor monitor) {
        stopServer(monitor);
        startServer(monitor);
    }

    public synchronized void stopServer(final IProgressMonitor monitor) {
        if (serverIsStarted()) {
            monitor.subTask(Messages.stoppingWebServer);
            if (BonitaStudioLog.isLoggable(IStatus.OK)) {
                BonitaStudioLog.debug("Stopping tomcat server...", EnginePlugin.PLUGIN_ID);
            }
            stopWatchdog();
            try {
                WorkspaceResourceServerManager.getInstance().stop();
            } catch (final Exception e1) {
                BonitaStudioLog.error(e1, EnginePlugin.PLUGIN_ID);
            }
            tomcat.stop(true);
            try {
                waitServerStopped(monitor);
                tomcat.delete();
            } catch (final CoreException e) {
                BonitaStudioLog.error(e, EnginePlugin.PLUGIN_ID);
            }
            if (BonitaStudioLog.isLoggable(IStatus.OK)) {
                BonitaStudioLog.debug("Tomcat server stopped",
                        EnginePlugin.PLUGIN_ID);
            }
        }
    }

    public String generateLoginURL(final String username, final String password) {
        final IPreferenceStore store = BonitaStudioPreferencesPlugin.getDefault().getPreferenceStore();
        final String port = store.getString(BonitaPreferenceConstants.CONSOLE_PORT);
        final String host = store.getString(BonitaPreferenceConstants.CONSOLE_HOST);
        return "http://" + host + ":" + port + LOGINSERVICE_PATH + "username=" + username + "&password=" + password;
    }

    public void cleanBeforeShutdown() {
        if (BonitaStudioPreferencesPlugin.getDefault().getPreferenceStore().getBoolean(BonitaPreferenceConstants.DELETE_TENANT_ON_EXIT)) {
            cleanTenant();
        }
        if (dropBusinessDataDBOnExit()) {
            deleteBusinessDataDBFiles();
        }
    }

    protected void cleanTenant() {
        final File bonitaServerFile = Paths.get(getBonitaHomeInTomcat(), "engine-server", "work", "tenants", "1").toFile();
        PlatformUtil.delete(bonitaServerFile, null);
        final File bonitaClientFile = Paths.get(getBonitaHomeInTomcat(), "engine-client", "work", "tenants", "1").toFile();
        PlatformUtil.delete(bonitaClientFile, null);
        final File bonitaWebClientFile = Paths.get(getBonitaHomeInTomcat(), "client", "tenants", "1").toFile();
        PlatformUtil.delete(bonitaWebClientFile, null);
        final File platformTomcatConfig = Paths.get(getBonitaHomeInTomcat(), "client", "platform", "conf", "platform-tenant-config.properties").toFile();
        PlatformUtil.delete(platformTomcatConfig, null);
        try {
            FileUtil.copyFile(BonitaHomeUtil.getDefaultPlatformTenantConfigFile(), platformTomcatConfig);
        } catch (final IOException e) {
            BonitaStudioLog.error(e, EnginePlugin.PLUGIN_ID);
        }
        deleteBonitaDbFiles();
    }

    protected void deleteBusinessDataDBFiles() {
        deleteDbFiles("business");
    }

    protected void deleteBonitaDbFiles() {
        deleteDbFiles("bonita");
    }

    protected void deleteDbFiles(final String fileStartName) {
        final File workDir = getPlatformWorkDir();
        if (workDir != null && workDir.exists()) {
            for (final File file : workDir.listFiles()) {
                final String fileName = file.getName();
                if (fileName.endsWith("h2.db") && fileName.contains(fileStartName)) {
                    PlatformUtil.delete(file, null);
                    if (file.exists()) {
                        BonitaStudioLog.info(fileName + " failed to be deleted", EnginePlugin.PLUGIN_ID);
                    } else {
                        BonitaStudioLog.info(fileName + " has been deleted successfuly", EnginePlugin.PLUGIN_ID);
                    }
                }
            }
        }
    }

    protected String getBonitaHomeInTomcat() {
        return Paths.get(tomcatInstanceLocation, "bonita").toString();
    }

    protected File getPlatformWorkDir() {
        return Paths.get(getBonitaHomeInTomcat(), "engine-server", "work", "platform").toFile();
    }

    public File getBonitaLogFile() {
        final File logDir = new File(tomcatInstanceLocation, "logs");
        final File[] list = logDir.listFiles(new FilenameFilter() {

            @Override
            public boolean accept(final File file, final String fileName) {
                return fileName.contains("bonita");
            }
        });
        Date fileDate = new Date(0);
        File lastLogFile = null;
        for (final File f : list) {
            final Date d = new Date(f.lastModified());
            if (d.after(fileDate)) {
                fileDate = d;
                lastLogFile = f;
            }
        }
        return lastLogFile;
    }

    private boolean dropBusinessDataDBOnExit() {
        final IPreferenceStore preferenceStore = EnginePlugin.getDefault().getPreferenceStore();
        return preferenceStore.getBoolean(EnginePreferenceConstants.DROP_BUSINESS_DATA_DB_ON_EXIT_PREF);
    }

}
