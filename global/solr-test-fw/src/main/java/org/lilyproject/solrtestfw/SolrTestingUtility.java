/*
 * Copyright 2010 Outerthought bvba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lilyproject.solrtestfw;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.lilyproject.util.MavenUtil;
import org.lilyproject.util.test.TestHomeUtil;
import org.lilyproject.util.zookeeper.ZkUtil;
import org.lilyproject.util.zookeeper.ZooKeeperItf;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.webapp.WebAppContext;

public class SolrTestingUtility {
    private int solrPort = 8983;
    private Server server;
    private SolrDefinition solrDef;
    private String autoCommitSetting;
    private String solrWarPath;
    private File solrHomeDir;
    private final boolean clearData;
    private final boolean useSolrCloud;

    public SolrTestingUtility() throws IOException {
        this(null);
    }

    public SolrTestingUtility(File solrHomeDir) throws IOException {
        this(solrHomeDir, true);
    }

    public SolrTestingUtility(File solrHomeDir, boolean clearData) throws IOException {
        this(solrHomeDir, clearData, false);
    }

    public SolrTestingUtility(File solrHomeDir, boolean clearData, boolean useSolrCloud) throws IOException {
        this.clearData = clearData;
        this.useSolrCloud = useSolrCloud;

        if (solrHomeDir == null) {
            this.solrHomeDir = TestHomeUtil.createTestHome("lily-solrtesthome-");
        } else {
            this.solrHomeDir = solrHomeDir;
        }
    }

    public void setSolrDefinition(SolrDefinition solrDef) {
        this.solrDef = solrDef;
    }

    public String getAutoCommitSetting() {
        return autoCommitSetting;
    }

    public void setAutoCommitSetting(String autoCommitSetting) {
        this.autoCommitSetting = autoCommitSetting;
    }

    public String getSolrWarPath() {
        return solrWarPath;
    }

    public void setSolrWarPath(String solrWarPath) {
        this.solrWarPath = solrWarPath;
    }

    public File getSolrHomeDir() {
        return solrHomeDir;
    }

    public void setSolrPort(int solrPort) {
        this.solrPort = solrPort;
    }

    public void start() throws Exception {
        if (solrDef == null || solrDef.getCores().size() == 0) {
            solrDef = new SolrDefinition(SolrDefinition.defaultSolrSchema(), SolrDefinition.defaultSolrConfig());
        }

        SolrHomeDirSetup.write(solrHomeDir, solrDef, autoCommitSetting, solrPort);

        setSystemProperties();

        // Determine location of Solr war file:
        //  - either provided by setSolrWarPath()
        //  - or provided via system property solr.war
        //  - finally use default, assuming availability in local repository
        if (solrWarPath == null) {
            solrWarPath = System.getProperty("solr.war");
        }
        if (solrWarPath == null) {
            Properties properties = new Properties();
            InputStream is = getClass().getResourceAsStream("solr.properties");
            if (is != null) {
                properties.load(is);
                is.close();
                String solrVersion = properties.getProperty("solr.version");
                solrWarPath = MavenUtil.findLocalMavenRepository().getAbsolutePath() +
                        "/org/apache/solr/solr/" + solrVersion + "/solr-" + solrVersion + ".war";
            }
        }

        if (solrWarPath == null || !new File(solrWarPath).exists()) {
            System.out.println();
            System.out.println("------------------------------------------------------------------------");
            System.out.println("Solr not found at");
            System.out.println(solrWarPath);
            System.out.println("------------------------------------------------------------------------");
            System.out.println();
            throw new Exception("Solr war not found at " + solrWarPath);
        }

        server = createServer();
        server.start();
    }

    private Server createServer() throws Exception {
        if (this.useSolrCloud) {
            // create path on zookeeper for solr cloud
            ZooKeeperItf zk = ZkUtil.connect("localhost:2181", 10000);
            ZkUtil.createPath(zk, "/solr");
            zk.close();
        }

        Server server = new Server(solrPort);
        WebAppContext ctx = new WebAppContext(solrWarPath, "/solr");
        // The reason to change the classloading behavior was primarily so that the logging libraries would
        // be inherited, and hence that Solr would use the same logging system & conf.
        ctx.setParentLoaderPriority(true);
        server.addHandler(ctx);
        return server;
    }

    public String getDefaultUri() {
        return "http://localhost:" + solrPort + "/solr/" + SolrDefinition.DEFAULT_CORE_NAME;
    }

    public String getBaseUri() {
        return "http://localhost:" + solrPort + "/solr";
    }

    public Server getServer() {
        return server;
    }

    public void stop() throws Exception {
        if (server != null) {
            server.stop();
        }

        if (clearData && solrHomeDir != null) {
            FileUtils.deleteDirectory(solrHomeDir);
        }
    }

    /**
     * Restarts the servlet container without throwing away the data.
     */
    public void restartServletContainer() throws Exception {
        server.stop();
        server.join();

        // Somehow restarting the same server object does not work, so create a new one
        server = createServer();
        server.start();
    }

    private void setSystemProperties() {
        System.setProperty("solr.solr.home", solrHomeDir.getAbsolutePath());
        if (this.useSolrCloud) {
            System.setProperty("zkHost", "localhost:2181/solr");
            // I'd rather start with an empty set of cores, but our testfw is currently designed
            // around always having the default 'core0' core around, and OTOH SolrCloud doens't
            // support having cores not associated with a collection, so until we review this
            // more thoroughly, we keep this in place
            System.setProperty("bootstrap_conf", "true");
        }
    }

    public boolean isSolrCloudEnabled() {
        return useSolrCloud;
    }

}
