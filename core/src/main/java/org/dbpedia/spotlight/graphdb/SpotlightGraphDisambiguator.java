package org.dbpedia.spotlight.graphdb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dbpedia.spotlight.disambiguate.CollectiveDisambiguator;
import org.dbpedia.spotlight.disambiguate.Disambiguator;
import org.dbpedia.spotlight.exceptions.InputException;
import org.dbpedia.spotlight.exceptions.ItemNotFoundException;
import org.dbpedia.spotlight.exceptions.SearchException;
import org.dbpedia.spotlight.model.CandidateSearcher;
import org.dbpedia.spotlight.model.DBpediaResource;
import org.dbpedia.spotlight.model.DBpediaResourceOccurrence;
import org.dbpedia.spotlight.model.SurfaceFormOccurrence;

import com.tinkerpop.blueprints.Graph;

import de.unima.dws.dbpediagraph.graphdb.GraphFactory;
import de.unima.dws.dbpediagraph.graphdb.GraphType;
import de.unima.dws.dbpediagraph.graphdb.disambiguate.GraphDisambiguator;
import de.unima.dws.dbpediagraph.graphdb.disambiguate.local.KPPCentrality;
import de.unima.dws.dbpediagraph.graphdb.model.SurfaceForm;
import de.unima.dws.dbpediagraph.graphdb.model.SurfaceFormSenseScore;
import de.unima.dws.dbpediagraph.graphdb.subgraph.SubgraphConstruction;
import de.unima.dws.dbpediagraph.graphdb.subgraph.SubgraphConstructionFactory;
import de.unima.dws.dbpediagraph.graphdb.subgraph.SubgraphConstructionSettings;
import de.unima.dws.dbpediagraph.graphdb.util.CollectionUtils;

/**
 * Graph based disambiguator compatible with the spotlight interface of {@link Disambiguator}.
 * 
 * @author Bernhard Schäfer
 * 
 */
public class SpotlightGraphDisambiguator extends AbstractSpotlightGraphDisambiguator implements CollectiveDisambiguator {
	private static final Log logger = LogFactory.getLog(SpotlightGraphDisambiguator.class);

	private static final GraphDisambiguator<DBpediaSurfaceForm, DBpediaSense> DEFAULT_DISAMBIGUATOR = new KPPCentrality<>(
			GraphType.DIRECTED_GRAPH, DBpediaModelFactory.INSTANCE);

	private static final Comparator<SurfaceFormOccurrence> offsetComparator = new Comparator<SurfaceFormOccurrence>() {
		@Override
		public int compare(SurfaceFormOccurrence o1, SurfaceFormOccurrence o2) {
			return Integer.compare(o1.textOffset(), o2.textOffset());
		}
	};

	public static List<DBpediaSense> convertToSenses(Collection<DBpediaResource> resources) {
		List<DBpediaSense> senses = new ArrayList<>(resources.size());
		for (DBpediaResource resource : resources)
			senses.add(new DBpediaSense(resource));
		return senses;
	}

	private static Map<SurfaceFormOccurrence, List<DBpediaResourceOccurrence>> unwrap(
			Map<DBpediaSurfaceForm, List<SurfaceFormSenseScore<DBpediaSurfaceForm, DBpediaSense>>> bestK) {
		// sort according to textOffset so that the spotlight demo
		// (https://github.com/dbpedia-spotlight/demo) works
		Map<SurfaceFormOccurrence, List<DBpediaResourceOccurrence>> resultMap = new TreeMap<>(offsetComparator);
		for (Map.Entry<DBpediaSurfaceForm, List<SurfaceFormSenseScore<DBpediaSurfaceForm, DBpediaSense>>> entry : bestK
				.entrySet()) {
			SurfaceFormOccurrence sFO = entry.getKey().getSurfaceFormOccurrence();
			List<DBpediaResourceOccurrence> occs = new ArrayList<>();
			for (SurfaceFormSenseScore<DBpediaSurfaceForm, DBpediaSense> senseScore : entry.getValue()) {
				DBpediaResource resource = senseScore.sense().getResource();
				occs.add(new DBpediaResourceOccurrence(resource, sFO.surfaceForm(), sFO.context(), sFO.textOffset(),
						senseScore.score()));
			}
			resultMap.put(sFO, occs);
		}
		return resultMap;
	}

	private final SubgraphConstructionSettings subgraphConstructionSettings;

	private final GraphDisambiguator<DBpediaSurfaceForm, DBpediaSense> graphDisambiguator;

	/**
	 * The only relevant method is {@link CandidateSearcher#getCandidates(SurfaceForm)}
	 */
	private final CandidateSearcher searcher;

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
	public Map<SurfaceFormOccurrence, List<DBpediaResourceOccurrence>> bestK(List<SurfaceFormOccurrence> occurrences,
			int k) throws SearchException {
		logger.info("Using " + getClass().getSimpleName());

		// get sense candidates
		Map<DBpediaSurfaceForm, List<DBpediaSense>> surfaceFormsSenses = getSFOsCandidatesMap(occurrences);

		// create subgraph
		SubgraphConstruction subgraphConstruction = SubgraphConstructionFactory.newSubgraphConstruction(
				GraphFactory.getDBpediaGraph(), subgraphConstructionSettings);
		Graph subgraph = subgraphConstruction.createSubgraph(surfaceFormsSenses);

		// disambiguate using subgraph
		Map<DBpediaSurfaceForm, List<SurfaceFormSenseScore<DBpediaSurfaceForm, DBpediaSense>>> bestK = graphDisambiguator
				.bestK(surfaceFormsSenses, subgraph, k);

		return unwrap(bestK);
	}

	@Override
	public List<DBpediaResourceOccurrence> disambiguate(List<SurfaceFormOccurrence> sfOccurrences)
			throws SearchException, InputException {
		Map<SurfaceFormOccurrence, List<DBpediaResourceOccurrence>> bestK = bestK(sfOccurrences, 1);
		return CollectionUtils.joinListValues(bestK);
	}

	private Map<DBpediaSurfaceForm, List<DBpediaSense>> getSFOsCandidatesMap(List<SurfaceFormOccurrence> sfOccs)
			throws SearchException {
		long timeBefore = System.currentTimeMillis();
		Map<DBpediaSurfaceForm, List<DBpediaSense>> sFSenses = new HashMap<>(sfOccs.size());
		for (SurfaceFormOccurrence sfOcc : sfOccs) {
			Collection<DBpediaResource> resources = Collections.emptyList();
			try {
				resources = searcher.getCandidates(sfOcc.surfaceForm());
			} catch (ItemNotFoundException e) {
				logger.warn("Error while trying to find candidates for " + sfOcc.surfaceForm().name(), e);
			}
			sFSenses.put(new DBpediaSurfaceForm(sfOcc), convertToSenses(resources));
		}
		if (logger.isInfoEnabled()) {
			long elapsedTime = System.currentTimeMillis() - timeBefore;
			logger.info(new StringBuilder().append("Found ").append(CollectionUtils.joinListValues(sFSenses).size())
					.append(" total sense candidates. Elapsed time [sec]: ").append(elapsedTime / 1000.0).toString());
		}
		return sFSenses;
	}
}
