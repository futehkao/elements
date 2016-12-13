/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.network.shell;

import net.e6tech.elements.security.SymmetricCipher;
import org.crsh.console.jline.JLineProcessor;
import org.crsh.console.jline.Terminal;
import org.crsh.console.jline.TerminalFactory;
import org.crsh.console.jline.console.ConsoleReader;
import org.crsh.console.jline.internal.Configuration;
import org.crsh.plugin.Embedded;
import org.crsh.plugin.PluginContext;
import org.crsh.shell.Shell;
import org.crsh.shell.ShellFactory;
import org.crsh.shell.impl.command.CRaSHShellFactory;
import org.crsh.ssh.SSHPlugin;
import org.crsh.telnet.TelnetPlugin;
import org.crsh.util.InterruptHandler;
import org.crsh.util.Utils;
import org.crsh.vfs.spi.FSMountFactory;
import org.crsh.vfs.spi.file.FileMountFactory;
import org.fusesource.jansi.AnsiConsole;

import java.io.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Created by futeh.
 *
 * Shelld is a daemon that can accepts ssh or telnet connections although
 * they can be turned off.
 */
public class Shelld extends Embedded {

    static {
        SymmetricCipher.initialize();
    }

    protected ClassLoader classLoader;
    private Map<String,Object> attributes = new HashMap<String, Object>();
    protected final HashMap<String, FSMountFactory<?>> drivers = new HashMap<String, FSMountFactory<?>>();
    private String cmdMountPointConfig;
    private String confMountPointConfig;
    private String propertiesFile;
    private Properties properties;
    private Set<String> excludes = new LinkedHashSet<>();
    private Set<String> includes = new LinkedHashSet<>();
    private Set<String> commandPaths = new LinkedHashSet<>();
    private String fileRootDir;         // for using with files
    private PluginContext context;
    private int sshPort = -1;
    private int sshAuthTimeout = 300000;
    private int sshIdleTimeout = 300000;
    private int telnetPort = -1;

    public static void main(String ... args) throws Exception {
        Shelld shelld = new Shelld();
        shelld.addCmdClassPath("net/e6tech/elements/network/shell/commands")
                .telnetPort(5000)
                .sshPort(2000)
                .start();

        /* shelld.fileRootDir("/Users/futeh/Projects/e6tech/elements/network/src/main/resources/net/e6tech/elements/network/shell")
                .cmdMountPointConfig("file:commands")
                .telnetPort(5000)
                .sshPort(2000)
                .start(); */
    }

    public Shelld sshPort(int port) {
        sshPort = port;
        return this;
    }

    public int getSshPort() {
        return sshPort;
    }

    public void setSshPort(int sshPort) {
        this.sshPort = sshPort;
    }

    public int getSshAuthTimeout() {
        return sshAuthTimeout;
    }

    public void setSshAuthTimeout(int sshAuthTimeout) {
        this.sshAuthTimeout = sshAuthTimeout;
    }

    public int getSshIdleTimeout() {
        return sshIdleTimeout;
    }

    public void setSshIdleTimeout(int sshIdleTimeout) {
        this.sshIdleTimeout = sshIdleTimeout;
    }

    public Shelld telnetPort(int port) {
        telnetPort = port;
        return this;
    }

    public int getTelnetPort() {
        return telnetPort;
    }

    public void setTelnetPort(int telnetPort) {
        this.telnetPort = telnetPort;
    }

