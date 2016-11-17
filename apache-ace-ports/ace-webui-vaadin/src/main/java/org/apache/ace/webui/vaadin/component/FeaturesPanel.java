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
package org.apache.ace.webui.vaadin.component;

import java.util.List;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.Feature2DistributionAssociation;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.repository.FeatureRepository;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.ace.webui.vaadin.AssociationRemover;
import org.apache.ace.webui.vaadin.Associations;

import com.vaadin.data.Item;

/**
 * Provides an object panel for displaying features.
 */
public abstract class FeaturesPanel extends BaseObjectPanel<FeatureObject, FeatureRepository> {

    /**
     * Creates a new {@link FeaturesPanel} instance.
     * 
     * @param associations the assocation-holder object;
     * @param associationRemover the helper for removing associations.
     */
    public FeaturesPanel(Associations associations, AssociationRemover associationRemover) {
        super(associations, associationRemover, "Feature", UIExtensionFactory.EXTENSION_POINT_VALUE_FEATURE, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean doRemoveLeftSideAssociation(FeatureObject object, RepositoryObject other) {
        List<Artifact2FeatureAssociation> associations = object.getAssociationsWith((ArtifactObject) other);
        for (Artifact2FeatureAssociation association : associations) {
            m_associationRemover.removeAssociation(association);
        }
        return true;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean doRemoveRightSideAssociation(FeatureObject object, RepositoryObject other) {
        List<Feature2DistributionAssociation> associations = object.getAssociationsWith((DistributionObject) other);
        for (Feature2DistributionAssociation association : associations) {
            m_associationRemover.removeAssociation(association);
        }
        return true;
    }
    
    /**
     * {@inheritDoc}
     */
    protected void handleEvent(String topic, RepositoryObject entity, org.osgi.service.event.Event event) {
        FeatureObject feature = (FeatureObject) entity;
        if (FeatureObject.TOPIC_ADDED.equals(topic)) {
            add(feature);
        }
        if (FeatureObject.TOPIC_REMOVED.equals(topic)) {
            remove(feature);
        }
        if (FeatureObject.TOPIC_CHANGED.equals(topic) || RepositoryAdmin.TOPIC_STATUSCHANGED.equals(topic)) {
            update(feature);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isSupportedEntity(RepositoryObject entity) {
        return entity instanceof FeatureObject;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void populateItem(FeatureObject feature, Item item) {
        item.getItemProperty(WORKING_STATE_ICON).setValue(getWorkingStateIcon(feature));
        item.getItemProperty(OBJECT_NAME).setValue(feature.getName());
        item.getItemProperty(OBJECT_DESCRIPTION).setValue(feature.getDescription());
        item.getItemProperty(ACTIONS).setValue(createActionButtons(feature));
    }
}

