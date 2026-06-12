/**
 * Author: Roeland Luykx
 * 
 * Copyright (c) 2026+ by RALY GmbH
 */

package ch.hl7.vacd.api.utils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Resource;

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

	public static Bundle ensureBundleProfile(Bundle bundle, String chVacdBundleProfile) {
		Meta meta = bundle.getMeta();
		if (meta != null) {
			List<String> profiles = meta.getProfile().stream().map(CanonicalType::getValue)
					.collect(Collectors.toList());
			if (profiles.contains(chVacdBundleProfile)) {
				return bundle;
			}
		}
		if (meta == null) {
			meta = new Meta();
			bundle.setMeta(meta);
		}
		meta.addProfile(chVacdBundleProfile);
		return bundle;
	}

	// --- ID extraction ---
	public static String extractId(Resource resource, Map<Resource, String> fullUrlMap) {
		String id = resource.getIdElement() != null ? resource.getIdElement().getIdPart() : null;
		if (id != null && !id.isEmpty()) {
			return id;
		}
		String fullUrl = fullUrlMap.get(resource);
		if (fullUrl != null && fullUrl.startsWith("urn:uuid:")) {
			return fullUrl.substring("urn:uuid:".length());
		}
		return UUID.randomUUID().toString();
	}

}
