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
package org.apache.ace.repository.servlet;

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Dictionary;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.authentication.api.AuthenticationService;
import org.apache.ace.range.SortedRangeSet;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;

/**
 * Base class for the repository servlets. Both the repository and the repository replication
 * servlets work in a similar way, so the specifics were factored out of this base class and
 * put in two subclasses.
 */
public abstract class RepositoryServletBase extends HttpServlet implements ManagedService {

    /** A boolean denoting whether or not authentication is enabled. */
    private static final String KEY_USE_AUTHENTICATION = "authentication.enabled";

    private static final int COPY_BUFFER_SIZE = 1024;
    
    private static final String QUERY = "/query";
    
    protected static final String TEXT_MIMETYPE = "text/plain";
    protected static final String BINARY_MIMETYPE = "application/octet-stream";

    // injected by Dependency Manager
    private volatile DependencyManager m_dm; 
    private volatile AuthenticationService m_authService;

    private volatile boolean m_useAuth = false;
    
    protected volatile BundleContext m_context;
    protected volatile LogService m_log;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();
        String customer = request.getParameter("customer");
        String name = request.getParameter("name");
        String filter = request.getParameter("filter");
        String version = request.getParameter("version");

        if (QUERY.equals(path)) {
            // both repositories have a query method
            if (filter != null) {
                if ((name == null) && (customer == null)) {
                    handleQuery(filter, response);
                }
                else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Specify either a filter or customer and/or name, but not both.");
                }
            }
            else {
                if ((name != null) && (customer != null)) {
                    handleQuery("(&(customer=" + customer + ")(name=" + name + "))", response);
                }
                else if (name != null) {
                    handleQuery("(name=" + name + ")", response);
                }
                else if (customer != null) {
                    handleQuery("(customer=" + customer + ")", response);
                }
                else {
                    handleQuery(null, response);
                }
            }
        }
        else if (getCheckoutCommand().equals(path)) {
            // and both have a checkout, only it's named differently
            if ((name != null) && (customer != null) && (version != null)) {
                handleCheckout(customer, name, Long.parseLong(version), response);
            }
        }
        else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * Returns the name of the "checkout" command.
     */
    protected abstract String getCheckoutCommand();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();
        String customer = request.getParameter("customer");
        String name = request.getParameter("name");
        String version = request.getParameter("version");

        if (getCommitCommand().equals(path)) {
            // and finally, both have a commit, only it's named differently
            if ((name != null) && (customer != null) && (version != null)) {
                handleCommit(customer, name, Long.parseLong(version), request.getInputStream(), response);
            }
            else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Name, customer and version should all be specified.");
            }
        }
        else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * Returns the name of the "commit" command.
     */
    protected abstract String getCommitCommand();

    /**
     * Handles a query command and sends back the response.
     */
    private void handleQuery(String filter, HttpServletResponse response) throws IOException {
        try {
            ServiceReference[] refs = getRepositories(filter);
            StringBuffer result = new StringBuffer();
            if (refs != null) {
                for (ServiceReference ref : refs) {
                    result.append((String) ref.getProperty("customer"));
                    result.append(',');
                    result.append((String) ref.getProperty("name"));
                    result.append(',');
                    result.append(getRange(ref).toRepresentation());
                    result.append('\n');
                }
            }
            response.setContentType(TEXT_MIMETYPE);
            response.getWriter().print(result.toString());
        }
        catch (IOException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not retrieve version range for repository: " + e.getMessage());
        }
        catch (InvalidSyntaxException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid filter syntax: " + e.getMessage());
        }
    }

    /**
     * Implement this by asking the right repository for a range of available versions.
     *
     * @param ref reference to the repository service you need to dereference
     * @return a sorted range set
     * @throws java.io.IOException if the range cannot be obtained
     */
    protected abstract SortedRangeSet getRange(ServiceReference ref) throws IOException;

    /**
     * Returns a list of repositories that match the specified filter condition.
     *
     * @param filter the filter condition
     * @return an array of service references
     * @throws InvalidSyntaxException if the filter condition is invalid
     */
    protected abstract ServiceReference[] getRepositories(String filter) throws InvalidSyntaxException;

    /**
     * Called by Dependency Manager upon initialization of this component.
     * 
     * @param comp the component to initialize, cannot be <code>null</code>.
     */
    protected void init(Component comp) {
        comp.add(m_dm.createServiceDependency()
            .setService(AuthenticationService.class)
            .setRequired(m_useAuth)
            .setInstanceBound(true)
            );
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

    /**
     * Handles a commit command and sends back the response.
     */
    private void handleCommit(String customer, String name, long version, InputStream data, HttpServletResponse response) throws IOException {
        try {
            ServiceReference[] refs = getRepositories("(&(customer=" + customer + ")(name=" + name + "))");
            if ((refs != null) && (refs.length == 1)) {
                ServiceReference ref = refs[0];
                try {
                    if (!doCommit(ref, version, data)) {
                        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not commit");
                    } else {
                        response.sendError(HttpServletResponse.SC_OK);
                    }
                }
                catch (IllegalArgumentException e) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid version");
                }
                catch (IllegalStateException e) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot commit, not the master repository");
                }
            }
        }
        catch (IOException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "I/O exception: " + e.getMessage());
        }
        catch (InvalidSyntaxException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid filter syntax: " + e.getMessage());
        }
    }

    /**
     * Commit or put the data into the repository.
     *
     * @param ref reference to the repository service
     * @param version the version
     * @param data the data
     * @return <code>true</code> if successful
     * @throws IllegalArgumentException
     * @throws java.io.IOException
     */
    protected abstract boolean doCommit(ServiceReference ref, long version, InputStream data) throws IllegalArgumentException, IOException;

    /**
     * Handles a checkout command and returns the response.
     */
    private void handleCheckout(String customer, String name, long version, HttpServletResponse response) throws IOException {
        try {
            ServiceReference[] refs = getRepositories("(&(customer=" + customer + ")(name=" + name + "))");
            if ((refs != null) && (refs.length == 1)) {
                ServiceReference ref = refs[0];
                response.setContentType(BINARY_MIMETYPE);
                InputStream data = doCheckout(ref, version);
                if (data == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Requested version does not exist: " + version);
                } else {
                    copy(data, response.getOutputStream());
                }
            }
            else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, ((refs == null) ? "Could not find repository " : "Multiple repositories found ") + " for customer " + customer + ", name " + name);
            }
        }
        catch (IOException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "I/O exception: " + e.getMessage());
        }
        catch (InvalidSyntaxException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid filter syntax: " + e.getMessage());
        }
    }

    /**
     * Checkout or get data from the repository.
     *
     * @param ref reference to the repository service
     * @param version the version
     * @return the data
     * @throws IllegalArgumentException
     * @throws java.io.IOException
     */
    protected abstract InputStream doCheckout(ServiceReference ref, long version) throws IllegalArgumentException, IOException;

    /**
     * Copies data from an input stream to an output stream.
     * @param in the input
     * @param out the output
     * @throws java.io.IOException if copying fails
     */
    private void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int bytes = in.read(buffer);
        while (bytes != -1) {
            out.write(buffer, 0, bytes);
            bytes = in.read(buffer);
        }
    }

    public void updated(Dictionary settings) throws ConfigurationException {
        if (settings != null) {
            String useAuthString = (String) settings.get(KEY_USE_AUTHENTICATION);
            if (useAuthString == null
                || !("true".equalsIgnoreCase(useAuthString) || "false".equalsIgnoreCase(useAuthString))) {
                throw new ConfigurationException(KEY_USE_AUTHENTICATION, "Missing or invalid value!");
            }
            boolean useAuth = Boolean.parseBoolean(useAuthString);

            m_useAuth = useAuth;
        }
        else {
            m_useAuth = false;
        }
    }
}