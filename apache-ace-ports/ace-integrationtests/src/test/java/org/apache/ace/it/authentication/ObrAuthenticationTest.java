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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.it.Options.Ace;
import org.apache.ace.it.Options.Felix;
import org.apache.ace.it.Options.Knopflerfish;
import org.apache.ace.it.Options.Osgi;
import org.apache.ace.obr.storage.file.constants.OBRFileStoreConstants;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.impl.constants.RepositoryConstants;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.FileUtils;
import org.apache.ace.test.utils.NetUtils;
import org.apache.felix.dm.Component;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.options.extra.CleanCachesOption;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Provides a test case in which the OBR has authentication enabled, and the 
 * rest of ACE has to remain function correctly.
 */
@RunWith(JUnit4TestRunner.class)
public class ObrAuthenticationTest extends AuthenticationTestBase {
    
    private volatile String m_endpoint;
    private volatile File m_storeLocation;
    private volatile String m_authConfigPID;
    
    /* Injected by dependency manager */
    private volatile ArtifactRepository m_artifactRepository; 
    private volatile Repository m_userRepository;
    private volatile UserAdmin m_userAdmin;
    private volatile ConfigurationAdmin m_configAdmin;
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
                Ace.log(),
                Ace.serverLogStore(),
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
        m_endpoint = "/obr";
        
        String tmpDir = System.getProperty("java.io.tmpdir");
        m_storeLocation = new File(tmpDir, "store");
        m_storeLocation.delete();
        m_storeLocation.mkdirs();
        
        super.setupTest();

        String userName = "d";
        String password = "f";

        importSingleUser(m_userRepository, userName, password);
        waitForUser(m_userAdmin, userName);

        URL obrURL = new URL("http://localhost:" + TestConstants.PORT + m_endpoint + "/");
        m_artifactRepository.setObrBase(obrURL);

        URL testURL = new URL(obrURL, "repository.xml");

        assertTrue("Failed to access OBR in time!", waitForURL(m_connectionFactory, testURL, 401, 15000));

        m_authConfigPID = configureFactory("org.apache.ace.connectionfactory", 
                "authentication.baseURL", obrURL.toExternalForm(), 
                "authentication.type", "basic",
                "authentication.user.name", userName,
                "authentication.user.password", password);

