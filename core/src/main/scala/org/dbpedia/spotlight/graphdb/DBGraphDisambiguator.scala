package org.dbpedia.spotlight.graphdb
import scala.collection.JavaConverters._

import org.dbpedia.spotlight.db.DBCandidateSearcher
import org.dbpedia.spotlight.db.model._
import org.dbpedia.spotlight.disambiguate.ParagraphDisambiguator
import org.dbpedia.spotlight.exceptions.SurfaceFormNotFoundException
import org.dbpedia.spotlight.log.SpotlightLog
import org.dbpedia.spotlight.model._

import com.tinkerpop.blueprints.Graph

import de.unima.dws.dbpediagraph.graphdb._
import de.unima.dws.dbpediagraph.graphdb.disambiguate.GraphDisambiguator
import de.unima.dws.dbpediagraph.graphdb.model.SurfaceFormSenseScore
import de.unima.dws.dbpediagraph.graphdb.subgraph.SubgraphConstructionFactory
import de.unima.dws.dbpediagraph.graphdb.subgraph.SubgraphConstructionSettings

class DBGraphDisambiguator(val graphDisambiguator: GraphDisambiguator[DBpediaSurfaceForm, DBpediaSense],
  val subgraphConstructionSettings: SubgraphConstructionSettings,
  val candidateSearcher: DBCandidateSearcher,
  val surfaceFormStore: SurfaceFormStore) extends ParagraphDisambiguator {

  def this(candidateSearcher: DBCandidateSearcher,
    surfaceFormStore: SurfaceFormStore) = this(GraphConfig.newLocalDisambiguator(GraphType.DIRECTED_GRAPH, DBpediaModelFactory.INSTANCE),
    SubgraphConstructionSettings.fromConfig(GraphConfig.config()),
    candidateSearcher, surfaceFormStore)

  def disambiguate(paragraph: Paragraph): List[DBpediaResourceOccurrence] = {
    // return first from each candidate set
    bestK(paragraph, MAX_CANDIDATES)
      .filter(_._2.nonEmpty)
      .map(_._2.head)
      .toList
      .sortBy(_.textOffset)
  }

  def bestK(paragraph: Paragraph, k: Int): Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]] = {

    SpotlightLog.debug(this.getClass, "Running bestK for paragraph %s.", paragraph.id)

    if (paragraph.occurrences.size == 0)
      return Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]]()

    val sfResources: Map[SurfaceFormOccurrence, List[Candidate]] = getOccurrencesCandidates(paragraph.occurrences, candidateSearcher)

    val surfaceFormsSenses = wrap(sfResources)

    // create subgraph
    val subgraphConstruction = SubgraphConstructionFactory.newSubgraphConstruction(GraphFactory.getDBpediaGraph(), subgraphConstructionSettings);
    val subgraph: Graph = subgraphConstruction.createSubgraph(surfaceFormsSenses);

    // disambiguate using subgraph
    val bestK = graphDisambiguator.bestK(surfaceFormsSenses, subgraph, k).asScala.mapValues(_.asScala.toList).toMap;
    unwrap(bestK)
  }

  //maximum number of considered candidates
  val MAX_CANDIDATES = 20

  def getOccurrencesCandidates(occurrences: List[SurfaceFormOccurrence],
    searcher: DBCandidateSearcher): Map[SurfaceFormOccurrence, List[Candidate]] = {
    val timeBefore = System.currentTimeMillis();

    val occs = occurrences.foldLeft(
      Map[SurfaceFormOccurrence, List[Candidate]]())(
        (acc, sfOcc) => {

          SpotlightLog.debug(this.getClass, "Searching...")

          val candidateRes = {
            val sf = try {
              surfaceFormStore.getSurfaceForm(sfOcc.surfaceForm.name)
            } catch {
              case e: SurfaceFormNotFoundException => sfOcc.surfaceForm
            }

            val cands = candidateSearcher.getCandidates(sf)
            SpotlightLog.debug(this.getClass, "# candidates for: %s = %s.", sf, cands.size)

            if (cands.size > MAX_CANDIDATES) {
              SpotlightLog.debug(this.getClass, "Reducing number of candidates to %d.", MAX_CANDIDATES)
              cands.toList.sortBy(_.prior).reverse.take(MAX_CANDIDATES).toSet
            } else {
              cands
            }
          }

          acc + (sfOcc -> candidateRes.toList)
        })

    val elapsedMsec = System.currentTimeMillis() - timeBefore
    SpotlightLog.info(getClass(), "Found %d total resource candidates for %d surface forms. Elapsed time [sec]: %.3f",
      occs.size, occs.values.foldLeft(0)((total, cs) => total + cs.size), (elapsedMsec / 1000.0))
    occs
  }

  def wrap(sfResources: Map[SurfaceFormOccurrence, List[Candidate]]): java.util.Map[DBpediaSurfaceForm, java.util.List[DBpediaSense]] = {
    sfResources.map(kv => (new DBpediaSurfaceForm(kv._1), kv._2.map(c => new DBpediaSense(c.resource)).asJava)).asJava
  }

  def unwrap(bestK: Map[DBpediaSurfaceForm, List[SurfaceFormSenseScore[DBpediaSurfaceForm, DBpediaSense]]]): Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]] = {
    bestK.map(kv => (kv._1.getSurfaceFormOccurrence(),
      kv._2.map(s => new DBpediaResourceOccurrence(s.sense().getResource(),
        s.surfaceForm().getSurfaceFormOccurrence().surfaceForm,
        s.surfaceForm().getSurfaceFormOccurrence().context,
        s.surfaceForm().getSurfaceFormOccurrence().textOffset,
        s.score()))))
  }

  def name = "Database-backed Graph-based 2 Step disambiguator"
}

object DBGraphDisambiguator {

}