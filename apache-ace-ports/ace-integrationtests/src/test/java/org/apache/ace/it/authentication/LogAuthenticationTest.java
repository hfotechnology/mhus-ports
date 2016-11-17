/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ace.it.authentication;

import static org.apache.ace.it.Options.jetty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.discovery.property.constants.DiscoveryConstants;
import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.ace.identification.property.constants.IdentificationConstants;
import org.apache.ace.it.Options.Ace;
import org.apache.ace.it.Options.Felix;
import org.apache.ace.it.Options.Knopflerfish;
import org.apache.ace.it.Options.Osgi;
import org.apache.ace.log.Log;
import org.apache.ace.log.LogDescriptor;
import org.apache.ace.log.LogEvent;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.impl.constants.RepositoryConstants;
import org.apache.ace.server.log.store.LogStore;
import org.apache.ace.test.constants.TestConstants;
import org.apache.felix.dm.Component;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.options.extra.CleanCachesOption;
import org.osgi.framework.Constants;
import org.osgi.service.http.HttpService;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Integration tests for the audit log. Both a server and a target are setup
 * on the same machine. The audit log is run and we check if it is indeed
 * replicated to the server.
 */
@RunWith(JUnit4TestRunner.class)
public class LogAuthenticationTest extends AuthenticationTestBase {

    private static final String AUDITLOG_ENDPOINT = "/auditlog";

    private static final String HOST = "localhost";
    private static final String TARGET_ID = "target-id";

    private volatile Log m_auditLog;
    private volatile LogStore m_serverStore;
    private volatile Runnable m_auditLogSyncTask;
    private volatile Repository m_userRepository;
    private volatile UserAdmin m_userAdmin;
    private volatile ConnectionFactory m_connectionFactory;

    /**
     * @return the PAX Exam configuration options, never <code>null</code>.
     */
    @Configuration
    public Option[] configuration() {
        return options(
            systemProperty("org.osgi.service.http.port").value("" + TestConstants.PORT),
            new CleanCachesOption(),
            junitBundles(),
            provision(
                // Misc bundles...
                Osgi.compendium(),
                Felix.dependencyManager(),
                jetty(),
                Felix.configAdmin(),
                Felix.preferences(),
                Felix.eventAdmin(),
                Knopflerfish.useradmin(),
                Knopflerfish.log(),
                // ACE core bundles...
                Ace.util(),
                Ace.authentication(),
                Ace.authenticationProcessorBasicAuth(),
                Ace.connectionFactory(),
                Ace.rangeApi(),
                Ace.discoveryApi(),
                Ace.discoveryProperty(),
                Ace.identificationApi(),
                Ace.identificationProperty(),
                Ace.log(),
                Ace.logListener(),
                Ace.logServlet(),
                Ace.serverLogStore(),
                Ace.logTask(),
                Ace.targetLog(),
                Ace.targetLogStore(),
                Ace.httplistener(),
                Ace.repositoryApi(),
                Ace.repositoryImpl(),
                Ace.repositoryServlet(),
                Ace.configuratorServeruseradmin(),
                Ace.obrMetadata(),
                Ace.obrServlet(),
                Ace.obrStorage(),
                Ace.clientRepositoryApi(),
                Ace.clientRepositoryImpl(),
                Ace.clientRepositoryHelperBase(),
                Ace.clientRepositoryHelperBundle(),
                Ace.clientRepositoryHelperConfiguration(),
                Ace.scheduler(),
                Ace.resourceprocessorUseradmin(),
                Ace.configuratorUseradminTask()
            )
        );
    }

    @Override
    public void setupTest() throws Exception {
        super.setupTest();

        String baseURL = "http://" + HOST + ":" + TestConstants.PORT;

        URL testURL = new URL(baseURL.concat(AUDITLOG_ENDPOINT));
        assertTrue("Failed to access auditlog in time!", waitForURL(m_connectionFactory, testURL, 401, 15000));

        String userName = "d";
        String password = "f";

        importSingleUser(m_userRepository, userName, password);
        waitForUser(m_userAdmin, userName);

        configureFactory("org.apache.ace.connectionfactory", 
            "authentication.baseURL", baseURL.concat(AUDITLOG_ENDPOINT), 
            "authentication.type", "basic",
            "authentication.user.name", userName,
            "authentication.user.password", password);

        assertTrue("Failed to access auditlog in time!", waitForURL(m_connectionFactory, testURL, 200, 15000));
    }

    /**
     * Tests that accessing the log servlet with authentication works when given the right credentials.
     */
    @Test
    public void testAccessLogServletWithCorrectCredentialsOk() throws Exception {
        String tid1 = "42";
        String tid2 = "47";

        // prepare the store
        List<LogEvent> events = new ArrayList<LogEvent>();
        events.add(new LogEvent(tid1, 1, 1, 1, 1, new Properties()));
        events.add(new LogEvent(tid2, 1, 1, 1, 1, new Properties()));
        m_serverStore.put(events);

        List<String> result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/query");
        assert result.size() > 1 : "We expect at least two logs on the server.";
    }

