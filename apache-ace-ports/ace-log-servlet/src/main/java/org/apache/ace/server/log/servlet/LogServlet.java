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
package org.apache.ace.server.log.servlet;

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.authentication.api.AuthenticationService;
import org.apache.ace.log.LogDescriptor;
import org.apache.ace.log.LogEvent;
import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.server.log.store.LogStore;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;

/**
 * This class acts as a servlet and handles the log protocol. This means a number of requests will be handled:
 *
 * The endpoint is configured externally, 'auditlog' is used as an example here.
 *
 * Querying existing audit log event id's:
 * http://host:port/auditlog/query - Return all known event ranges
 * http://host:port/auditlog/query?tid=myid&logid=123712636323 - Return the event range belonging to the specified target and log id
 *
 * Accepting new audit log events:
 * http://host:port/auditlog/send - Gets a new log event and puts it in the store, the event is inside the request and should be a formatted as done in <code>LogEvent.toRepresentation()</code>.
 *
 * Querying existing audit log events:
 * http://host:port/auditlog/receive - Return all known events
 * http://host:port/auditlog/receive?tid=myid - Return all known events belonging to the specified target ID
 * http://host:port/auditlog/receive?tid=myid&logid=2374623874 - Return all known events belonging to the specified target ID
 *
 * If the request is not correctly formatted or other problems arise error code <code>HttpServletResponse.SC_NOT_FOUND</code> will be sent in the response.
 */
