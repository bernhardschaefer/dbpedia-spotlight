package org.dbpedia.spotlight.graphdb;

import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dbpedia.spotlight.disambiguate.CollectiveDisambiguator;
import org.dbpedia.spotlight.disambiguate.Disambiguator;
import org.dbpedia.spotlight.exceptions.*;
import org.dbpedia.spotlight.model.*;

import com.google.common.collect.Ordering;
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
	private static final String CONFIG_CANDIDATE_MIN_SUPPORT = "org.dbpedia.spotlight.graphdb.filter.minSupport";
	/**
	 * Configuration key for filtering the best k candidate senses by support.
	 */
	private static final String CONFIG_CANDIDATE_BEST_K_SUPPORT = "org.dbpedia.spotlight.graphdb.filter.bestkSupport";

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

		Map<SurfaceFormOccurrence, List<DBpediaResource>> sfResources = getSurfaceFormResourceCandidates(occurrences,
				searcher);

		// filter by best k and threshold support
		int minSupport = GraphConfig.config().getInt(CONFIG_CANDIDATE_MIN_SUPPORT, -1);
		filterResourcesBySupport(sfResources, minSupport);
		int bestkSupport = GraphConfig.config().getInt(CONFIG_CANDIDATE_BEST_K_SUPPORT, -1);
		filterBestkResourcesBySupport(sfResources, bestkSupport);

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

	private static Ordering<DBpediaResource> descSupport = new Ordering<DBpediaResource>() {
		@Override
		public int compare(DBpediaResource o1, DBpediaResource o2) {
			return Integer.compare(o2.support(), o1.support());
		}
	};

	/**
	 * Deletes all {@link DBpediaResource} from the map values which do not belong to the best k for each surface form.
	 * If k <= 0, the method does not do anything.
	 */
	private static void filterBestkResourcesBySupport(Map<SurfaceFormOccurrence, List<DBpediaResource>> sfResources,
			int k) {
		if (k <= 0)
			return;
		int filtered = 0;

		for (Entry<SurfaceFormOccurrence, List<DBpediaResource>> entry : sfResources.entrySet()) {
			if (entry.getValue().size() > k) {
				List<DBpediaResource> unfilteredResources = entry.getValue();
				List<DBpediaResource> bestKResources = descSupport.greatestOf(unfilteredResources, k);
				sfResources.put(entry.getKey(), bestKResources);

				filtered += (unfilteredResources.size() - bestKResources.size());
			}
		}

		logger.info("Filtered " + filtered + " resource candidates by best " + k + " candidate filter.");
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
	private static void filterResourcesBySupport(Map<SurfaceFormOccurrence, List<DBpediaResource>> sfResources,
			final int minSupport) {
		if (minSupport <= 0)
			return;
		int filtered = 0;

		for (Entry<SurfaceFormOccurrence, List<DBpediaResource>> entry : sfResources.entrySet()) {
			List<DBpediaResource> unfilteredResources = entry.getValue();
			List<DBpediaResource> filteredResources = new ArrayList<>();
			for (DBpediaResource resource : unfilteredResources) {
				if (resource.support() >= minSupport)
					filteredResources.add(resource);
			}
			sfResources.put(entry.getKey(), filteredResources);

			filtered += (unfilteredResources.size() - filteredResources.size());
		}

		logger.info("Filtered " + filtered + " resource candidates with support < " + minSupport);
	}

	private static Map<SurfaceFormOccurrence, List<DBpediaResource>> getSurfaceFormResourceCandidates(
			List<SurfaceFormOccurrence> sfOccs, CandidateSearcher searcher) throws SearchException {
		long timeBefore = System.currentTimeMillis();

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
			long elapsedTime = System.currentTimeMillis() - timeBefore;
			logger.info(new StringBuilder().append("Found ").append(CollectionUtils.countCollectionValues(sfSenses))
					.append(" total resource candidates for ").append(sfOccs.size())
					.append(" surface forms. Elapsed time [sec]: ").append(elapsedTime / 1000.0).toString());
		}
		return sfSenses;
	}

}
