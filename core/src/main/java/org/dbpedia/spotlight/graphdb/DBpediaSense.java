package org.dbpedia.spotlight.graphdb;

import org.dbpedia.spotlight.model.DBpediaResource;

import de.unima.dws.dbpediagraph.graphdb.model.Sense;

/**
 * DBpedia {@link Sense} implementation which holds a {@link DBpediaResource} object.
 * 
 * @author Bernhard Schäfer
 * 
 */
public class DBpediaSense implements Sense {

	private final DBpediaResource resource;
	private final String fullUri;

	public DBpediaSense(DBpediaResource resource) {
		this.resource = resource;
		// Spotlight resources are Url encoded, DBpedia dumps are partly encoded.

		// Spotlight --> DBpedia Dump

		// Napol%C3%A9on_%281955_film%29 --> Napol%C3%A9on_(1955_film)
		// Wolfgang_Graf_von_Bl%C3%BCcher --> Wolfgang_Graf_von_Blücher

		// Other examples
		// Sandra_Day_O%27Connor --> Sandra_Day_O'Connor
		// Goal%21_(film) --> Goal!_(film)

		// TODO think about proper transformation and get rid of ugly hack
		fullUri = resource.getFullUri().replace("%28", "(").replace("%29", ")").replace("%27", "'").replace("%21", "!");
	}

	@Override
	public String fullUri() {
		return fullUri;
	}

	public DBpediaResource getResource() {
		return resource;
	}

	@Override
	public String toString() {
		return fullUri();
	}
}
