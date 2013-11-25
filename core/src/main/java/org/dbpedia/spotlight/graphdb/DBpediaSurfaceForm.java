package org.dbpedia.spotlight.graphdb;

import org.dbpedia.spotlight.model.SurfaceFormOccurrence;

import de.unima.dws.dbpediagraph.model.DefaultSurfaceForm;
import de.unima.dws.dbpediagraph.model.SurfaceForm;

/**
 * DBpedia {@link SurfaceForm} implementation which holds a {@link SurfaceFormOccurrence} object.
 * 
 * @author Bernhard Schäfer
 * 
 */
public class DBpediaSurfaceForm extends DefaultSurfaceForm implements SurfaceForm {
	private final SurfaceFormOccurrence surfaceFormOccurrence;

	public DBpediaSurfaceForm(SurfaceFormOccurrence surfaceFormOccurrence) {
		super(surfaceFormOccurrence.surfaceForm().name());
		this.surfaceFormOccurrence = surfaceFormOccurrence;
	}

	public SurfaceFormOccurrence getSurfaceFormOccurrence() {
		return surfaceFormOccurrence;
	}

}
