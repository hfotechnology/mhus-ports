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
package org.apache.ace.client.repository.helper.user.impl;

import java.io.File;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.ace.client.repository.helper.ArtifactPreprocessor;
import org.apache.ace.client.repository.helper.ArtifactRecognizer;
import org.apache.ace.client.repository.helper.ArtifactResource;
import org.apache.ace.client.repository.helper.base.VelocityArtifactPreprocessor;
import org.apache.ace.client.repository.helper.user.UserAdminHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class UserHelperImpl implements ArtifactRecognizer, UserAdminHelper {

    // Injected by Dependency Manager
    private volatile ConnectionFactory m_connectionFactory;
    // Created in #start()
    private volatile VelocityArtifactPreprocessor m_artifactPreprocessor;

    public boolean canHandle(String mimetype) {
        return MIMETYPE.equals(mimetype);
    }

    public Map<String, String> extractMetaData(ArtifactResource artifact) throws IllegalArgumentException {
        Map<String, String> result = new HashMap<String, String>();
        result.put(ArtifactObject.KEY_PROCESSOR_PID, PROCESSOR);
        result.put(ArtifactObject.KEY_MIMETYPE, MIMETYPE);
        String name = new File(artifact.getURL().getFile()).getName();
        String key = ArtifactObject.KEY_ARTIFACT_NAME + "-";
        int idx = name.indexOf(key);
        if (idx > -1) {
            int endIdx = name.indexOf("-", idx + key.length());
            name = name.substring(idx + key.length(), (endIdx > -1) ? endIdx : (name.length() - getExtension(artifact).length()));
        }
        result.put(ArtifactObject.KEY_ARTIFACT_NAME, name);
        return result;
    }

    public String recognize(ArtifactResource artifact) {
        try {
            InputStream in = artifact.openStream();
            
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            Node root = doc.getFirstChild();
            if (!root.getNodeName().equals("roles")) {
                return null;
            }
            for (Node node = root.getFirstChild(); root != null; root = root.getNextSibling()) {
                if (!node.getNodeName().equals("group") && !node.getNodeName().equals("user") && !node.getNodeName().equals("#text")) {
                    return null;
                }
            }
            return MIMETYPE;
        }
        catch (Exception e) {
            // Does not matter.
        }

        return null;
    }

    public boolean canUse(ArtifactObject object) {
        return MIMETYPE.equals(object.getMimetype());
    }

    public Map<String, String> checkAttributes(Map<String, String> attributes) {
        // All necessary checks will be done by the constructor using getMandatoryAttributes.
        return attributes;
    }

    public <TYPE extends ArtifactObject> String getAssociationFilter(TYPE obj, Map<String, String> properties) {
        return "(" + ArtifactObject.KEY_ARTIFACT_NAME + "=" + obj.getAttribute(ArtifactObject.KEY_ARTIFACT_NAME) + ")";
    }

    public <TYPE extends ArtifactObject> int getCardinality(TYPE obj, Map<String, String> properties) {
        return Integer.MAX_VALUE;
    }

    public Comparator<ArtifactObject> getComparator() {
        return null;
    }

    public String[] getDefiningKeys() {
        return new String[] {ArtifactObject.KEY_ARTIFACT_NAME};
    }

    public String[] getMandatoryAttributes() {
        return new String[] {ArtifactObject.KEY_ARTIFACT_NAME};
    }

    public ArtifactPreprocessor getPreprocessor() {
        return m_artifactPreprocessor;
    }
    
    public String getExtension(ArtifactResource artifact) {
        return ".xml";
    }

    /**
     * Called by dependency manager upon start of this component.
     */
    protected void start() {
        m_artifactPreprocessor = new VelocityArtifactPreprocessor(m_connectionFactory);
    }

    /**
     * Called by dependency manager upon stopping of this component.
     */
    protected void stop() {
        m_artifactPreprocessor = null;
        
    }
}