public class LogServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // response mime type
    private static final String TEXT_MIMETYPE = "text/plain";

    // url path names available on the endpoint
    private static final String QUERY = "/query";
    private static final String SEND = "/send";
    private static final String RECEIVE = "/receive";

    // url parameter keys
    private static final String TARGETID_KEY = "tid";
    private static final String FILTER_KEY = "filter";
    private static final String LOGID_KEY = "logid";
    private static final String RANGE_KEY = "range";
    
    // injected by Dependency Manager
    private volatile LogService m_log;
    private volatile LogStore m_store;
    private volatile AuthenticationService m_authService;

    private final String m_name;
    private final boolean m_useAuth;

    public LogServlet(String name, boolean useAuth) {
        m_name = name;
        m_useAuth = useAuth;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        // 'send' calls are POST calls
        String path = request.getPathInfo();
        response.setContentType(TEXT_MIMETYPE);
        try {
            if (SEND.equals(path) && !handleSend(request.getInputStream())) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Could not construct a log event for all events received");
            }
        }
        catch (IOException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing received log events");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        // 'query' and 'receive' calls are GET calls

        String path = request.getPathInfo();
        String targetID = request.getParameter(TARGETID_KEY);
        String logID = request.getParameter(LOGID_KEY);
        String filter = request.getParameter(FILTER_KEY);
        String range = request.getParameter(RANGE_KEY);

        m_log.log(LogService.LOG_DEBUG, "Log servlet called: path(" + path + ") targetID(" + targetID + ") logID(" + logID + ") range( " + range + ") filter(" + filter +")");
        response.setContentType(TEXT_MIMETYPE);

        ServletOutputStream output = null;
        try {
            output = response.getOutputStream();
            if (QUERY.equals(path) && !handleQuery(targetID, logID, filter, output)) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Unable to interpret query");
            }
            else if (RECEIVE.equals(path) && !handleReceive(targetID, logID, range, filter, output)) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Unable to interpret receive query");
            }
        }
        catch (IOException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to process query");
        }
        finally {
            try {
                if (output != null) {
                    output.close();
                }
            }
            catch (Exception ex) {
                m_log.log(LogService.LOG_WARNING, "Exception trying to close stream after request: " + request.getRequestURL(), ex);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!authenticate(req)) {
            // Authentication failed; don't proceed with the original request...
            resp.sendError(SC_UNAUTHORIZED);
        } else {
            // Authentication successful, proceed with original request...
            super.service(req, resp);
        }
    }

    /**
     * Authenticates, if needed the user with the information from the given request.
     * 
     * @param request the request to obtain the credentials from, cannot be <code>null</code>.
     * @return <code>true</code> if the authentication was successful, <code>false</code> otherwise.
     */
    private boolean authenticate(HttpServletRequest request) {
        if (m_useAuth) {
            User user = m_authService.authenticate(request);
            if (user == null) {
                m_log.log(LogService.LOG_INFO, "Authentication failure!");
            }
            return (user != null);
        }
        return true;
    }

    // Handle a call to the query 'command'
    protected boolean handleQuery(String targetID, String logID, String filter, ServletOutputStream output) throws IOException {
        if ((targetID != null) && (logID != null)) {
            // target and log id are specified, return only the range that matches these id's
            LogDescriptor range = m_store.getDescriptor(targetID, Long.parseLong(logID));
            output.print(range.toRepresentation());
            return true;
        }
        else if ((targetID == null) && (logID == null)) {
            // no target or log id has been specified, return all ranges
            List<LogDescriptor> ranges = m_store.getDescriptors();
            for (LogDescriptor range : ranges) {
                output.print(range.toRepresentation() + "\n");
            }
            return true;
        }
        return false;
    }

    // Handle a call to the receive 'command'
    protected boolean handleReceive(String targetID, String logID, String range, String filter, ServletOutputStream output) throws IOException {
        if ((targetID != null) && (logID != null)) {
            // target and log id are specified, return only the events that are in the range that matches these id's
            if (range != null) {
                LogDescriptor storeDescriptor = m_store.getDescriptor(targetID, Long.parseLong(logID));
                outputRange(output, new LogDescriptor(storeDescriptor.getTargetID(), storeDescriptor.getLogID(), new SortedRangeSet(range)));
            }
            else {
                outputRange(output, m_store.getDescriptor(targetID, Long.parseLong(logID)));
            }
            return true;
        }
        else if ((targetID != null) && (logID == null)) {
            // target id is specified, log id is not, return all events that belong to the specified target id
            List<LogDescriptor> descriptors = m_store.getDescriptors(targetID);
            for (LogDescriptor descriptor : descriptors) {
                outputRange(output, descriptor);
            }
            return true;
        }
        else if ((targetID == null) && (logID == null)) {
            // no target or log id has been specified, return all events
            List<LogDescriptor> descriptors = m_store.getDescriptors();
            for (LogDescriptor descriptor : descriptors) {
                outputRange(output, descriptor);
            }
            return true;
        }
        return false;
    }

    // Handle a call to the send 'command'
    protected boolean handleSend(ServletInputStream input) throws IOException {
        List<LogEvent> events = new ArrayList<LogEvent>();
        boolean success = true;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(input));

            String eventString;
            while ((eventString = reader.readLine()) != null) {
                try {
                    m_log.log(LogService.LOG_DEBUG, "Log event received: '" + eventString +"'");
                    LogEvent event = new LogEvent(eventString);
                    events.add(event);
                }
                catch (IllegalArgumentException iae) {
                    success = false;
                    m_log.log(LogService.LOG_WARNING, "Could not construct LogEvent from string: '" + eventString + "'");
                }
            }
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (Exception ex) {
                    // not much we can do
                }
            }
        }
        m_store.put(events);
        return success;
    }

    // print string representations of all events in the specified range to the specified output
    private void outputRange(ServletOutputStream output, LogDescriptor range) throws IOException {
        List<LogEvent> events = m_store.get(range);
        for (LogEvent event : events) {
            output.print(event.toRepresentation() + "\n");
        }
    }

    // send an error response
    private void sendError(HttpServletResponse response, int statusCode, String description) {
        m_log.log(LogService.LOG_WARNING, "Log request failed: " + description);
        try {
            response.sendError(statusCode, description);
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_WARNING, "Unable to send error response", e);
        }
    }

    @Override
    public String getServletInfo() {
        return "Log Endpoint (channel=" + m_name + ")";
    }
}