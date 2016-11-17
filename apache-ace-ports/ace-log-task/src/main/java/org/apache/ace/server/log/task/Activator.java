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
package org.apache.ace.server.log.task;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.log.LogSync;
import org.apache.ace.server.log.store.LogStore;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase implements ManagedServiceFactory {

    private static final String KEY_LOG_NAME = "name";
    
    private final Map<String, Component> m_instances = new HashMap<String, Component>();
    
    private volatile DependencyManager m_manager;
    private volatile LogService m_log;

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        m_manager = manager;
        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, "org.apache.ace.server.log.task.factory");
        manager.add(createComponent()
            .setInterface(ManagedServiceFactory.class.getName(), props)
            .setImplementation(this)
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
            );
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }

    public void deleted(String pid) {
        Component service = m_instances.remove(pid);
        if (service != null) {
            m_manager.remove(service);
        }
    }

    public String getName() {
        return "Log Sync Task Factory";
    }

    @SuppressWarnings("unchecked")
    public synchronized void updated(String pid, Dictionary dict) throws ConfigurationException {
        String name = (String) dict.get(KEY_LOG_NAME);
        if ((name == null) || "".equals(name)) {
            throw new ConfigurationException(KEY_LOG_NAME, "Log name has to be specified.");
        }

        Component comp;
        boolean created = false;

        synchronized (m_instances) {
            comp = m_instances.get(pid);
            if (comp != null) {
                m_log.log(LogService.LOG_INFO, "Ignoring configuration update because factory instance was already configured: " + name);
            } else {
                Properties props = new Properties();
                props.put(KEY_LOG_NAME, name);
                props.put("taskName", LogSyncTask.class.getName());
                props.put("description", "Syncs log (name=" + name + ") with a server.");
                
                String filter = "(&(" + Constants.OBJECTCLASS + "=" + LogStore.class.getName() + ")(name=" + name + "))";

                LogSyncTask service = new LogSyncTask(name, name);

                comp = m_manager.createComponent()
                                .setInterface(new String[] { Runnable.class.getName(), LogSync.class.getName() }, props)
                                .setImplementation(service)
                                .add(createServiceDependency().setService(ConnectionFactory.class).setRequired(true))
                                .add(createServiceDependency().setService(LogStore.class, filter).setRequired(true))
                                .add(createServiceDependency().setService(Discovery.class).setRequired(true))
                                .add(createServiceDependency().setService(LogService.class).setRequired(false));
                m_instances.put(pid, comp);

                created = true;
            }
        }
        
        if (created && (comp != null)) {
            m_manager.add(comp);
        }
    }
}