package org.vaadin.addons.portallayout;

import com.vaadin.annotations.Theme;
import com.vaadin.server.VaadinRequest;
import com.vaadin.ui.Layout;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import org.vaadin.addons.portallayout.demo.ActionDemoTab;

/**
 * The Application's "main" class
 */
@SuppressWarnings("serial")
@Theme("test_portal")
public class PortalLayoutDemoUI extends UI {
    @Override
    protected void init(VaadinRequest request) {
        final Layout layout = new VerticalLayout();
        layout.setSizeFull();
        
        setContent(layout);
        layout.addComponent(new ActionDemoTab());
    }
}
