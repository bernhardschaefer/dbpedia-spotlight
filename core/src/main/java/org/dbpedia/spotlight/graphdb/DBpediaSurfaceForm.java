package org.dbpedia.spotlight.graphdb;

import org.dbpedia.spotlight.model.SurfaceFormOccurrence;

import de.unima.dws.dbpediagraph.graphdb.model.SurfaceForm;

/**
 * DBpedia {@link SurfaceForm} implementation which holds a {@link SurfaceFormOccurrence} object.
 * 
 * @author Bernhard Schäfer
 * 
 */
public class DBpediaSurfaceForm implements SurfaceForm {
	private final SurfaceFormOccurrence surfaceFormOccurrence;

	public DBpediaSurfaceForm(SurfaceFormOccurrence surfaceFormOccurrence) {
		this.surfaceFormOccurrence = surfaceFormOccurrence;
	}

	public SurfaceFormOccurrence getSurfaceFormOccurrence() {
		return surfaceFormOccurrence;
	}

	@Override
	public String name() {
		return getSurfaceFormOccurrence().surfaceForm().name();
	}

	@Override
	public String toString() {
		return name();
	}

}
