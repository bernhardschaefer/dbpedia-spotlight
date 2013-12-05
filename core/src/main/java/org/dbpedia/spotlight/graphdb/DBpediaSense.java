package org.dbpedia.spotlight.graphdb;

import org.dbpedia.spotlight.model.DBpediaResource;

import de.unima.dws.dbpediagraph.graph.UriTransformer;
import de.unima.dws.dbpediagraph.model.DefaultSense;
import de.unima.dws.dbpediagraph.model.Sense;

/**
 * DBpedia {@link Sense} implementation which holds a {@link DBpediaResource} object.
 * 
 * @author Bernhard Schäfer
 * 
 */
public class DBpediaSense extends DefaultSense implements Sense {

	private final DBpediaResource resource;

	public DBpediaSense(DBpediaResource resource) {
		// Spotlight resources are URL encoded and need to be decoded
		super(UriTransformer.decode(resource.getFullUri()));
		this.resource = resource;
	}

	public DBpediaResource getResource() {
		return resource;
	}

}
