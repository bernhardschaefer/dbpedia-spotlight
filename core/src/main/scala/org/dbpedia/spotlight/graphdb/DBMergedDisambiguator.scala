package org.dbpedia.spotlight.graphdb

import org.dbpedia.spotlight.disambiguate.ParagraphDisambiguator
import org.dbpedia.spotlight.disambiguate.mixtures.LinearRegressionFeatureMixture
import org.dbpedia.spotlight.disambiguate.mixtures.Mixture
import org.dbpedia.spotlight.log.SpotlightLog
import org.dbpedia.spotlight.model.DBpediaResourceOccurrence
import org.dbpedia.spotlight.model.Paragraph
import org.dbpedia.spotlight.model.Score
import org.dbpedia.spotlight.model.SurfaceFormOccurrence

/**
 * Combines DBTwoStepDisambiguator and DBGraphBasedDisambiguator bestK using the provided Mixture.
 *
 */
class DBMergedDisambiguator(
  val graphDisambiguator: ParagraphDisambiguator,
  val statDisambiguator: ParagraphDisambiguator,
  val mixture: Mixture) extends ParagraphDisambiguator {

  def disambiguate(paragraph: Paragraph): List[DBpediaResourceOccurrence] = {
    // return first from each candidate set
    bestK(paragraph, 10)
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

      //TODO (1) the linreg mixture score cannot simply be joined with the fallback unweighted mixture statistical score
      // --> solution1: make sure both (stat. & graph-based) use the same candidate set so that the fallback case never happens
      // --> solution2: always use linreg mixture, set graph-based score to zero if no graph score exists

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

            mergedOcc.contextualScore = statOcc.contextualScore
            mergedOcc.setFeature(new Score(DBMergedDisambiguator.PStat, statOcc.similarityScore))
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

      val mergedOccsSorted = mergedOccs.sortBy(o => o.similarityScore).reverse.take(k)
      sfo -> mergedOccsSorted
    })

    bestKMerged
  }

  def name = "Merged 2 Step disambiguator"

}

object DBMergedDisambiguator {
  val PGraph = "P(graph)"
  val PStat = "P(stat)"

  val WGraph = 0.35
  val WStat = 0.65

  def defaultWeightTwoFeatures(graphDisambiguator: ParagraphDisambiguator, statDisambiguator: ParagraphDisambiguator) = {
    new DBMergedDisambiguator(graphDisambiguator, statDisambiguator,
      new LinearRegressionFeatureMixture(List((PStat, WStat), (PGraph, WGraph)), 0))
  }

  def defaultWeightFourFeatures(graphDisambiguator: ParagraphDisambiguator, statDisambiguator: ParagraphDisambiguator) = {
    new DBMergedDisambiguator(graphDisambiguator, statDisambiguator,
      new LinearRegressionFeatureMixture(List(("P(e)", -0.0216), ("P(c|e)", -0.0005), ("P(s|e)", -0.2021), (PGraph, 0.25)), 0))
  }
}
