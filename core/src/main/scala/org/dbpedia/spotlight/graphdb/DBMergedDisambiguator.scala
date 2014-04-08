package org.dbpedia.spotlight.graphdb

import org.dbpedia.spotlight.disambiguate.ParagraphDisambiguator
import org.dbpedia.spotlight.log.SpotlightLog
import org.dbpedia.spotlight.model.DBpediaResourceOccurrence
import org.dbpedia.spotlight.model.Paragraph
import org.dbpedia.spotlight.model.SurfaceFormOccurrence
import org.dbpedia.spotlight.disambiguate.mixtures.Mixture
import org.dbpedia.spotlight.model.Score

/**
 * Combines DBTwoStepDisambiguator and DBGraphBasedDisambiguator bestK using the provided Mixture.
 *
 */
class DBMergedDisambiguator(
  val graphDisambiguator: ParagraphDisambiguator,
  val statDisambiguator: ParagraphDisambiguator,
  val mixture: Mixture) extends ParagraphDisambiguator {

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

      // go over all statistical bestK entities, try to find graph score for each entity
      val mergedOccs = statOccs.map(statOcc => {
        graphOccs.find(occ => occ.equals(statOcc)) match {
          case Some(graphOcc) => { // merge scores if there is a graph score for entity graphOcc
            val mergedOcc = new DBpediaResourceOccurrence(
              statOcc.id,
              statOcc.resource,
              statOcc.surfaceForm,
              statOcc.context,
              statOcc.textOffset,
              statOcc.provenance)
            
            mergedOcc.features ++= statOcc.features
            mergedOcc.features ++= graphOcc.features
            mergedOcc.setSimilarityScore(mixture.getScore(mergedOcc))

            mergedOcc
          }
          case None => {
            SpotlightLog.debug(this.getClass, "%s[pos %d]->%s: only stat score (%.4f)",
              sfo.surfaceForm.name, sfo.textOffset, statOcc.resource.uri, statOcc.similarityScore)
            statOcc
          }
        }
      })

      val mergedOccsSorted = mergedOccs.sortBy(o => o.similarityScore).reverse
      sfo -> mergedOccsSorted
    })

    bestKMerged
  }

  def name = "Merged 2 Step disambiguator"

}