        assertTrue("Failed to access auditlog in time!", waitForURL(m_connectionFactory, testURL, 200, 15000));
    }

    /**
     * Tears down the set up of the test case.
     * 
     * @throws java.lang.Exception not part of this test case.
     */
    @After
    public void tearDown() throws Exception {
        FileUtils.removeDirectoryWithContent(m_storeLocation);
    }

    /**
     * Test that we can retrieve the 'repository.xml' from the OBR.
     */
    @Test
    public void testAccessObrRepositoryWithCredentialsOk() throws IOException {
        URL url = new URL("http://localhost:" + TestConstants.PORT + m_endpoint + "/repository.xml");
        
        URLConnection conn = m_connectionFactory.createConnection(url);
        assertNotNull(conn);

        Object content = conn.getContent();
        assertNotNull(content);
    }

    /**
     * Test that we cannot retrieve the 'repository.xml' from the OBR without any credentials.
     */
    @Test(expected = IOException.class)
    public void testAccessObrRepositoryWithoutCredentialsFail() throws IOException {
        URL url = new URL("http://localhost:" + TestConstants.PORT + m_endpoint + "/repository.xml");
        
        // do NOT use connection factory as it will supply the credentials for us...
        URLConnection conn = url.openConnection();
        assertNotNull(conn);

        // we expect a 401 for this URL...
        NetUtils.waitForURL(url, 401, 15000);

        // ...causing all other methods on URLConnection to fail...
        conn.getContent(); // should fail!
    }

    /**
     * Test that we cannot retrieve the 'repository.xml' from the OBR with incorrect credentials.
     */
    @Test(expected = IOException.class)
    public void testAccessObrRepositoryWithWrongCredentialsFail() throws IOException {
        org.osgi.service.cm.Configuration configuration = m_configAdmin.getConfiguration(m_authConfigPID);
        assertNotNull(configuration);

        // Simulate incorrect credentials by updating the config of the connection factory...
        configuration.getProperties().put("authentication.user.name", "foo");
        
        configuration.update();

        URL url = new URL("http://localhost:" + TestConstants.PORT + m_endpoint + "/repository.xml");
        
        // do NOT use connection factory as it will supply the credentials for us...
        URLConnection conn = url.openConnection();
        assertNotNull(conn);

        // we expect a 401 for this URL...
        NetUtils.waitForURL(url, 401, 15000);

        // ...causing all other methods on URLConnection to fail...
        conn.getContent(); // should fail!
    }

    /**
     * Test that an import of an artifact through the API of ACE works, making sure they can access an authenticated OBR as well.
     */
    @Test
    public void testImportArtifactWithCredentialsOk() throws Exception {
        // Use a valid JAR file, without a Bundle-SymbolicName header.
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1");
        attributes.putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
        attributes.putValue(BundleHelper.KEY_SYMBOLICNAME, "org.apache.ace.test");

        File temp = File.createTempFile("org.apache.ace.test", ".jar");
        temp.deleteOnExit();
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(temp), manifest);
        jos.close();

        m_artifactRepository.importArtifact(temp.toURI().toURL(), true /* upload */);

        assertEquals(1, m_artifactRepository.get().size());
        assertTrue(m_artifactRepository.getResourceProcessors().isEmpty());

        // Create a JAR file which looks like a resource processor supplying bundle.
        attributes.putValue(BundleHelper.KEY_RESOURCE_PROCESSOR_PID, "someProcessor");
        attributes.putValue(BundleHelper.KEY_VERSION, "1.0.0.processor");

        temp = File.createTempFile("org.apache.ace.test", ".jar");
        temp.deleteOnExit();
        jos = new JarOutputStream(new FileOutputStream(temp), manifest);
        jos.close();

        m_artifactRepository.importArtifact(temp.toURI().toURL(), true);

        assertEquals(1, m_artifactRepository.get().size());
        assertEquals(1, m_artifactRepository.getResourceProcessors().size());
    }

    /**
     * Test that an import of an artifact through the API of ACE works, making sure they can access an authenticated OBR as well.
     */
    @Test(expected = IOException.class)
    public void testImportArtifactWithoutCredentialsFail() throws Exception {
        org.osgi.service.cm.Configuration configuration = m_configAdmin.getConfiguration(m_authConfigPID);
        assertNotNull(configuration);

        // Delete the credentials for the OBR-URL, thereby simulating wrong credentials for the OBR...
        configuration.delete();

        // Use a valid JAR file, without a Bundle-SymbolicName header.
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1");
        attributes.putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
        attributes.putValue(BundleHelper.KEY_SYMBOLICNAME, "org.apache.ace.test");

        File temp = File.createTempFile("org.apache.ace.test", ".jar");
        temp.deleteOnExit();
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(temp), manifest);
        jos.close();

        m_artifactRepository.importArtifact(temp.toURI().toURL(), true /* upload */); // should fail!
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void before() throws Exception {
        final String fileLocation = m_storeLocation.getAbsolutePath();

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

        configure("org.apache.ace.obr.servlet", 
            "OBRInstance", "singleOBRServlet", 
            "org.apache.ace.server.servlet.endpoint", m_endpoint, 
            "authentication.enabled", "true");
        configure("org.apache.ace.obr.storage.file", 
            "OBRInstance", "singleOBRStore",
            OBRFileStoreConstants.FILE_LOCATION_KEY, fileLocation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(ArtifactRepository.class).setRequired(true))
                .add(createServiceDependency().setService(ConnectionFactory.class).setRequired(true))
                .add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true))
                .add(createServiceDependency().setService(UserAdmin.class).setRequired(true))
                .add(createServiceDependency()
                    .setService(Repository.class, "(&(" + RepositoryConstants.REPOSITORY_NAME + "=users)(" + RepositoryConstants.REPOSITORY_CUSTOMER + "=apache))")
                    .setRequired(true))
        };
    }
}