    public void addAttribute(String name, Object object) {
        attributes.put(name, object);
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public Object removeAttribute(String name) {
        return attributes.remove(name);
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public List<String> getExcludes() {
        ArrayList<String> list = new ArrayList<>();
        list.addAll(excludes);
        return Collections.unmodifiableList(list);
    }

    public void setExcludes(List<String> excludes) {
        this.excludes.clear();
        this.excludes.addAll(excludes);
    }

    public List<String> getIncludes() {
        ArrayList<String> list = new ArrayList<>();
        list.addAll(includes);
        return Collections.unmodifiableList(list);
    }

    public void setIncludes(List<String> includes) {
        this.includes.clear();
        this.includes.addAll(includes);
    }

    public void includePlugins(String ... plugins) {
        mergePlugins(includes, excludes, plugins);
    }

    public void excludePlugins(String ... plugins) {
        mergePlugins(excludes, includes, plugins);
    }

    protected void mergePlugins(Set<String> add, Set<String> remove, String ... plugins) {
        if (plugins != null) {
            for (String p : plugins) {
                if (!add.contains(p)) {
                    add.add(p);
                }
                remove.remove(p);
            }
        }
    }

    public List<String> getCommandPaths() {
        ArrayList<String> list = new ArrayList<>();
        list.addAll(commandPaths);
        return Collections.unmodifiableList(list);
    }

    public void setCommandPaths(List<String> commandPaths) {
        this.commandPaths.clear();
        this.commandPaths.addAll(commandPaths);
    }

    public Shelld addCmdClassPath(String path) {
        commandPaths.add(path);
        return this;
    }

    public Shelld fileRootDir(String dir) {
        fileRootDir = dir;
        return this;
    }

    public String getFileRootDir() {
        return fileRootDir;
    }

    public void setFileRootDir(String fileRootDir) {
        this.fileRootDir = fileRootDir;
    }

    public Shelld propertiesFile(String fileName) {
        propertiesFile = fileName;
        return this;
    }

    public String getPropertiesFile() {
        return propertiesFile;
    }

    public void setPropertiesFile(String propertiesFile) {
        this.propertiesFile = propertiesFile;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public void start() throws Exception {
        if (classLoader == null) {
            if (Thread.currentThread().getContextClassLoader() != null) classLoader = Thread.currentThread().getContextClassLoader();
            else classLoader = getClass().getClassLoader();
        }

        Properties config = new Properties();
        if (propertiesFile != null) config.load(new FileReader(propertiesFile));
        if (properties != null) config.putAll(properties);

        if (sshPort <= 0 && config.containsKey("crash.ssh.port")) {
            sshPort = Integer.parseInt(config.getProperty("crash.ssh.port"));
        }

        if (config.containsKey("crash.vfs.refresh_period")) config.setProperty("crash.vfs.refresh_period", "1");

        excludePlugins(SSHPlugin.class.getName());
        if (sshPort > 0) {
            config.setProperty("crash.ssh.port", "" + sshPort);
            if (!config.containsKey("crash.ssh.auth_timeout")) config.setProperty("crash.ssh.auth_timeout", "" + sshAuthTimeout);
            if (!config.containsKey("crash.ssh.idle_timeout"))config.setProperty("crash.ssh.idle_timeout", "" + sshIdleTimeout);
            includePlugins(CustomerSSHPlugin.class.getName()); // using my own
            includePlugins(SshKeyAuthenticationPlugin.class.getName());
        }

        if (telnetPort <= 0 && config.containsKey("crash.telnet.port")) {
            telnetPort = Integer.parseInt(config.getProperty("crash.telnet.port"));
        }

        if (telnetPort > 0) {
            config.setProperty("crash.telnet.port", "" + telnetPort);
        } else {
            excludePlugins(TelnetPlugin.class.getName());
        }

        includePlugins(PasswordAuthenticationPlugin.class.getName());
        excludePlugins(CRaSHShellFactory.class.getName());  // using my own
        includePlugins(CustomShellFactory.class.getName());  // using my own


        setConfig(config);

        try {
            // resolveCmdMountPointConfig and resolveConfMountPointConfig can
            // either start with classpath: or file:
            File rootDir = Utils.getCurrentDirectory();
            if (fileRootDir != null) rootDir = new File(fileRootDir);
            ClassPathMountFactory classPathMountFactory = new ClassPathMountFactory(classLoader);
            FileMountFactory fileMountFactory = new FileMountFactory(rootDir);
            drivers.put("classpath", classPathMountFactory);
            drivers.put("file", fileMountFactory);
            commandPaths.forEach(path -> classPathMountFactory.addCommandPath(path));
        } catch (Exception e) {
            log.log(Level.SEVERE, "Could not initialize classpath driver", e);
            return;
        }

        //
        PluginDiscovery discovery = new PluginDiscovery(classLoader);
        for (String exclude : excludes) discovery.removePlugin(exclude);
        for (String include : includes) discovery.addPlugin(include);
        //
        context = start(Collections.unmodifiableMap(attributes), discovery, classLoader);
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader loader) {
        this.classLoader = loader;
    }

    public Shelld cmdMountPointConfig(String mountPoint) {
        cmdMountPointConfig = mountPoint;
        return this;
    }

    public String getCmdMountPointConfig() {
        return cmdMountPointConfig;
    }

    public void setCmdMountPointConfig(String cmdMountPointConfig) {
        this.cmdMountPointConfig = cmdMountPointConfig;
    }

    public Shelld confMountPointConfig(String mountPoint) {
        confMountPointConfig = mountPoint;
        return this;
    }

    public String getConfMountPointConfig() {
        return confMountPointConfig;
    }

    public void setConfMountPointConfig(String confMountPointConfig) {
        this.confMountPointConfig = confMountPointConfig;
    }

    @Override
    protected Map<String, FSMountFactory<?>> getMountFactories() {
        return drivers;
    }

    @Override
    protected String resolveConfMountPointConfig() {
        return confMountPointConfig != null ? confMountPointConfig : getDefaultConfMountPointConfig();
    }

    @Override
    protected String resolveCmdMountPointConfig() {
        return cmdMountPointConfig != null ? cmdMountPointConfig : getDefaultCmdMountPointConfig();
    }

    protected String getDefaultCmdMountPointConfig() {
        return "classpath:/crash/commands/";
    }

    protected String getDefaultConfMountPointConfig() {
        return "classpath:/crash/";
    }

    public void destroy() throws Exception {
        stop();
    }

    public void interactive() throws IOException {
        ShellFactory factory = context.getPlugin(ShellFactory.class);
        Shell shell = factory.create(null);
        //
        if (shell != null) {
            //
            final Terminal term = TerminalFactory.create();

            //
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        term.restore();
                    }
                    catch (Exception ignore) {
                    }
                }
            });

            //
            String encoding = Configuration.getEncoding();

            // Use AnsiConsole only if term doesn't support Ansi
            PrintStream out;
            PrintStream err;
            boolean ansi;
            if (term.isAnsiSupported()) {
                out = new PrintStream(new BufferedOutputStream(term.wrapOutIfNeeded(new FileOutputStream(FileDescriptor.out)), 16384), false, encoding);
                err = new PrintStream(new BufferedOutputStream(term.wrapOutIfNeeded(new FileOutputStream(FileDescriptor.err)), 16384), false, encoding);
                ansi = true;
            } else {
                out = AnsiConsole.out;
                err = AnsiConsole.err;
                ansi = false;
            }

            //
            FileInputStream in = new FileInputStream(FileDescriptor.in);
            ConsoleReader reader = new ConsoleReader(null, in, out, term);

            //
            final JLineProcessor processor = new JLineProcessor(ansi, shell, reader, out);

            //
            InterruptHandler interruptHandler = new InterruptHandler(new Runnable() {
                @Override
                public void run() {
                    processor.interrupt();
                }
            });
            interruptHandler.install();

            //
            Thread thread = new Thread(processor);
            thread.setDaemon(true);
            thread.start();

            //
            try {
                processor.closed();
            }
            catch (Throwable t) {
                t.printStackTrace();
            } finally {

            }
        }
    }
}
