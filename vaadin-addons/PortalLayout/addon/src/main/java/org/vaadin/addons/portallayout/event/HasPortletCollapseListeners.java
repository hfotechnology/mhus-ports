package org.vaadin.addons.portallayout.event;

/**
 *
 */
public interface HasPortletCollapseListeners {

    void addPortletCollapseListener(PortletCollapseEvent.Listener listener);

    void removePortletCollapseListener(PortletCollapseEvent.Listener listener);

}