    /**
     * Tests that the log synchronization works when the log servlet has authentication enabled.
     */
    @Test
    public void testLogSynchronizationOk() throws Exception {
        final int type = 12345;
        
        // now log another event
        Properties props = new Properties();
        props.put("one", "value1");
        props.put("two", "value2");
        m_auditLog.log(type, props);

        boolean found = false;
        
        long startTime = System.currentTimeMillis();
        long waitTime = 5000; // milliseconds
        
        while (!found && ((System.currentTimeMillis() - startTime) < waitTime)) {
            // synchronize again
            m_auditLogSyncTask.run();

            // get and evaluate results (note that there is some concurrency that might interfere with this test)
            List<LogDescriptor> ranges2 = m_serverStore.getDescriptors();
            if (ranges2.isEmpty()) {
                continue;
            }

            List<LogEvent> events = m_serverStore.get(ranges2.get(0));
            for (LogEvent event : events) {
                if (event.getType() == type) {
                    Dictionary properties = event.getProperties();
                    assertEquals("value1", properties.get("one"));
                    assertEquals("value2", properties.get("two"));
                    found = true;
                    break;
                }
            }

            // wait if we have not found anything yet
            if (!found) {
                TimeUnit.MILLISECONDS.sleep(100);
            }
        }

        assertTrue("We could not retrieve our audit log event (after 5 seconds).", found);
    }

    /**
     * {@inheritDoc}
     */
    protected void before() throws Exception {
        
        String baseURL = "http://" + HOST + ":" + TestConstants.PORT;

        getService(SessionFactory.class).createSession("test-session-ID");

        configureFactory("org.apache.ace.server.repository.factory",
            RepositoryConstants.REPOSITORY_NAME, "users",
            RepositoryConstants.REPOSITORY_CUSTOMER, "apache",
            RepositoryConstants.REPOSITORY_MASTER, "true");

        configure("org.apache.ace.configurator.useradmin.task.UpdateUserAdminTask",
            "repositoryName", "users",
            "repositoryCustomer", "apache");
        
        configure("org.apache.ace.scheduler",
            "org.apache.ace.configurator.useradmin.task.UpdateUserAdminTask", "100");

        configure(DiscoveryConstants.DISCOVERY_PID,
            DiscoveryConstants.DISCOVERY_URL_KEY, baseURL);
        configure(IdentificationConstants.IDENTIFICATION_PID,
            IdentificationConstants.IDENTIFICATION_TARGETID_KEY, TARGET_ID);

        configureFactory("org.apache.ace.target.log.store.factory",
            "name", "auditlog");
        configureFactory("org.apache.ace.target.log.factory",
            "name", "auditlog");
        configureFactory("org.apache.ace.target.log.sync.factory",
            "name", "auditlog");
        configureFactory("org.apache.ace.server.log.servlet.factory",
            "name", "auditlog",
            HttpConstants.ENDPOINT, AUDITLOG_ENDPOINT,
            "authentication.enabled", "true");
        configureFactory("org.apache.ace.server.log.store.factory",
            "name", "auditlog");
    }

    /**
     * {@inheritDoc}
     */
    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(UserAdmin.class).setRequired(true))
                .add(createServiceDependency()
                    .setService(Repository.class, "(&(" + RepositoryConstants.REPOSITORY_NAME + "=users)(" + RepositoryConstants.REPOSITORY_CUSTOMER + "=apache))")
                    .setRequired(true))
                .add(createServiceDependency().setService(ConnectionFactory.class).setRequired(true))
                .add(createServiceDependency().setService(HttpService.class).setRequired(true))
                .add(createServiceDependency().setService(Log.class, "(&(" + Constants.OBJECTCLASS + "=" + Log.class.getName() + ")(name=auditlog))").setRequired(true))
                .add(createServiceDependency().setService(LogStore.class, "(&(" + Constants.OBJECTCLASS + "=" + LogStore.class.getName() + ")(name=auditlog))").setRequired(true))
                .add(createServiceDependency().setService(Runnable.class, "(&(" + Constants.OBJECTCLASS + "=" + Runnable.class.getName() + ")(taskName=auditlog))").setRequired(true))
        };
    }

    private List<String> getResponse(String request) throws IOException {
        List<String> result = new ArrayList<String>();
        InputStream in = null;
        try {
            in = m_connectionFactory.createConnection(new URL(request)).getInputStream();
            byte[] response = new byte[in.available()];
            in.read(response);

            StringBuilder element = new StringBuilder();
            for (byte b : response) {
                switch(b) {
                    case '\n' :
                        result.add(element.toString());
                        element = new StringBuilder();
                        break;
                    default :
                        element.append(b);
                }
            }
        }
        finally {
            try {
                in.close();
            }
            catch (Exception e) {
                // no problem.
            }
        }
        return result;
    }
}
