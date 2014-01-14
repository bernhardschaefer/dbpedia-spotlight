package org.dbpedia.spotlight.graphdb
import scala.collection.JavaConverters._

import org.apache.commons.configuration.Configuration
import org.dbpedia.spotlight.db.DBCandidateSearcher
import org.dbpedia.spotlight.db.model._
import org.dbpedia.spotlight.disambiguate.ParagraphDisambiguator
import org.dbpedia.spotlight.exceptions.SurfaceFormNotFoundException
import org.dbpedia.spotlight.log.SpotlightLog
import org.dbpedia.spotlight.model._

import com.google.common.base.Stopwatch
import com.tinkerpop.blueprints.Graph

import de.unima.dws.dbpediagraph._
import de.unima.dws.dbpediagraph.disambiguate.GraphDisambiguator
import de.unima.dws.dbpediagraph.disambiguate.GraphDisambiguatorFactory
import de.unima.dws.dbpediagraph.graph._
import de.unima.dws.dbpediagraph.model.SurfaceFormSenseScore
import de.unima.dws.dbpediagraph.subgraph._

class DBGraphDisambiguator(
  val candidateSearcher: DBCandidateSearcher,
  val surfaceFormStore: SurfaceFormStore,
  val config: Configuration) extends ParagraphDisambiguator {

  def disambiguate(paragraph: Paragraph): List[DBpediaResourceOccurrence] = {
    // return first from each candidate set
    bestK(paragraph, MAX_CANDIDATES)
      .filter(_._2.nonEmpty)
      .map(_._2.head)
      .toList
      .sortBy(_.textOffset)
  }

  def bestK(paragraph: Paragraph, k: Int): Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]] = {
    //TODO cache all objects if config has not changed
    val graph = GraphFactory.getDBpediaGraph()
    val settings = SubgraphConstructionSettings.fromConfig(config)
    val subgraphConstruction = SubgraphConstructionFactory.newSubgraphConstruction(graph, settings)
    val graphDisambiguator: GraphDisambiguator[DBpediaSurfaceForm, DBpediaSense] = GraphDisambiguatorFactory.newFromConfig(config)
    bestK_(paragraph, k, graph, subgraphConstruction, graphDisambiguator)
  }

  def bestK_(paragraph: Paragraph, k: Int, graph: Graph, subgraphConstruction: SubgraphConstruction, graphDisambiguator: GraphDisambiguator[DBpediaSurfaceForm, DBpediaSense]): Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]] = {
    SpotlightLog.debug(this.getClass, "Running bestK for paragraph %s.", paragraph.id)

    if (paragraph.occurrences.size == 0)
      return Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]]()

    val sfResources: Map[SurfaceFormOccurrence, List[Candidate]] = getOccurrencesCandidates(paragraph.occurrences, candidateSearcher)
    val surfaceFormsSenses = wrap(sfResources)

    // create subgraph
    val subgraph = subgraphConstruction.createSubgraph(surfaceFormsSenses)

    // disambiguate using subgraph
    val bestK = graphDisambiguator.bestK(surfaceFormsSenses, subgraph, k).asScala.mapValues(_.asScala.toList).toMap;
    
    val sfOccs = unwrap(bestK)

    // set confidence as percentageOfSecondRank
    sfOccs.foreach{case(sf, candOccs) =>
	    (1 to candOccs.size-1).foreach{ i: Int =>
	        val top = candOccs(i-1)
	        val bottom = candOccs(i)
	        top.setPercentageOfSecondRank(breeze.numerics.exp(bottom.similarityScore - top.similarityScore))
	    }
    }
    
    sfOccs
  }

  //maximum number of considered candidates
  val MAX_CANDIDATES = 20

  def getOccurrencesCandidates(occurrences: List[SurfaceFormOccurrence],
    searcher: DBCandidateSearcher): Map[SurfaceFormOccurrence, List[Candidate]] = {
    val timer = Stopwatch.createStarted();

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

    SpotlightLog.info(getClass(), "Found %d total resource candidates for %d surface forms. Elapsed time: %s",
      occs.values.foldLeft(0)((total, cs) => total + cs.size), occs.size, timer)
    occs
  }

  def wrap(sfResources: Map[SurfaceFormOccurrence, List[Candidate]]): java.util.Map[DBpediaSurfaceForm, java.util.List[DBpediaSense]] = {
	// sfResources.map(kv => (new DBpediaSurfaceForm(kv._1), kv._2.map(c => new DBpediaSense(c.resource)).asJava)).asJava
    sfResources.map(kv => (new DBpediaSurfaceForm(kv._1), kv._2.map(c => new DBpediaSense(c)).asJava)).asJava
  }

  def unwrap(bestK: Map[DBpediaSurfaceForm, List[SurfaceFormSenseScore[DBpediaSurfaceForm, DBpediaSense]]]): Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]] = {
    bestK.map(kv => (kv._1.getSurfaceFormOccurrence(),
      kv._2.map(s => new DBpediaResourceOccurrence(s.getSense().getResource(),
        s.getSurfaceForm().getSurfaceFormOccurrence().surfaceForm,
        s.getSurfaceForm().getSurfaceFormOccurrence().context,
        s.getSurfaceForm().getSurfaceFormOccurrence().textOffset,
        s.getScore()))))
  }

  def name = "Database-backed Graph-based 2 Step disambiguator"
}

object DBGraphDisambiguator {
  def fromConfig(candidateSearcher: DBCandidateSearcher, surfaceFormStore: SurfaceFormStore, config: Configuration): DBGraphDisambiguator = {
    new DBGraphDisambiguator(candidateSearcher, surfaceFormStore, config)
  }

  def fromDefaultConfig(candidateSearcher: DBCandidateSearcher, surfaceFormStore: SurfaceFormStore): DBGraphDisambiguator = {
    val config = GraphConfig.config()
    fromConfig(candidateSearcher, surfaceFormStore, config)
  }
}