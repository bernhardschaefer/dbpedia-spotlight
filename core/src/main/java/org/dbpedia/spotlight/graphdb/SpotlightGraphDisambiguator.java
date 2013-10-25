package org.dbpedia.spotlight.graphdb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dbpedia.spotlight.disambiguate.Disambiguator;
import org.dbpedia.spotlight.exceptions.InputException;
import org.dbpedia.spotlight.exceptions.ItemNotFoundException;
import org.dbpedia.spotlight.exceptions.SearchException;
import org.dbpedia.spotlight.model.CandidateSearcher;
import org.dbpedia.spotlight.model.DBpediaResource;
import org.dbpedia.spotlight.model.DBpediaResourceOccurrence;
import org.dbpedia.spotlight.model.SpotlightConfiguration;
import org.dbpedia.spotlight.model.SurfaceForm;
import org.dbpedia.spotlight.model.SurfaceFormOccurrence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;

import de.unima.dws.dbpediagraph.graphdb.GraphFactory;
import de.unima.dws.dbpediagraph.graphdb.Graphs;
import de.unima.dws.dbpediagraph.graphdb.disambiguate.GraphDisambiguator;
import de.unima.dws.dbpediagraph.graphdb.disambiguate.local.DegreeCentrality;
import de.unima.dws.dbpediagraph.graphdb.model.ModelTransformer;
import de.unima.dws.dbpediagraph.graphdb.model.SurfaceFormSenseScore;
import de.unima.dws.dbpediagraph.graphdb.model.SurfaceFormSenses;
import de.unima.dws.dbpediagraph.graphdb.subgraph.SubgraphConstruction;
import de.unima.dws.dbpediagraph.graphdb.subgraph.SubgraphConstructionFactory;
import de.unima.dws.dbpediagraph.graphdb.subgraph.SubgraphConstructionSettings;

/**
 * Graph based disambiguator compatible with the spotlight interface of
 * {@link Disambiguator}.
 * 
 * @author Bernhard Schäfer
 * 
 */
public class SpotlightGraphDisambiguator extends AbstractSpotlightGraphDisambiguator implements Disambiguator {
	private static final Log logger = LogFactory.getLog(SpotlightGraphDisambiguator.class);

	private static List<DBpediaResourceOccurrence> unwrap(
			List<SurfaceFormSenseScore<DBpediaSurfaceForm, DBpediaSense>> results) {
		List<DBpediaResourceOccurrence> resources = new ArrayList<>(results.size());
		for (SurfaceFormSenseScore<DBpediaSurfaceForm, DBpediaSense> senseScore : results) {
			DBpediaResource resource = senseScore.sense().getResource();
			SurfaceFormOccurrence surfaceFormOccurrence = senseScore.surfaceForm().getSurfaceFormOccurrence();
			resources.add(new DBpediaResourceOccurrence(resource, surfaceFormOccurrence.surfaceForm(),
					surfaceFormOccurrence.context(), surfaceFormOccurrence.textOffset()));
		}
		return resources;
	}

	private final GraphDisambiguator<DBpediaSurfaceForm, DBpediaSense> graphDisambiguator;
	private static final GraphDisambiguator<DBpediaSurfaceForm, DBpediaSense> DEFAULT_DISAMBIGUATOR = new DegreeCentrality<>(
			Direction.BOTH, DBpediaModelFactory.INSTANCE);

	private final SubgraphConstruction subgraphConstruction;

	/**
	 * The only relevant method is
	 * {@link CandidateSearcher#getCandidates(SurfaceForm)}
	 */
	CandidateSearcher searcher;

	public SpotlightGraphDisambiguator(CandidateSearcher searcher) {
		// TODO think about how to prevent IllegalStateException when no graph
		// exists (exception should be thrown when disambiguate() is called
		this(DEFAULT_DISAMBIGUATOR, SubgraphConstructionFactory.newDefaultImplementation(
				GraphFactory.getDBpediaGraph(), SubgraphConstructionSettings.getDefault()), searcher);
	}

	public SpotlightGraphDisambiguator(GraphDisambiguator<DBpediaSurfaceForm, DBpediaSense> graphDisambiguator,
			SubgraphConstruction subgraphConstruction, CandidateSearcher searcher) {
		this.searcher = searcher;
		this.graphDisambiguator = graphDisambiguator;
		this.subgraphConstruction = subgraphConstruction;
	}

	@Override
	public List<DBpediaResourceOccurrence> disambiguate(List<SurfaceFormOccurrence> sfOccurrences)
			throws SearchException, InputException {
		logger.info("Starting disambiguation with " + getClass().getSimpleName());
		Collection<SurfaceFormSenses<DBpediaSurfaceForm, DBpediaSense>> surfaceFormsSenses = getSFOsCandidates(sfOccurrences);

		Collection<Collection<Vertex>> wordsSenses = ModelTransformer.wordsVerticesFromSenses(
				GraphFactory.getDBpediaGraph(), surfaceFormsSenses);
		Graph subgraph = subgraphConstruction.createSubgraph(wordsSenses);
		logger.info("Created subgraph with " + Graphs.numberOfVertices(subgraph) + " vertices");

		List<SurfaceFormSenseScore<DBpediaSurfaceForm, DBpediaSense>> results = graphDisambiguator.disambiguate(
				surfaceFormsSenses, subgraph);
		return unwrap(results);
	}

	private Collection<SurfaceFormSenses<DBpediaSurfaceForm, DBpediaSense>> getSFOsCandidates(
			List<SurfaceFormOccurrence> sfOccurrences) throws SearchException {
		Collection<SurfaceFormSenses<DBpediaSurfaceForm, DBpediaSense>> surfaceFormsSenses = new ArrayList<>(
				sfOccurrences.size());
		Collection<DBpediaResource> candidates;
		for (SurfaceFormOccurrence sfOcc : sfOccurrences) {
			try {
				candidates = searcher.getCandidates(sfOcc.surfaceForm());
			} catch (ItemNotFoundException e) {
				logger.warn("Error while trying to find candidates for " + sfOcc.surfaceForm().name(), e);
				candidates = Collections.emptyList();
			}
			surfaceFormsSenses.add(new DBpediaSurfaceFormSenses(sfOcc, candidates));
		}
		return surfaceFormsSenses;
	}

}
