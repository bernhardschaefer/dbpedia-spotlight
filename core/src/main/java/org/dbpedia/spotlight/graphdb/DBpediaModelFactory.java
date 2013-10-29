package org.dbpedia.spotlight.graphdb;

import java.util.Collection;

import org.dbpedia.spotlight.model.DBpediaResource;

import com.tinkerpop.blueprints.Vertex;

import de.unima.dws.dbpediagraph.graphdb.Graphs;
import de.unima.dws.dbpediagraph.graphdb.model.ModelFactory;
import de.unima.dws.dbpediagraph.graphdb.model.SurfaceFormSenseScore;
import de.unima.dws.dbpediagraph.graphdb.model.SurfaceFormSenses;

/**
 * Concrete factory as singleton for creating dbpedia related sense and surface
 * form instances.
 * 
 * @author Bernhard Schäfer
 * 
 */
public enum DBpediaModelFactory implements ModelFactory<DBpediaSurfaceForm, DBpediaSense> {
	INSTANCE;

	@Override
	public SurfaceFormSenseScore<DBpediaSurfaceForm, DBpediaSense> newSurfaceFormSenseScore(
			DBpediaSurfaceForm surfaceForm, DBpediaSense sense, double score) {
		return new DBpediaSurfaceFormSenseScore(surfaceForm, sense, score);
	}

	@Override
	public DBpediaSense newSense(String uri) {
		return new DBpediaSense(new DBpediaResource(uri));
	}

	@Override
	public DBpediaSense newSense(Vertex v) {
		return newSense(Graphs.uriOfVertex(v));
	}

	@Override
	public SurfaceFormSenses<DBpediaSurfaceForm, DBpediaSense> newSurfaceFormSenses(Collection<DBpediaSense> senses,
			String name) {
		throw new UnsupportedOperationException();
	}

}
