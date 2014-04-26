package org.dbpedia.spotlight.graphdb

import org.dbpedia.spotlight.disambiguate.ParagraphDisambiguator
import org.dbpedia.spotlight.disambiguate.mixtures.LinearRegressionFeatureMixture
import org.dbpedia.spotlight.disambiguate.mixtures.MergedSemiLinearFeatureNormalizer
import org.dbpedia.spotlight.disambiguate.mixtures.Mixture
import org.dbpedia.spotlight.disambiguate.mixtures.NormalizedLinearRegressionFeatureMixture
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

  def defaultWeightTwoFeatures(graphDisambiguator: ParagraphDisambiguator, statDisambiguator: ParagraphDisambiguator) = {
    new DBMergedDisambiguator(graphDisambiguator, statDisambiguator,
      //new LinearRegressionFeatureMixture(List((PStat, 0.401804), (PGraph, 0.263434)), 0.056802)) // vowpal wabbit mit best-7 graph-based
      new LinearRegressionFeatureMixture(List((PStat, 0.44507077855000726), (PGraph, 0.3880231012993819)), 0)) // breeze mit best-7
  }

  def defaultWeightFourFeatures(graphDisambiguator: ParagraphDisambiguator, statDisambiguator: ParagraphDisambiguator) = {
    new DBMergedDisambiguator(graphDisambiguator, statDisambiguator,
      new NormalizedLinearRegressionFeatureMixture(
        // vowpal wabbit weights
        List(("P(e)", 186.894287), ("P(c|e)", 0.314567), ("P(s|e)", 0.155955), (PGraph, 0.184077)), 0.086976,
        new MergedSemiLinearFeatureNormalizer()))
  }
}