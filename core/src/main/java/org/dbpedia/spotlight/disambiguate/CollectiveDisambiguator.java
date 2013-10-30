package org.dbpedia.spotlight.disambiguate;

import java.util.List;
import java.util.Map;

import org.dbpedia.spotlight.exceptions.SearchException;
import org.dbpedia.spotlight.model.DBpediaResourceOccurrence;
import org.dbpedia.spotlight.model.SurfaceFormOccurrence;

/**
 * Extends a {@link Disambiguator} with methods for collective disambiguation.
 * In collective disambiguation, the surface forms are dependent. Thus, all
 * surface forms in the paragraph are disambiguated simultaneously.
 * 
 * @author Bernhard Schäfer
 * 
 */
public interface CollectiveDisambiguator extends Disambiguator {

	public Map<SurfaceFormOccurrence, List<DBpediaResourceOccurrence>> bestK(List<SurfaceFormOccurrence> occurrences,
			int k) throws SearchException;
}
