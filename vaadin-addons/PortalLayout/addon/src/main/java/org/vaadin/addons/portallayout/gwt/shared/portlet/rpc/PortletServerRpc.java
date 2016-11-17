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
package org.vaadin.addons.portallayout.gwt.shared.portlet.rpc;

import com.vaadin.shared.annotations.Delayed;
import com.vaadin.shared.communication.ServerRpc;

/**
 * PortletServerRpc.
 */
public interface PortletServerRpc extends ServerRpc {

    void close();

    void setCollapsed(boolean isCollapsed);

    @Delayed(lastOnly = true)
    void updatePixelWidth(int widthPixels);

    @Delayed(lastOnly = true)
    void updatePixelHeight(int heightPixels);
}
