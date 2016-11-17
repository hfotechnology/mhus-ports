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
package org.apache.ace.server.log.store.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.ace.log.LogDescriptor;
import org.apache.ace.log.LogEvent;
import org.apache.ace.range.Range;
import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.server.log.store.LogStore;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

/**
 * A simple implementation of the LogStore interface.
 */
public class LogStoreImpl implements LogStore {

    private volatile EventAdmin m_eventAdmin; /* Injected by dependency manager */

    // the dir to store logs in - init is in the start method
    private final File m_dir;
    private final String m_name;

    public LogStoreImpl(File baseDir, String name) {
        m_name = name;
        m_dir = new File(baseDir, "store");
    }

    /*
     * init the dir in which to store logs in - thows IllegalArgumentException
     * if we can't get it.
     */
    protected void start() throws IOException {
        if (!m_dir.isDirectory() && !m_dir.mkdirs()) {
            throw new IllegalArgumentException("Need valid dir");
        }
    }

    /**
     * @see org.apache.ace.server.log.store.LogStore#get(org.apache.ace.log.LogDescriptor)
     */
    public synchronized List<LogEvent> get(LogDescriptor descriptor)
            throws IOException {
        final List<LogEvent> result = new ArrayList<LogEvent>();
        final SortedRangeSet set = descriptor.getRangeSet();
        BufferedReader in = null;
        try {
            File log = new File(new File(m_dir,
                    targetIDToFilename(descriptor.getTargetID())),
                    String.valueOf(descriptor.getLogID()));
            if (!log.isFile()) {
                return result;
            }
            in = new BufferedReader(new FileReader(log));
            String file = log.getAbsolutePath();
            long counter = 0;
            for (String line = in.readLine(); line != null; line = in
                    .readLine()) {
                LogEvent event = new LogEvent(line);
                long id = event.getID();
                if ((counter != -1) && ++counter == id) {

                } else {
                    counter = -1;
                }
                if (set.contains(id)) {
                    result.add(event);
                }
            }
            if (counter < 1) {
                m_fileToID.remove(file);
            } else {
                m_fileToID.put(file, counter);
            }
        } 
        finally {
            if (in != null) {
                try {
                    in.close();
                } 
                catch (Exception ex) {
                    // Not much we can do
                }
            }
        }
        return result;
    }

    private final Map<String, Long> m_fileToID = new HashMap<String, Long>();

    /**
     * @see org.apache.ace.server.log.store.LogStore#getDescriptor(String, long)
     */
    public synchronized LogDescriptor getDescriptor(String targetID, long logID)
            throws IOException {
        Long high = m_fileToID.get(new File(new File(m_dir,
                targetIDToFilename(targetID)), String.valueOf(logID))
                .getAbsolutePath());
        if (high != null) {
            Range r = new Range(1, high);
            return new LogDescriptor(targetID, logID, new SortedRangeSet(
                    r.toRepresentation()));
        }
        List<LogEvent> events = get(new LogDescriptor(targetID, logID,
                SortedRangeSet.FULL_SET));

        long[] idsArray = new long[events.size()];
        int i = 0;
        for (LogEvent e : events) {
            idsArray[i++] = e.getID();
        }
        return new LogDescriptor(targetID, logID, new SortedRangeSet(idsArray));
    }

    /**
     * @see org.apache.ace.server.log.store.LogStore#getDescriptors(String)
     */
    public List<LogDescriptor> getDescriptors(String targetID)
            throws IOException {
        File dir = new File(m_dir, targetIDToFilename(targetID));
        List<LogDescriptor> result = new ArrayList<LogDescriptor>();
        if (!dir.isDirectory()) {
            return result;
        }

        for (String name : notNull(dir.list())) {
            result.add(getDescriptor(targetID, Long.parseLong(name)));
        }

        return result;
    }

    /**
     * @see org.apache.ace.server.log.store.LogStore#getDescriptors()
     */
    public List<LogDescriptor> getDescriptors() throws IOException {
        List<LogDescriptor> result = new ArrayList<LogDescriptor>();
        for (String name : notNull(m_dir.list())) {
            result.addAll(getDescriptors(filenameToTargetID(name)));
        }
        return result;
    }

    /**
     * @see org.apache.ace.server.log.store.LogStore#put(java.util.List)
     */
    public void put(List<LogEvent> events) throws IOException {
        Map<String, Map<Long, List<LogEvent>>> sorted = sort(events);
        for (String targetID : sorted.keySet()) {
            for (Long logID : sorted.get(targetID).keySet()) {
                put(targetID, logID, sorted.get(targetID).get(logID));
            }
        }
    }

