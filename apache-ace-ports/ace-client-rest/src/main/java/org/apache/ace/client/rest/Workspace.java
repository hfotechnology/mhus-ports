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
package org.apache.ace.client.rest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;

import org.apache.ace.authentication.api.AuthenticationService;
import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.repository.Artifact2FeatureAssociationRepository;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.Distribution2TargetAssociationRepository;
import org.apache.ace.client.repository.repository.DistributionRepository;
import org.apache.ace.client.repository.repository.Feature2DistributionAssociationRepository;
import org.apache.ace.client.repository.repository.FeatureRepository;
import org.apache.ace.client.repository.repository.TargetRepository;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetRepository;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

public class Workspace {
    static final String ARTIFACT = "artifact";
    static final String ARTIFACT2FEATURE = "artifact2feature";
    static final String FEATURE = "feature";
    static final String FEATURE2DISTRIBUTION = "feature2distribution";
    static final String DISTRIBUTION = "distribution";
    static final String DISTRIBUTION2TARGET = "distribution2target";
    static final String TARGET = "target";
    
    private final String m_sessionID;
    private final URL m_repositoryURL;
    private final URL m_obrURL;
    private final String m_customerName;
    private final String m_storeRepositoryName;
    private final String m_distributionRepositoryName;
    private final String m_deploymentRepositoryName;
    private final String m_serverUser;
    private final boolean m_useAuthentication;
    
    private volatile AuthenticationService m_authenticationService;
    private volatile DependencyManager m_manager;
    private volatile RepositoryAdmin m_repositoryAdmin;
    private volatile ArtifactRepository m_artifactRepository;
    private volatile FeatureRepository m_featureRepository;
    private volatile DistributionRepository m_distributionRepository;
    private volatile StatefulTargetRepository m_statefulTargetRepository;
    private volatile Artifact2FeatureAssociationRepository m_artifact2FeatureAssociationRepository;
    private volatile Feature2DistributionAssociationRepository m_feature2DistributionAssociationRepository;
    private volatile Distribution2TargetAssociationRepository m_distribution2TargetAssociationRepository;
    private volatile UserAdmin m_userAdmin;
    private volatile LogService m_log;

    public Workspace(String sessionID, String repositoryURL, String obrURL, String customerName, String storeRepositoryName, String distributionRepositoryName, String deploymentRepositoryName, boolean useAuthentication, String serverUser) throws MalformedURLException {
        m_sessionID = sessionID;
        m_repositoryURL = new URL(repositoryURL);
        m_obrURL = new URL(obrURL);
        m_customerName = customerName;
        m_storeRepositoryName = storeRepositoryName;
        m_distributionRepositoryName = distributionRepositoryName;
        m_deploymentRepositoryName = deploymentRepositoryName;
        m_useAuthentication = useAuthentication;
        m_serverUser = serverUser;
    }
    
    /**
     * @return the session ID of this workspace, never <code>null</code>.
     */
    public String getSessionID() {
        return m_sessionID;
    }
    
    private void addSessionDependency(Component component, Class service, boolean isRequired) {
        component.add(m_manager.createServiceDependency()
            .setService(service, "(" + SessionFactory.SERVICE_SID + "=" + m_sessionID + ")")
            .setRequired(isRequired)
            .setInstanceBound(true)
        );
    }
    
    private void addDependency(Component component, Class service, boolean isRequired) {
        component.add(m_manager.createServiceDependency()
            .setService(service)
            .setRequired(isRequired)
            .setInstanceBound(true)
        );
    }
    
    public void init(Component component) {
        addSessionDependency(component, RepositoryAdmin.class, true);
        addSessionDependency(component, ArtifactRepository.class, true);
        addSessionDependency(component, FeatureRepository.class, true);
        addSessionDependency(component, DistributionRepository.class, true);
        addSessionDependency(component, TargetRepository.class, true);
        addSessionDependency(component, StatefulTargetRepository.class, true);
        addSessionDependency(component, Artifact2FeatureAssociationRepository.class, true);
        addSessionDependency(component, Feature2DistributionAssociationRepository.class, true);
        addSessionDependency(component, Distribution2TargetAssociationRepository.class, true);
        addDependency(component, AuthenticationService.class, m_useAuthentication);
        addDependency(component, UserAdmin.class, true);
        addDependency(component, LogService.class, false);
    }
    
    public void start() {
    }
    
    public void destroy() {
    }
    
