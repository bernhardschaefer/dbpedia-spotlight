package org.dbpedia.spotlight.graphdb;

import org.dbpedia.spotlight.model.DBpediaResource;

import de.unima.dws.dbpediagraph.graphdb.model.Sense;

public class DBpediaSense implements Sense {

	private final DBpediaResource resource;
	private final String fullUri;

	public DBpediaSense(DBpediaResource resource) {
		this.resource = resource;
		// Spotlight resources are Url encoded:
		// Company_%28military_unit%29
		// Napol%C3%A9on_%281955_film%29

		// DBpedia dumps are partly encoded:
		// Napol%C3%A9on_(1955_film) [Napoléon_(1955_film)]
		// Wolfgang_Graf_von_Bl%C3%BCcher [Wolfgang_Graf_von_Blücher]

		// TODO think about proper transformation and get rid of ugly hack
		fullUri = resource.getFullUri().replace("%28", "(").replace("%29", ")");
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
