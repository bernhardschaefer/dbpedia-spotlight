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

  def bestK(paragraph: Paragraph, k: Int): Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]] = {
    val bestKGraph = graphDisambiguator.bestK(paragraph, k)
    val bestKStat = statDisambiguator.bestK(paragraph, k)

    val bestKMerged = bestKStat.map(kv => {
      val sfo = kv._1
      val statOccs = kv._2

      val graphOccs = bestKGraph.getOrElse(sfo, List[DBpediaResourceOccurrence]())

      val mergedOccs = statOccs.map(statOcc => {
        val graphOcc = graphOccs.find(occ => occ.equals(statOcc))

        val mergedOcc = graphOcc match {
          case Some(occ) => {
            SpotlightLog.debug(this.getClass, "%s has both stat score (%d) and graph score (%d).", sfo, statOcc.similarityScore, occ.similarityScore)
            new DBpediaResourceOccurrence(
              statOcc.id,
              statOcc.resource,
              statOcc.surfaceForm,
              statOcc.context,
              statOcc.textOffset,
              statOcc.provenance,
              statOcc.similarityScore + occ.similarityScore)
          }
          case None => {
            SpotlightLog.debug(this.getClass, "%s has only stat score (%d)", sfo, statOcc.similarityScore)
            statOcc
          }
        }

        mergedOcc
      })

      val mergedOccsSorted = mergedOccs.sortBy(o => o.similarityScore).reverse
      // SpotlightLog.debug(this.getClass, "%s has only stat score (%d)", sfo, mergedOccsSorted)

      sfo -> mergedOccsSorted
    })

    bestKMerged
  }

  def name = "Merged 2 Step disambiguator"

}