    public boolean login(HttpServletRequest request) {
        try {
            final User user;
            if (m_useAuthentication) {
                // Use the authentication service to authenticate the given request...
                user = m_authenticationService.authenticate(request);
            } else {
                // Use the "hardcoded" user to login with...
                user = m_userAdmin.getUser("username", m_serverUser);
            }
            
            if (user == null) {
                // No user obtained through request/fallback scenario; login failed...
                return false;
            }

            RepositoryAdminLoginContext context = m_repositoryAdmin.createLoginContext(user);
            
            context.setObrBase(m_obrURL)
                .add(context.createShopRepositoryContext()
                    .setLocation(m_repositoryURL)
                    .setCustomer(m_customerName)
                    .setName(m_storeRepositoryName)
                    .setWriteable())
                .add(context.createTargetRepositoryContext()
                    .setLocation(m_repositoryURL)
                    .setCustomer(m_customerName)
                    .setName(m_distributionRepositoryName)
                    .setWriteable())
                .add(context.createDeploymentRepositoryContext()
                    .setLocation(m_repositoryURL)
                    .setCustomer(m_customerName)
                    .setName(m_deploymentRepositoryName)
                    .setWriteable());

            m_repositoryAdmin.login(context);
            m_repositoryAdmin.checkout();
        }
        catch (IOException e) {
            e.printStackTrace();
            m_log.log(LogService.LOG_ERROR, "Could not login and checkout. Workspace will probably not work correctly.", e);
        }
        
        return true;
    }
    
    public void checkout() throws IOException {
        m_repositoryAdmin.checkout();
    }

    public void commit() throws IOException {
        m_repositoryAdmin.commit();
    }

    public RepositoryObject getRepositoryObject(String entityType, String entityId) {
        return getObjectRepository(entityType).get(entityId);
    }

    public List<RepositoryObject> getRepositoryObjects(String entityType) {
        List list = getObjectRepository(entityType).get();
        if (list != null) {
            return list;
        }
        else {
            return Collections.EMPTY_LIST;
        }
    }

    public RepositoryObject addRepositoryObject(String entityType, Map<String, String> attributes, Map<String, String> tags) throws IllegalArgumentException {
        if (TARGET.equals(entityType)) {
            return ((StatefulTargetRepository) getObjectRepository(TARGET)).preregister(attributes, tags);
        }
        else {
            if (ARTIFACT2FEATURE.equals(entityType) || FEATURE2DISTRIBUTION.equals(entityType) || DISTRIBUTION2TARGET.equals(entityType)) {

                String leftAttribute = attributes.get("left");
                String rightAttribute = attributes.get("right");

                RepositoryObject left = null;
                if(leftAttribute != null) {
                    left = getLeft(entityType, leftAttribute);
                }

                RepositoryObject right = null;
                if(rightAttribute != null) {
                    right = getRight(entityType, rightAttribute);
                }


                if (left != null) {
                    if (left instanceof StatefulTargetObject) {
                        if (((StatefulTargetObject) left).isRegistered()) {
                            attributes.put("leftEndpoint", ((StatefulTargetObject) left).getTargetObject().getAssociationFilter(attributes));
                        }
                    }
                    else {
                        attributes.put("leftEndpoint", left.getAssociationFilter(attributes));
                    }
                }
                if (right != null) {
                    if (right instanceof StatefulTargetObject) {
                        if (((StatefulTargetObject) right).isRegistered()) {
                            attributes.put("rightEndpoint", ((StatefulTargetObject) right).getTargetObject().getAssociationFilter(attributes));
                        }
                    }
                    else {
                        attributes.put("rightEndpoint", right.getAssociationFilter(attributes));
                    }
                }
            }
            return getObjectRepository(entityType).create(attributes, tags);
        }
    }
    
    /**
     * Approves a given stateful target object.
     * 
     * @param targetObject the target object to approve, cannot be <code>null</code>.
     * @return the approved stateful target object, cannot be <code>null</code>.
     */
    public StatefulTargetObject approveTarget(StatefulTargetObject targetObject) {
        targetObject.approve();
        return targetObject;
    }

    /**
     * Registers a given stateful target object.
     * 
     * @param targetObject the target object to register, cannot be <code>null</code>.
     * @return the registered stateful target object, can be <code>null</code> only if the given target object is already registered.
     */
    public StatefulTargetObject registerTarget(StatefulTargetObject targetObject) {
        if (targetObject.isRegistered()) {
            return null;
        }
        targetObject.register();
        return targetObject;
    }
    
