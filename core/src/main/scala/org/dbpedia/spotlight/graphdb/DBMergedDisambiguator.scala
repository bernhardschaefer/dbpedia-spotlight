package org.dbpedia.spotlight.graphdb

import org.dbpedia.spotlight.disambiguate.ParagraphDisambiguator
import org.dbpedia.spotlight.log.SpotlightLog
import org.dbpedia.spotlight.model.DBpediaResourceOccurrence
import org.dbpedia.spotlight.model.Paragraph
import org.dbpedia.spotlight.model.SurfaceFormOccurrence

/**
 * Combines DBTwoStepDisambiguator and DBGraphBasedDisambiguator bestK
 *
 */
class DBMergedDisambiguator(
  val graphDisambiguator: ParagraphDisambiguator,
  val statDisambiguator: ParagraphDisambiguator) extends ParagraphDisambiguator {

  //maximum number of considered candidates
  val MAX_CANDIDATES = 20

  def disambiguate(paragraph: Paragraph): List[DBpediaResourceOccurrence] = {
    // return first from each candidate set
    bestK(paragraph, MAX_CANDIDATES)
      .filter(kv =>
        kv._2.nonEmpty)
      .map(kv =>
        kv._2.head)
      .toList
      .sortBy(_.textOffset)
  }

  val w_graph = 0.6
  val w_stat = 0.4

  def weightedLinearCombination(sim_graph: Double, sim_stat: Double): Double = {
    w_graph * sim_graph + w_stat * sim_stat
  }

  def bestK(paragraph: Paragraph, k: Int): Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]] = {
    val bestKGraph = graphDisambiguator.bestK(paragraph, k)
    val bestKStat = statDisambiguator.bestK(paragraph, k)

    val bestKMerged = bestKStat.map(kv => {
      val sfo = kv._1
      val statOccs = kv._2

      val graphOccs = bestKGraph.getOrElse(sfo, List[DBpediaResourceOccurrence]())

      val mergedOccs = statOccs.map(statOcc => {
        graphOccs.find(occ => occ.equals(statOcc)) match {
          case Some(occ) => {
            val statScore = statOcc.similarityScore
            val graphScore = occ.similarityScore
            val mergedScore = weightedLinearCombination(graphScore, statScore)
            SpotlightLog.debug(this.getClass, "%s (pos %d): %.3f (graph x statistical = %.2f x %.2f + %.2f x %.2f)",
              sfo.surfaceForm.name, sfo.textOffset, mergedScore, w_graph, graphScore, w_stat, statScore)
            new DBpediaResourceOccurrence(
              statOcc.id,
              statOcc.resource,
              statOcc.surfaceForm,
              statOcc.context,
              statOcc.textOffset,
              statOcc.provenance,
              mergedScore)
          }
          case None => {
            SpotlightLog.debug(this.getClass, "%s (pos %d) has only stat score (%.4f)",
              sfo.surfaceForm.name, sfo.textOffset, statOcc.similarityScore)
            statOcc
          }
        }
      })

      val mergedOccsSorted = mergedOccs.sortBy(o => o.similarityScore).reverse
      // SpotlightLog.debug(this.getClass, "%s has only stat score (%.4f)", sfo.surfaceForm.name, sfo.textOffset, mergedOccsSorted)

      sfo -> mergedOccsSorted
    })

    bestKMerged
  }

  def name = "Merged 2 Step disambiguator"

}