/**
 * Author: Roeland Luykx
 * 
 * Copyright (c) 2026+ by RALY GmbH
 */

package ch.hl7.vacd.api.utils;

/**
 * 
 */
public class RessourceUtil {

	public static String removeUrn(String id) {
		if (id != null && id.startsWith("urn:uuid:")) {
			return id.substring(9);
		} else if (id != null && id.startsWith("urn:oid:")) {
			return id.substring(8);
		}
		return id;
	}

}