    public void updateObjectWithData(String entityType, String entityId, RepositoryValueObject valueObject) {
        RepositoryObject repositoryObject = getRepositoryObject(entityType, entityId);
        // first handle the attributes
        for (Entry<String, String> attribute : valueObject.attributes.entrySet()) {
            String key = attribute.getKey();
            String value = attribute.getValue();
            // only add/update the attribute if it actually changed
            if (!value.equals(repositoryObject.getAttribute(key))) {
                repositoryObject.addAttribute(key, value);
            }
        }
        Enumeration<String> keys = repositoryObject.getAttributeKeys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (!valueObject.attributes.containsKey(key)) {
                // TODO since we cannot remove keys right now, we null them
                repositoryObject.addAttribute(key, null);
            }
        }
        if (ARTIFACT2FEATURE.equals(entityType) || FEATURE2DISTRIBUTION.equals(entityType) || DISTRIBUTION2TARGET.equals(entityType)) {
            String leftAttribute = repositoryObject.getAttribute("left");
            String rightAttribute = repositoryObject.getAttribute("right");

            RepositoryObject left = null;
            if (leftAttribute != null) {
                left = getLeft(entityType, leftAttribute);
            }

            RepositoryObject right = null;
            if (rightAttribute != null) {
                right = getRight(entityType, rightAttribute);
            }

            if (left != null) {
                if (left instanceof StatefulTargetObject) {
                    if (((StatefulTargetObject) left).isRegistered()) {
                        repositoryObject.addAttribute("leftEndpoint", ((StatefulTargetObject) left).getTargetObject().getAssociationFilter(getAttributes(((StatefulTargetObject) left).getTargetObject())));
                    }
                }
                else {
                    repositoryObject.addAttribute("leftEndpoint", left.getAssociationFilter(getAttributes(left)));
                }
            }
            if (right != null) {
                if (right instanceof StatefulTargetObject) {
                    if (((StatefulTargetObject) right).isRegistered()) {
                        repositoryObject.addAttribute("rightEndpoint", ((StatefulTargetObject) right).getTargetObject().getAssociationFilter(getAttributes(((StatefulTargetObject) right).getTargetObject())));
                    }
                }
                else {
                    repositoryObject.addAttribute("rightEndpoint", right.getAssociationFilter(getAttributes(right)));
                }
            }
        }
        // now handle the tags in a similar way
        for (Entry<String, String> attribute : valueObject.tags.entrySet()) {
            String key = attribute.getKey();
            String value = attribute.getValue();
            // only add/update the tag if it actually changed
            if (!value.equals(repositoryObject.getTag(key))) {
                repositoryObject.addTag(key, value);
            }
        }
        keys = repositoryObject.getTagKeys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (!valueObject.tags.containsKey(key)) {
                // TODO since we cannot remove keys right now, we null them
                repositoryObject.addTag(key, null);
            }
        }
    }

    private Map getAttributes(RepositoryObject object) {
        Map result = new HashMap();
        for (Enumeration<String> keys = object.getAttributeKeys(); keys.hasMoreElements();) {
            String key = keys.nextElement();
            result.put(key, object.getAttribute(key));
        }
        return result;
    }

    public RepositoryObject getLeft(String entityType, String entityId) {
        if (ARTIFACT2FEATURE.equals(entityType)) {
            return getObjectRepository(ARTIFACT).get(entityId);
        }
        else if (FEATURE2DISTRIBUTION.equals(entityType)) {
            return getObjectRepository(FEATURE).get(entityId);
        }
        else if (DISTRIBUTION2TARGET.equals(entityType)) {
            return getObjectRepository(DISTRIBUTION).get(entityId);
        }
        else {
            // throws an exception in case of an illegal type!
            getObjectRepository(entityType);
        }
        return null;
    }

    public RepositoryObject getRight(String entityType, String entityId) {
        if (ARTIFACT2FEATURE.equals(entityType)) {
            return getObjectRepository(FEATURE).get(entityId);
        }
        else if (FEATURE2DISTRIBUTION.equals(entityType)) {
            return getObjectRepository(DISTRIBUTION).get(entityId);
        }
        else if (DISTRIBUTION2TARGET.equals(entityType)) {
            return getObjectRepository(TARGET).get(entityId);
        }
        else {
            // throws an exception in case of an illegal type!
            getObjectRepository(entityType);
        }
        return null;
    }
    
    public void deleteRepositoryObject(String entityType, String entityId) {
        ObjectRepository objectRepository = getObjectRepository(entityType);
        RepositoryObject repositoryObject = objectRepository.get(entityId);
        // ACE-239: avoid null entities being passed in...
        if (repositoryObject == null) {
            throw new IllegalArgumentException("Could not find repository object!");
        }

        objectRepository.remove(repositoryObject);
    }

    private ObjectRepository getObjectRepository(String entityType) {
        if (ARTIFACT.equals(entityType)) {
            return m_artifactRepository;
        }
        if (ARTIFACT2FEATURE.equals(entityType)) {
            return m_artifact2FeatureAssociationRepository;
        }
        if (FEATURE.equals(entityType)) {
            return m_featureRepository;
        }
        if (FEATURE2DISTRIBUTION.equals(entityType)) {
            return m_feature2DistributionAssociationRepository;
        }
        if (DISTRIBUTION.equals(entityType)) {
            return m_distributionRepository;
        }
        if (DISTRIBUTION2TARGET.equals(entityType)) {
            return m_distribution2TargetAssociationRepository;
        }
        if (TARGET.equals(entityType)) {
            return m_statefulTargetRepository;
        }
        throw new IllegalArgumentException("Unknown entity type: " + entityType);
    }
}
