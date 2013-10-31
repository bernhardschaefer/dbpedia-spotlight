package org.dbpedia.spotlight.graphdb;

import org.dbpedia.spotlight.model.DBpediaResource;
import org.dbpedia.spotlight.model.SurfaceFormOccurrence;

import de.unima.dws.dbpediagraph.graphdb.model.SurfaceFormSenseScore;

/**
 * DBpedia {@link SurfaceFormSenseScore} implementation.
 * 
 * @author Bernhard Schäfer
 * 
 */
public class DBpediaSurfaceFormSenseScore implements SurfaceFormSenseScore<DBpediaSurfaceForm, DBpediaSense> {
	private final DBpediaSurfaceForm surfaceForm;
	private final DBpediaSense sense;
	private final double score;

	public DBpediaSurfaceFormSenseScore(DBpediaSurfaceForm dbpediaSurfaceForm, DBpediaSense sense, double score) {
		this.surfaceForm = dbpediaSurfaceForm;
		this.sense = sense;
		this.score = score;
	}

	public DBpediaSurfaceFormSenseScore(SurfaceFormOccurrence surfaceFormOccurrence, DBpediaResource resource,
			double score) {
		this.surfaceForm = new DBpediaSurfaceForm(surfaceFormOccurrence);
		this.sense = new DBpediaSense(resource);
		this.score = score;
	}

	@Override
	public int compareTo(SurfaceFormSenseScore<DBpediaSurfaceForm, DBpediaSense> o) {
		return Double.compare(score(), o.score());
	}

	@Override
	public double score() {
		return score;
	}

	@Override
	public DBpediaSense sense() {
		return sense;
	}

	@Override
	public DBpediaSurfaceForm surfaceForm() {
		return surfaceForm;
	}

	@Override
	public String toString() {
		return new StringBuilder().append(surfaceForm.toString()).append(": ").append(sense.toString()).append(" --> ")
				.append(score).toString();
	}

}
