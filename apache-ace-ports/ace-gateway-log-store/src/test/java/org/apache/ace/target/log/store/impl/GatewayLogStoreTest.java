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
package org.apache.ace.target.log.store.impl;

import static org.apache.ace.test.utils.TestUtils.UNIT;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.ace.identification.Identification;
import org.apache.ace.log.AuditEvent;
import org.apache.ace.log.LogEvent;
import org.apache.ace.target.log.store.LogStore;
import org.apache.ace.target.log.store.impl.LogStoreImpl;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.service.log.LogService;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class GatewayLogStoreTest {
    private LogStoreImpl m_logStore;
    private File m_dir = null;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        m_dir  = File.createTempFile(LogStore.class.getName(), null);
        m_dir.delete();
        m_logStore = new LogStoreImpl(m_dir);
        TestUtils.configureObject(m_logStore, LogService.class);
        TestUtils.configureObject(m_logStore, Identification.class, TestUtils.createMockObjectAdapter(Identification.class, new Object() {
            @SuppressWarnings("unused")
            public String getID() {
                return "test";
            }
        }));
        m_logStore.start();
    }

    @AfterMethod(alwaysRun = true)
    protected void tearDown() throws IOException {
        m_logStore.stop();
        delete(m_dir);
        m_logStore = null;
    }

    @SuppressWarnings({ "serial", "unchecked" })
    @Test(groups = {UNIT})
    public void testLog() throws IOException {
        long[] ids = m_logStore.getLogIDs();
        assert ids.length == 1 : "New store should have only one id";
        List<String> events = new ArrayList<String>();
        events.add(m_logStore.put(AuditEvent.FRAMEWORK_STARTED, new Properties() {{put("test", "test");}}).toRepresentation());
        events.add(m_logStore.put(AuditEvent.BUNDLE_INSTALLED, new Properties() {{put("test", "test");}}).toRepresentation());
        events.add(m_logStore.put(AuditEvent.DEPLOYMENTADMIN_COMPLETE, new Properties() {{put("test", "test");}}).toRepresentation());
        ids = m_logStore.getLogIDs();
        assert ids.length == 1 : "Error free store should have only one id";
        long highest = m_logStore.getHighestID(ids[0]);
        assert  highest == 3 : "Store with 3 entries should have 3 as highest id but was: " + highest;
        List<String> result = new ArrayList<String>();
        for (LogEvent event : (List<LogEvent>) m_logStore.get(ids[0])) {
            result.add(event.toRepresentation());
        }
        assert result.equals(events) : "Events " + events + " should equal full log " + result;
        result = new ArrayList<String>();
        for (LogEvent event : (List<LogEvent>) m_logStore.get(ids[0], 1, highest)) {
            result.add(event.toRepresentation());
        }
        assert result.equals(events) : "Events " + events + " should equal full log " + result;
    }

    @Test(groups = {UNIT}, expectedExceptions = {IOException.class})
    public void testExceptionHandling() throws IOException {
        m_logStore.handleException(m_logStore.getLog(4711), new IOException("test"));
    }

    private static void delete(File target) {
        if (target.isDirectory()) {
            for (File child : target.listFiles()) {
                delete(child);
            }
        }
        target.delete();
    }
}
