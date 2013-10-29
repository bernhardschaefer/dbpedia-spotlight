package org.dbpedia.spotlight.graphdb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
import org.dbpedia.spotlight.model.SurfaceFormOccurrence;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;

import de.unima.dws.dbpediagraph.graphdb.GraphFactory;
import de.unima.dws.dbpediagraph.graphdb.Graphs;
import de.unima.dws.dbpediagraph.graphdb.disambiguate.GraphDisambiguator;
import de.unima.dws.dbpediagraph.graphdb.disambiguate.local.DegreeCentrality;
import de.unima.dws.dbpediagraph.graphdb.model.ModelTransformer;
import de.unima.dws.dbpediagraph.graphdb.model.SurfaceForm;
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

	private static final Comparator<DBpediaResourceOccurrence> offsetComparator = new Comparator<DBpediaResourceOccurrence>() {
		@Override
		public int compare(DBpediaResourceOccurrence o1, DBpediaResourceOccurrence o2) {
			return Integer.compare(o1.textOffset(), o2.textOffset());
		}
	};

	private static List<DBpediaResourceOccurrence> unwrap(
			List<SurfaceFormSenseScore<DBpediaSurfaceForm, DBpediaSense>> results) {
		List<DBpediaResourceOccurrence> resources = new ArrayList<>(results.size());
		for (SurfaceFormSenseScore<DBpediaSurfaceForm, DBpediaSense> senseScore : results) {
			DBpediaResource resource = senseScore.sense().getResource();
			SurfaceFormOccurrence surfaceFormOccurrence = senseScore.surfaceForm().getSurfaceFormOccurrence();
			resources.add(new DBpediaResourceOccurrence(resource, surfaceFormOccurrence.surfaceForm(),
					surfaceFormOccurrence.context(), surfaceFormOccurrence.textOffset(), senseScore.getScore()));
		}
		// sort according to textOffset so that the spotlight demo
		// (https://github.com/dbpedia-spotlight/demo) works
		Collections.sort(resources, offsetComparator);
		return resources;
	}

	private final GraphDisambiguator<DBpediaSurfaceForm, DBpediaSense> graphDisambiguator;
	private static final GraphDisambiguator<DBpediaSurfaceForm, DBpediaSense> DEFAULT_DISAMBIGUATOR = new DegreeCentrality<>(
			Direction.BOTH, DBpediaModelFactory.INSTANCE);

	private final SubgraphConstructionSettings subgraphConstructionSettings;

	/**
	 * The only relevant method is
	 * {@link CandidateSearcher#getCandidates(SurfaceForm)}
	 */
	CandidateSearcher searcher;

	public SpotlightGraphDisambiguator(CandidateSearcher searcher) {
		this(DEFAULT_DISAMBIGUATOR, SubgraphConstructionSettings.getDefault(), searcher);
	}

	public SpotlightGraphDisambiguator(GraphDisambiguator<DBpediaSurfaceForm, DBpediaSense> graphDisambiguator,
			SubgraphConstructionSettings subgraphConstructionSettings, CandidateSearcher searcher) {
		this.searcher = searcher;
		this.graphDisambiguator = graphDisambiguator;
		this.subgraphConstructionSettings = subgraphConstructionSettings;
	}

	@Override
	public List<DBpediaResourceOccurrence> disambiguate(List<SurfaceFormOccurrence> sfOccurrences)
			throws SearchException, InputException {
		logger.info("Using " + getClass().getSimpleName());

		Graph dbpediaGraph = GraphFactory.getDBpediaGraph();
		SubgraphConstruction subgraphConstruction = SubgraphConstructionFactory.newDefaultImplementation(dbpediaGraph,
				subgraphConstructionSettings);

		// transform SurfaceFormOccurrence to graphdb compatible format
		Collection<SurfaceFormSenses<DBpediaSurfaceForm, DBpediaSense>> surfaceFormsSenses = getSFOsCandidates(sfOccurrences);
		Collection<Collection<Vertex>> wordsSenses = ModelTransformer.wordsVerticesFromSenses(dbpediaGraph,
				surfaceFormsSenses);

		Graph subgraph = subgraphConstruction.createSubgraph(wordsSenses);

		if (logger.isInfoEnabled())
			logger.info(new StringBuilder().append("Created subgraph (vertices: ")
					.append(Graphs.numberOfVertices(subgraph)).append(", edges: ")
					.append(Graphs.numberOfEdges(subgraph)).append(")").toString());

		List<SurfaceFormSenseScore<DBpediaSurfaceForm, DBpediaSense>> results = graphDisambiguator.disambiguate(
				surfaceFormsSenses, subgraph);

		// TODO think about how to get rid of the generic model in
		// dbpedia-graphdb using maps;

		// approach 1:
		// uri -> DBpediaResource
		// word -> SurfaceFormOccurrence
		// problem: In one sentence there can be multiple SFOs of the same word
		// (if the word occurs multiple times)

		// approach 2:
		// graph db SurfaceForm(String id, String word)
		// use with SurfaceForm(sfo.hashCode(), sfo.surfaceForm.name)
		// for each SFO: go through DBpediaResource candidates
		return unwrap(results);
	}

	private Collection<SurfaceFormSenses<DBpediaSurfaceForm, DBpediaSense>> getSFOsCandidates(
			List<SurfaceFormOccurrence> sfOccurrences) throws SearchException {
		Collection<SurfaceFormSenses<DBpediaSurfaceForm, DBpediaSense>> surfaceFormsSenses = new ArrayList<>(
				sfOccurrences.size());
		for (SurfaceFormOccurrence sfOcc : sfOccurrences) {
			Collection<DBpediaResource> candidates = Collections.emptyList();
			;
			try {
				candidates = searcher.getCandidates(sfOcc.surfaceForm());
			} catch (ItemNotFoundException e) {
				logger.warn("Error while trying to find candidates for " + sfOcc.surfaceForm().name(), e);
			}
			surfaceFormsSenses.add(new DBpediaSurfaceFormSenses(sfOcc, candidates));
		}
		return surfaceFormsSenses;
	}

}
