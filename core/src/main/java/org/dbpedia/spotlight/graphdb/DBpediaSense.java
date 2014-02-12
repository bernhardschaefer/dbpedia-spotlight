package org.dbpedia.spotlight.graphdb;

import org.dbpedia.spotlight.model.Candidate;
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

	/**
	 * This constructor assigns the candidate.prior() as prior, which corresponds to P(s|e)
	 */
	public DBpediaSense(Candidate candidate) {
		// Spotlight resources are URL encoded and need to be decoded
		super(UriTransformer.decode(candidate.resource().getFullUri()), candidate.prior(), candidate.support());
		this.resource = candidate.resource();
	}
	
	/**
	 * This constructor assigns the resource.prior() as prior, which corresponds to P(e) 
	 */
	public DBpediaSense(DBpediaResource resource) {
		// Spotlight resources are URL encoded and need to be decoded
		super(UriTransformer.decode(resource.getFullUri()), resource.prior(), resource.support());
		this.resource = resource;
	}

	public DBpediaResource getResource() {
		return resource;
	}

}
