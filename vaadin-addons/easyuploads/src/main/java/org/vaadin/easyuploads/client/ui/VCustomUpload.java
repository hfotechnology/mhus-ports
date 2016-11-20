package org.vaadin.easyuploads.client.ui;

import org.vaadin.easyuploads.client.Html5FileInputSettingsConnector.HasFileUpload;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.FileUpload;
import com.vaadin.client.VConsole;
import com.vaadin.client.ui.VNotification;
import com.vaadin.client.ui.VUpload;
import com.vaadin.client.ui.dd.VHtml5File;

public class VCustomUpload extends VUpload implements HasFileUpload {

	private Integer maxSize;
	private String maxSizeText;

	@Override
	public void submit() {
		VConsole.error("Submit");
		if (maxSize == null || checkSize(maxSize)) {
			super.submit();
		} else {
			VConsole.error("cancelled upload due to too large file");
			((InputElement) fu.getElement().cast()).setValue(null);
		}
	}

	private boolean checkSize(int maxSize) {
		try {
			InputElement ie = (InputElement) fu.getElement().cast();
			JsArray<VHtml5File> files = getFiles(ie);

			for (int i = 0; i < files.length(); i++) {
				VHtml5File item = files.get(i);
				if (item.getSize() > maxSize) {
					VNotification.createNotification(1000,
							client.getUIConnector().getWidget()).show(
							"File is too big! (max " + maxSize + ")",
							VNotification.CENTERED, "warning");
					return false;
				}
			}
		} catch (Exception e) {
			VConsole.error("Detecting file size failed");
			// ActiveX check might work with older win, but not with modern &&
			// default settings so disabled for now

			// try {
			// checkSizeIe(fu.getElement(), maxSize);
			// } catch (Exception e2) {
			// VConsole.error("Failed with IE hack");
			// }
		}
		return true;
	}

	private static native final JsArray<VHtml5File> getFiles(InputElement ie) 
	/*-{
		return ie.files;
	}-*/;

	/**
	 * @param element
	 * @param maxSize
	 * @return
	 */
	private static native boolean checkSizeIe(Element element, int maxSize)
	/*-{
	var myFSO = new $wnd.ActiveXObject("Scripting.FileSystemObject");
	var filepath = element.value;
	var thefile = myFSO.getFile(filepath);
	var size = thefile.size;
	    
	if(size > maxSize) {
		return false;
	}
	return true;
	}-*/;

    public Integer getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(Integer maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public FileUpload getFileUpload() {
        return fu;
    }

    @Override
    public void setMaxSizeText(String maxSizeText) {
        this.maxSizeText = maxSizeText;
    }

    @Override
    public void setAccept(String accept) {
        //
    }

    @Override
    public void setMaxFileCount(Integer maxCount) {
        // 
    }

}
