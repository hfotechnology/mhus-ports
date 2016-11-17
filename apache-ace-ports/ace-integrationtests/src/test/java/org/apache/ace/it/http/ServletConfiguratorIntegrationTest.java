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
package org.apache.ace.it.http;

import static org.apache.ace.it.Options.jetty;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.it.Options.Ace;
import org.apache.ace.it.Options.Felix;
import org.apache.ace.it.Options.Osgi;
import org.apache.ace.test.constants.TestConstants;
import org.apache.felix.dm.Component;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.osgi.service.http.HttpService;

@RunWith(JUnit4TestRunner.class)
public class ServletConfiguratorIntegrationTest extends IntegrationTestBase {

    @Configuration
    public Option[] configuration() {
        return options(
            systemProperty("org.osgi.service.http.port").value("" + TestConstants.PORT),
            new VMOption("-ea"),
            junitBundles(),
            provision(
                Osgi.compendium(),
                Felix.dependencyManager(),
                jetty(),
                Felix.configAdmin(),
                Ace.httplistener()
            )
        );
    }

    protected void before() throws IOException {
        m_echoServlet = new EchoServlet();
        Dictionary<String, String> dictionary = new Hashtable<String, String>();
        dictionary.put(HttpConstants.ENDPOINT, "/echoServlet");
        m_echoServletService = m_dependencyManager.createComponent()
            .setImplementation(m_echoServlet)
            .setInterface(Servlet.class.getName(), dictionary);

        m_mockHttp = new MockHttpService();
        m_mockHttpService = m_dependencyManager.createComponent()
            .setImplementation(m_mockHttp)
            .setInterface(HttpService.class.getName(), null);
    }

    // the echo servlet
    private HttpServlet m_echoServlet;
    // echo servlet service-reference
    private Component m_echoServletService;
    // mock http service
    private MockHttpService m_mockHttp;

    //mock http service-reference
    private Component m_mockHttpService;

    /**
     * Start the http service and then register a servlet and see if it works
     * After that, try to unregister
     */
    @Test
    public void testRegisterServlet() throws Exception {
        m_dependencyManager.add(m_echoServletService);
        assert waitForEchoServlet(true) : "TestValue not echo'd back";

        m_dependencyManager.remove(m_echoServletService);
        assert !waitForEchoServlet(false) : "The servlet should not be available anymore";
    }

    /**
     * Register a servlet with 2 http services, try to unregister and see if it is removed from both
     */
    @Test
    public void testServletOnTwoHttpServices() throws Exception {
        // also use the mock version
        m_dependencyManager.add(m_mockHttpService);
        m_dependencyManager.add(m_echoServletService);
        assert waitForEchoServlet(true) : "TestValue not echo'd back";
        assert m_mockHttp.isRegisterCalled() : "Servlet not registered with the mock service";


        m_dependencyManager.remove(m_echoServletService);
        assert !waitForEchoServlet(false) : "The servlet should not be available anymore";
        assert m_mockHttp.isUnregisterCalled() : "Servlet not unregistered with the mock service";
    }

    /**
     * Now the server should be made available at
     * http://SERVER:PORT/echoservlet and if it is not available after
     * some time, the test is failed anyway.
     *
     * The expectSuccess parameter indicated if this method should expect a working echoservlet or a non-working one.
     *
     * This method returns whether the echo servlet worked. So if you expect
     * it to work (and it does), true will be returned.
     * If you expect it to NOT work (and it doesn't), false will be returned.
     */
    private boolean waitForEchoServlet(boolean expectedResult) {
        BufferedReader bufReader = null;

        long startTimeMillis = System.currentTimeMillis();
        // make sure we iterate at least once
        boolean success = !expectedResult;
        try {
            while ((expectedResult != success) && (System.currentTimeMillis() < startTimeMillis + 30000)) {
                URL echoServletUrl = new URL("http://localhost:" + TestConstants.PORT + "/echoServlet?test");
                String echoString = null;
                try {
                    bufReader = new BufferedReader(new InputStreamReader(echoServletUrl.openStream()));
                    echoString = bufReader.readLine();
                } catch (IOException ioe) {
                    // let's wait and try again.
                }
                boolean resultFromServlet = (echoString != null) && echoString.equals("test");
                if (resultFromServlet == expectedResult) {
                    success = expectedResult;
                }
                if ((expectedResult != success)) {
                    Thread.sleep(100);
                }
            }
        }catch (MalformedURLException e) {
            e.printStackTrace();
            assert false : "No MalformedURLException expected";
        }
        catch (InterruptedException e) {
            e.printStackTrace();
            assert false : "No interruptedException expected";
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (bufReader != null) {
                try {
                    bufReader.close();
                }
                catch (Exception ex) {
                    // not much we can do
                }
            }
        }
        return success;
    }

}
