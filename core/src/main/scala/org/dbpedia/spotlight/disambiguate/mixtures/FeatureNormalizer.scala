package org.dbpedia.spotlight.disambiguate.mixtures

import org.dbpedia.spotlight.model.DBpediaResourceOccurrence
import breeze.linalg.DenseVector
import org.dbpedia.spotlight.graphdb.DBMergedDisambiguator

trait FeatureNormalizer {
  def fromOcc(occ: DBpediaResourceOccurrence): List[(String, Double)]
}

class DefaultFeatureNormalizer extends FeatureNormalizer {
  def fromOcc(occ: DBpediaResourceOccurrence): List[(String, Double)] = {
    List("P(e)", "P(c|e)", "P(s|e)").map(f => (f, occ.featureValue[Double](f).get))
  }
}

class SpotlightSemiLinearFeatureNormalizer extends FeatureNormalizer {
  def fromOcc(occ: DBpediaResourceOccurrence): List[(String, Double)] = {
    List(
      ("P(e)", Math.exp(occ.featureValue[Double]("P(e)").get)),
      ("P(c|e)", occ.contextualScore), //TODO this does not work since contextual score is not set before mixture.getScore() is called
      ("P(s|e)", occ.featureValue[Double]("P(s|e)") match {
        case Some(score) => Math.exp(score)
        case None => 0.0 // NIL Entity has no P(s|e)
      }))
  }
}

class MergedTwoFeatureNormalizer extends FeatureNormalizer {
  def fromOcc(occ: DBpediaResourceOccurrence): List[(String, Double)] = {
    List(
      (DBMergedDisambiguator.PStat, occ.featureValue[Double](DBMergedDisambiguator.PStat).get),
      (DBMergedDisambiguator.PGraph, occ.featureValue[Double](DBMergedDisambiguator.PGraph).getOrElse(0.0)))
  }
}

class MergedSemiLinearFeatureNormalizer extends FeatureNormalizer {
  def fromOcc(occ: DBpediaResourceOccurrence): List[(String, Double)] = {
    List(
      ("P(e)", Math.exp(occ.featureValue[Double]("P(e)").get)),
      ("P(c|e)", occ.contextualScore), // feature value "P(c|e)" is too small for exp, e.g. exp(-434)== 0 with double precision
      ("P(s|e)", occ.featureValue[Double]("P(s|e)") match {
        case Some(score) => Math.exp(score)
        case None => 0.0 // NIL Entity has no P(s|e)
      }),
      (DBMergedDisambiguator.PGraph, occ.featureValue[Double](DBMergedDisambiguator.PGraph).getOrElse(0.0)))
  }
}