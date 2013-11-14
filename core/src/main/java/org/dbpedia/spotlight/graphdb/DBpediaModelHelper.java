package org.dbpedia.spotlight.graphdb;

import java.util.*;
import java.util.Map.Entry;

import org.dbpedia.spotlight.exceptions.SearchException;
import org.dbpedia.spotlight.model.*;

import de.unima.dws.dbpediagraph.graphdb.model.SurfaceFormSenseScore;

/**
 * Static helper class for transforming from DBpedia to Graph Model.
 * 
 * @author Bernhard Schäfer
 * 
 */
public class DBpediaModelHelper {

	private static final Comparator<SurfaceFormOccurrence> offsetComparator = new Comparator<SurfaceFormOccurrence>() {
		@Override
		public int compare(SurfaceFormOccurrence o1, SurfaceFormOccurrence o2) {
			return Integer.compare(o1.textOffset(), o2.textOffset());
		}
	};

	private static final Comparator<DBpediaResourceOccurrence> scoreComparator = new Comparator<DBpediaResourceOccurrence>() {
		@Override
		public int compare(DBpediaResourceOccurrence o1, DBpediaResourceOccurrence o2) {
			return Double.compare(o1.similarityScore(), o2.similarityScore());
		}
	};

	public static List<DBpediaSense> convertToSenses(Collection<DBpediaResource> resources) {
		List<DBpediaSense> senses = new ArrayList<>(resources.size());
		for (DBpediaResource resource : resources)
			senses.add(new DBpediaSense(resource));
		return senses;
	}

	public static Map<DBpediaSurfaceForm, List<DBpediaSense>> wrap(
			Map<SurfaceFormOccurrence, ? extends Collection<DBpediaResource>> surfaceFormsSenses)
			throws SearchException {
		Map<DBpediaSurfaceForm, List<DBpediaSense>> sfSenses = new HashMap<>(surfaceFormsSenses.size());
		for (Entry<SurfaceFormOccurrence, ? extends Collection<DBpediaResource>> entry : surfaceFormsSenses.entrySet()) {
			List<DBpediaSense> senses = convertToSenses(entry.getValue());
			sfSenses.put(new DBpediaSurfaceForm(entry.getKey()), senses);
		}
		return sfSenses;
	}

	/**
	 * Transform the results back to the DBpedia model. Sorts the {@link SurfaceFormOccurrence} based on their offset
	 * and the {@link DBpediaResourceOccurrence} based on their similarityScore.
	 * 
	 * @param bestK
	 *            the results from graphdb
	 */
	public static Map<SurfaceFormOccurrence, List<DBpediaResourceOccurrence>> unwrap(
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
			Collections.sort(occs, scoreComparator);
			resultMap.put(sFO, occs);
		}
		return resultMap;
	}
}
