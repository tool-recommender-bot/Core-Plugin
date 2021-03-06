/*******************************************************************************
 * Copyright (c) 2012 The PDT Extension Group (https://github.com/pdt-eg)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.pdtextensions.server.lhttpd;

public interface ILHttpdLocationConfigWorkingCopy extends ILHttpdLocationConfig {
	
	/**
	 * Sets the location path
	 * @param path location path
	 */
	void setPath(String path);
	
	/**
	 * Sets the module
	 * @param module module
	 */
	void setModule(ILHttpdModule module);
	
	/**
	 * Sets the allow override directive value
	 * @param value allow override; null to remove it and use the server default.
	 */
	void setAllowOverride(String value);

}
