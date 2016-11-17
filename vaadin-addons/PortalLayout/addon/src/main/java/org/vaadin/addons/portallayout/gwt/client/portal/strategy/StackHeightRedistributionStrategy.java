/*
 * Copyright 2013 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.vaadin.addons.portallayout.gwt.client.portal.strategy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.vaadin.addons.portallayout.gwt.client.portal.PortalLayoutUtil;
import org.vaadin.addons.portallayout.gwt.client.portal.connection.PortalLayoutConnector;
import org.vaadin.addons.portallayout.gwt.client.portlet.PortletChrome;
import org.vaadin.addons.portallayout.gwt.client.portlet.PortletConnector;

import com.google.gwt.user.client.Element;
import com.vaadin.client.ComponentConnector;
import com.vaadin.client.ComputedStyle;
import com.vaadin.client.Profiler;
import com.vaadin.client.Util;

/**
 * StackHeightRedistributionStrategy.
 */
public class StackHeightRedistributionStrategy implements PortalHeightRedistributionStrategy {

    @Override
    public void redistributeHeights(PortalLayoutConnector portalConnector) {
        Profiler.enter("PLC.recalcHeight");
        Iterator<ComponentConnector> it = portalConnector.getCurrentChildren().iterator();
        List<ComponentConnector> relativeHeightPortlets = new ArrayList<ComponentConnector>();
        double totalPercentage = 0;
        int totalFixedHeightConsumption = 0;
        while (it.hasNext()) {
            ComponentConnector cc = it.next();
            PortletConnector portletConnector = PortalLayoutUtil.getPortletConnectorForContent(cc);
            if (portletConnector != null) {
                if (portletConnector.hasRelativeHeight()) {
                    totalPercentage += Util.parseRelativeSize(portletConnector.getState().height);
                    relativeHeightPortlets.add(cc);
                } else {
                    PortletChrome portletWidget = portletConnector.getPortletChrome();
                    totalFixedHeightConsumption += cc.getLayoutManager().getOuterHeight(
                            portletWidget.getAssociatedSlot().getElement());
                }   
            }
        }
        if (totalPercentage > 0) {
            totalPercentage = Math.max(totalPercentage, 100);
            int totalPortalHeight = portalConnector.getLayoutManager().getInnerHeight(portalConnector.getWidget().getElement());
            boolean isSpacing = portalConnector.getState().spacing;
            int spacingConsumption = 0;
            if (isSpacing && portalConnector.getView().getWidgetCount() > 0) {
                Element spacingEl = portalConnector.getWidget().getElement().getChild(0).cast();
                spacingConsumption += new ComputedStyle(spacingEl).getIntProperty("height") * portalConnector.getView().getWidgetCount() - 1;
            }
            int reservedForRelativeSize = totalPortalHeight - totalFixedHeightConsumption - spacingConsumption;
            double ratio = reservedForRelativeSize / (double) totalPortalHeight * 100d;
            for (ComponentConnector cc : relativeHeightPortlets) {
                PortletConnector portletConnector = PortalLayoutUtil.getPortletConnectorForContent(cc);
                if (!portletConnector.isCollapsed()) {
                    float percentageHeight = Util.parseRelativeSize(portletConnector.getState().height);
                    double slotHeight = (percentageHeight / totalPercentage * ratio);
                    int headerHeight = portletConnector.getPortletChrome().getHeader().getOffsetHeight();
                    double headerHeightPercentage = (double)headerHeight / totalPortalHeight * 100d;
                    String slotHeightStr = Math.max(slotHeight, headerHeightPercentage) + "%";
                    portletConnector.getPortletChrome().getAssociatedSlot().setHeight(slotHeightStr);
                }
            }
        }
        Profiler.leave("PLC.recalcHeight");        
    }
}
