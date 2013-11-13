package org.dbpedia.spotlight.graphdb;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dbpedia.spotlight.disambiguate.CollectiveDisambiguator;
import org.dbpedia.spotlight.disambiguate.Disambiguator;
import org.dbpedia.spotlight.exceptions.*;
import org.dbpedia.spotlight.model.*;

import com.tinkerpop.blueprints.Graph;

import de.unima.dws.dbpediagraph.graphdb.*;
import de.unima.dws.dbpediagraph.graphdb.disambiguate.GraphDisambiguator;
import de.unima.dws.dbpediagraph.graphdb.model.SurfaceFormSenseScore;
import de.unima.dws.dbpediagraph.graphdb.subgraph.*;
import de.unima.dws.dbpediagraph.graphdb.util.CollectionUtils;

/**
 * Graph based disambiguator compatible with the spotlight interface of {@link Disambiguator}.
 * 
 * @author Bernhard Schäfer
 * 
 */
public class SpotlightGraphDisambiguator extends AbstractSpotlightGraphDisambiguator implements CollectiveDisambiguator {
	private static final Log logger = LogFactory.getLog(SpotlightGraphDisambiguator.class);

	/**
	 * Configuration key for filtering candidate senses by minimum support.
	 */
	private static final String KEY_CANDIDATE_MIN_SUPPORT = "org.dbpedia.spotlight.graphdb.minSupport";

	private final SubgraphConstructionSettings subgraphConstructionSettings;
	private final GraphDisambiguator<DBpediaSurfaceForm, DBpediaSense> graphDisambiguator;
	private final CandidateSearcher searcher;

	/**
	 * Convenience constructor that retrieves the {@link GraphDisambiguator} and the
	 * {@link SubgraphConstructionSettings} from the {@link GraphConfig} settings.
	 * 
	 * @param searcher
	 */
	public SpotlightGraphDisambiguator(CandidateSearcher searcher) {
		this(GraphConfig.newLocalDisambiguator(GraphType.DIRECTED_GRAPH, DBpediaModelFactory.INSTANCE),
				SubgraphConstructionSettings.fromConfig(GraphConfig.config()), searcher);
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

		// get filtered sense candidates
		Map<SurfaceFormOccurrence, Set<DBpediaResource>> sfResources = getSurfaceFormCandidates(occurrences, searcher);
		int minSupport = GraphConfig.config().getInt(KEY_CANDIDATE_MIN_SUPPORT, 0);
		filterResourcesBySupport(sfResources, minSupport);
		Map<DBpediaSurfaceForm, List<DBpediaSense>> surfaceFormsSenses = DBpediaModelHelper.wrap(sfResources);

		// create subgraph
		SubgraphConstruction subgraphConstruction = SubgraphConstructionFactory.newSubgraphConstruction(
				GraphFactory.getDBpediaGraph(), subgraphConstructionSettings);
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

	/**
	 * Removes all {@link DBpediaResource} from the map values with support < minSupport. If minSupport <= 0, the method
	 * returns right away.
	 */
	private static void filterResourcesBySupport(Map<SurfaceFormOccurrence, Set<DBpediaResource>> surfaceFormsSenses,
			final int minSupport) {
		if (minSupport <= 0)
			return;
		int filtered = 0;
		for (Set<DBpediaResource> resources : surfaceFormsSenses.values()) {
			Iterator<DBpediaResource> iter = resources.iterator();
			while (iter.hasNext()) {
				DBpediaResource resource = iter.next();
				if (resource.support() < minSupport) {
					iter.remove();
					filtered++;
				}
			}
		}
		logger.info("Filtered " + filtered + " resource candidates with support < " + minSupport);
	}

	private static Map<SurfaceFormOccurrence, Set<DBpediaResource>> getSurfaceFormCandidates(
			List<SurfaceFormOccurrence> sfOccs, CandidateSearcher searcher) throws SearchException {
		long timeBefore = System.currentTimeMillis();

		Map<SurfaceFormOccurrence, Set<DBpediaResource>> sfSenses = new HashMap<>(sfOccs.size());

		for (SurfaceFormOccurrence sfOcc : sfOccs) {
			Set<DBpediaResource> resources = Collections.emptySet();
			try {
				resources = searcher.getCandidates(sfOcc.surfaceForm());
			} catch (ItemNotFoundException e) {
				logger.warn("Error while trying to find candidates for " + sfOcc.surfaceForm().name(), e);
			}
			sfSenses.put(sfOcc, resources);
		}
		if (logger.isInfoEnabled()) {
			long elapsedTime = System.currentTimeMillis() - timeBefore;
			logger.info(new StringBuilder().append("Found ").append(CollectionUtils.countCollectionValues(sfSenses))
					.append(" total resource candidates. Elapsed time [sec]: ").append(elapsedTime / 1000.0).toString());
		}
		return sfSenses;
	}

}
