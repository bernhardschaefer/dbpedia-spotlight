package org.dbpedia.spotlight.graphdb;

import java.util.*;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dbpedia.spotlight.disambiguate.CollectiveDisambiguator;
import org.dbpedia.spotlight.disambiguate.Disambiguator;
import org.dbpedia.spotlight.exceptions.*;
import org.dbpedia.spotlight.model.*;

import com.google.common.base.Stopwatch;
import com.tinkerpop.blueprints.Graph;

import de.unima.dws.dbpediagraph.disambiguate.GraphDisambiguator;
import de.unima.dws.dbpediagraph.disambiguate.GraphDisambiguatorFactory;
import de.unima.dws.dbpediagraph.graph.GraphConfig;
import de.unima.dws.dbpediagraph.graph.GraphFactory;
import de.unima.dws.dbpediagraph.model.SurfaceFormSenseScore;
import de.unima.dws.dbpediagraph.subgraph.*;
import de.unima.dws.dbpediagraph.util.CollectionUtils;

/**
 * Graph based disambiguator compatible with the spotlight interface of {@link Disambiguator}.
 * 
 * @author Bernhard Schäfer
 * 
 */
public class SpotlightGraphDisambiguator extends AbstractSpotlightGraphDisambiguator implements CollectiveDisambiguator {
	private static final Log logger = LogFactory.getLog(SpotlightGraphDisambiguator.class);

	private final SubgraphConstructionSettings subgraphConstructionSettings;
	private final GraphDisambiguator<DBpediaSurfaceForm, DBpediaSense> graphDisambiguator;
	private final CandidateSearcher searcher;

	private static final Configuration config = GraphConfig.config();

	/**
	 * Convenience constructor that retrieves the {@link GraphDisambiguator} and the
	 * {@link SubgraphConstructionSettings} from the {@link GraphConfig} settings.
	 * 
	 * @param searcher
	 */
	public SpotlightGraphDisambiguator(CandidateSearcher searcher) {
		this(GraphDisambiguatorFactory.<DBpediaSurfaceForm, DBpediaSense> newFromConfig(config),
				SubgraphConstructionSettings.fromConfig(config), searcher);
	}

	public SpotlightGraphDisambiguator(GraphDisambiguator<DBpediaSurfaceForm, DBpediaSense> graphDisambiguator,
			SubgraphConstructionSettings subgraphConstructionSettings, CandidateSearcher searcher) {
		this.searcher = searcher;
		this.graphDisambiguator = graphDisambiguator;
		this.subgraphConstructionSettings = subgraphConstructionSettings;
	}

	@Override
	public Map<SurfaceFormOccurrence, List<DBpediaResourceOccurrence>> bestK(List<SurfaceFormOccurrence> occurrences,
			int k) throws SearchException {
		logger.info("Using " + getClass().getSimpleName());
		Graph graph = GraphFactory.getDBpediaGraph();

		Map<SurfaceFormOccurrence, List<DBpediaResource>> sfResources = getSurfaceFormResourceCandidates(occurrences,
				searcher);

		Map<DBpediaSurfaceForm, List<DBpediaSense>> surfaceFormsSenses = DBpediaModelHelper.wrap(sfResources);

		// filter by best k and threshold support
		surfaceFormsSenses = CandidateFilter.byConfigMinSupport(surfaceFormsSenses, config);
		surfaceFormsSenses = CandidateFilter.maxKByConfigPrior(surfaceFormsSenses, config);

		// create subgraph
		SubgraphConstruction subgraphConstruction = SubgraphConstructionFactory.newSubgraphConstruction(graph,
				subgraphConstructionSettings);
		Graph subgraph = subgraphConstruction.createSubgraph(surfaceFormsSenses);

		// disambiguate using subgraph
		Map<DBpediaSurfaceForm, List<SurfaceFormSenseScore<DBpediaSurfaceForm, DBpediaSense>>> bestK = graphDisambiguator
				.bestK(surfaceFormsSenses, subgraph, k);

		return DBpediaModelHelper.unwrap(bestK);
	}

	@Override
	public List<DBpediaResourceOccurrence> disambiguate(List<SurfaceFormOccurrence> sfOccurrences)
			throws SearchException, InputException {
		Map<SurfaceFormOccurrence, List<DBpediaResourceOccurrence>> bestK = bestK(sfOccurrences, 1);
		return CollectionUtils.joinListValues(bestK);
	}

	private static Map<SurfaceFormOccurrence, List<DBpediaResource>> getSurfaceFormResourceCandidates(
			List<SurfaceFormOccurrence> sfOccs, CandidateSearcher searcher) throws SearchException {
		Stopwatch timer = Stopwatch.createStarted();

		Map<SurfaceFormOccurrence, List<DBpediaResource>> sfSenses = new HashMap<>(sfOccs.size());

		for (SurfaceFormOccurrence sfOcc : sfOccs) {
			List<DBpediaResource> resources = Collections.emptyList();
			try {
				resources = new ArrayList<>(searcher.getCandidates(sfOcc.surfaceForm()));
			} catch (ItemNotFoundException e) {
				logger.warn("Error while trying to find candidates for " + sfOcc.surfaceForm().name(), e);
			}
			sfSenses.put(sfOcc, resources);
		}
		if (logger.isInfoEnabled()) {
			logger.info(new StringBuilder().append("Found ").append(CollectionUtils.countCollectionValues(sfSenses))
					.append(" total resource candidates for ").append(sfOccs.size())
					.append(" surface forms. Elapsed time: ").append(timer).toString());
		}
		return sfSenses;
	}

}
