package org.dbpedia.spotlight.graphdb;

import org.dbpedia.spotlight.model.SurfaceFormOccurrence;

import de.unima.dws.dbpediagraph.model.SurfaceForm;

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
		return surfaceFormOccurrence.surfaceForm().name();
	}

	// we cannot use DefaultSurfaceForm hashCode() and equals() since offset is important

	@Override
	public int hashCode() {
		return surfaceFormOccurrence.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return surfaceFormOccurrence.equals(obj);
	}
}
