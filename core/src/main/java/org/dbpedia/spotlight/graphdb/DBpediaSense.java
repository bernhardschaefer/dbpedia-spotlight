package org.dbpedia.spotlight.graphdb;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.dbpedia.spotlight.model.DBpediaResource;

import de.unima.dws.dbpediagraph.graphdb.model.Sense;

public class DBpediaSense implements Sense {

	private final DBpediaResource resource;
	private final String fullUri;

	public DBpediaSense(DBpediaResource resource) {
		this.resource = resource;
		// Spotlight resources are Url encoded
		// Example: http://dbpedia.org/resource/Company_%28military_unit%29
		try {
			fullUri = URLDecoder.decode(resource.getFullUri(), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public String fullUri() {
		return fullUri;
	}

	public DBpediaResource getResource() {
		return resource;
	}

}
