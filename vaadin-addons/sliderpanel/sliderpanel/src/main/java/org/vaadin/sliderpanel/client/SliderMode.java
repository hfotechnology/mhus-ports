package org.vaadin.sliderpanel.client;

/**
 * Presentation mode of the sliders
 *
 * @author Marten Prieß (http://www.non-rocket-science.com)
 * @version 1.0
 */
public enum SliderMode {

	/**
	 * slides from top to bottom
	 */
	TOP(false),
	/**
	 * slides from right to left
	 */
	RIGHT(true),
	/**
	 * slides from bottom to top
	 */
	BOTTOM(false),
	/**
	 * slides from left to right
	 */
	LEFT(true);

	private boolean vertical;

	SliderMode(final boolean vertical) {
		this.vertical = vertical;
	}

	/**
	 * layout is vertical
	 * 
	 * @return is vertical
	 */
	public boolean isVertical() {
		return this.vertical;
	}
}