    /**
     * Add a list of events to the log of the given ids.
     * 
     * @param targetID
     *            the id of the target to append to its log.
     * @param logID
     *            the id of the given target log.
     * @param list
     *            a list of events to store.
     * @throws java.io.IOException
     *             in case of any error.
     */
    protected synchronized void put(String targetID, Long logID,
            List<LogEvent> list) throws IOException {
        if ((list == null) || (list.size() == 0)) {
            // nothing to add, so return
            return;
        }
        // we actually need to distinguish between two scenarios here:
        // 1. we can append events at the end of the existing file
        // 2. we need to insert events in the existing file (meaning we have to
        // rewrite basically the whole file)
        String file = new File(new File(m_dir, targetIDToFilename(targetID)),
                String.valueOf(logID)).getAbsolutePath();
        Long highest = m_fileToID.get(file);
        boolean cached = false;
        if (highest != null) {
            if (highest.longValue() + 1 == list.get(0).getID()) {
                cached = true;
            }
        }
        List<LogEvent> events = null;
        if (!cached) {
            events = get(new LogDescriptor(targetID, logID,
                    SortedRangeSet.FULL_SET));

            // remove duplicates first
            list.removeAll(events);
        }

        if (list.size() == 0) {
            // nothing to add anymore, so return
            return;
        }

        PrintWriter out = null;
        try {
            File dir = new File(m_dir, targetIDToFilename(targetID));
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("Unable to create backup store.");
            }
            if (cached
                    || ((events.size() == 0) || (events.get(events.size() - 1)
                            .getID() < list.get(0).getID()))) {
                // we can append to the existing file
                out = new PrintWriter(new FileWriter(new File(dir,
                        logID.toString()), true));
            } else {
                // we have to merge the lists
                list.addAll(events);
                // and sort
                Collections.sort(list);
                out = new PrintWriter(new FileWriter(new File(dir,
                        logID.toString())));
            }
            long high = 0;
            for (LogEvent event : list) {
                out.println(event.toRepresentation());
                if (high < event.getID()) {
                    high = event.getID();
                } else {
                    high = Long.MAX_VALUE;
                }
                // send (eventadmin)event about a new (log)event being stored
                Dictionary props = new Hashtable();
                props.put(LogStore.EVENT_PROP_LOGNAME, m_name);
                props.put(LogStore.EVENT_PROP_LOG_EVENT, event);
                m_eventAdmin.postEvent(new Event(LogStore.EVENT_TOPIC, props));
            }
            if ((cached) && (high < Long.MAX_VALUE)) {
                m_fileToID.put(file, new Long(high));
            } else {
                m_fileToID.remove(file);
            }
        } 
        finally {
            try {
                out.close();
            } 
            catch (Exception ex) {
                // Not much we can do
            }
        }
    }

    /**
     * Sort the given list of events into a map of maps according to the
     * targetID and the logID of each event.
     * 
     * @param events
     *            a list of events to sort.
     * @return a map of maps that maps target ids to a map that maps log ids to
     *         a list of events that have those ids.
     */
    @SuppressWarnings("boxing")
    protected Map<String, Map<Long, List<LogEvent>>> sort(List<LogEvent> events) {
        Map<String, Map<Long, List<LogEvent>>> result = new HashMap<String, Map<Long, List<LogEvent>>>();
        for (LogEvent event : events) {
            Map<Long, List<LogEvent>> target = result
                    .get(event.getTargetID());

            if (target == null) {
                target = new HashMap<Long, List<LogEvent>>();
                result.put(event.getTargetID(), target);
            }

            List<LogEvent> list = target.get(event.getLogID());
            if (list == null) {
                list = new ArrayList<LogEvent>();
                target.put(event.getLogID(), list);
            }

            list.add(event);
        }
        return result;
    }

    /*
     * throw IOException in case the target is null else return the target.
     */
    private <T> T notNull(T target) throws IOException {
        if (target == null) {
            throw new IOException(
                    "Unknown IO error while trying to access the store.");
        }
        return target;
    }

    private static String filenameToTargetID(String filename) {
        byte[] bytes = new byte[filename.length() / 2];
        for (int i = 0; i < (filename.length() / 2); i++) {
            String hexValue = filename.substring(i * 2, (i + 1) * 2);
            bytes[i] = Byte.parseByte(hexValue, 16);
        }

        String result = null;
        try {
            result = new String(bytes, "UTF-8");
        } 
        catch (UnsupportedEncodingException e) {
            // UTF-8 is a mandatory encoding; this will never happen.
        }
        return result;
    }

    private static String targetIDToFilename(String targetID) {
        StringBuilder result = new StringBuilder();

        try {
            for (Byte b : targetID.getBytes("UTF-8")) {
                String hexValue = Integer.toHexString(b.intValue());
                if (hexValue.length() % 2 == 0) {
                    result.append(hexValue);
                } else {
                    result.append('0').append(hexValue);
                }
            }
        } 
        catch (UnsupportedEncodingException e) {
            // UTF-8 is a mandatory encoding; this will never happen.
        }

        return result.toString();
    }
}