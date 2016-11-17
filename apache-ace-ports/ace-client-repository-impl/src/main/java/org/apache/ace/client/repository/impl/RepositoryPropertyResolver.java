package org.apache.ace.client.repository.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.helper.PropertyResolver;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.object.DistributionObject;

/**
 * This PropertyResolver first tries to resolve the key in the
 * current repository object. If not found, it looks for the key
 * in its children. 
 */
public class RepositoryPropertyResolver implements PropertyResolver {
	
	private final RepositoryObject m_repositoryObject;
	
	public RepositoryPropertyResolver(RepositoryObject repositoryObject) {
		m_repositoryObject = repositoryObject;
	}
	
    public String get(String key) {
		return get(key, m_repositoryObject);
    }

    private String get(String key, RepositoryObject ro) {
        // Is it in this object?
        String result = findKeyInObject(ro, key);
        if (result != null) {
            return result;
        }

        // Is it in one of the children?
        List<? extends RepositoryObject> children = getChildren(ro);
        for (RepositoryObject child : children) {
            result = findKeyInObject(child, key);
            if (result != null) {
                return result;
            }
        }

        // Not found yet? then continue to the next level recursively.
        for (RepositoryObject child : children) {
            result = get(key, child);
            if (result != null) {
                return result;
            }
        }
        return result;
    }

    protected List<? extends RepositoryObject> getChildren() {
    	return getChildren(m_repositoryObject);
    }
    
    protected List<? extends RepositoryObject> getChildren(RepositoryObject ob) {
        if (ob instanceof TargetObject) {
            return ((TargetObject) ob).getDistributions();
        }
        else if (ob instanceof DistributionObject) {
            return ((DistributionObject) ob).getFeatures();
        }
        else if (ob instanceof FeatureObject) {
            return ((FeatureObject) ob).getArtifacts();
        }
        return new ArrayList<RepositoryObject>();
    }

    private String findKeyInObject(RepositoryObject ro, String key) {
        String result;
        if ((result = ro.getAttribute(key)) != null) {
            return result;
        }
        if ((result = ro.getTag(key)) != null) {
            return result;
        }
        return null;
    }	
}
