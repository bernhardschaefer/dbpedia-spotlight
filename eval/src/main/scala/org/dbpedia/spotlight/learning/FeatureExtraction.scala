package org.dbpedia.spotlight.learning

import org.dbpedia.spotlight.model.DBpediaResourceOccurrence
import breeze.linalg.DenseVector
import org.dbpedia.spotlight.graphdb.DBMergedDisambiguator

trait FeatureExtraction {
  def fromOcc(occ: DBpediaResourceOccurrence): List[(String, Double)]
}

class SpotlightSemiLinearFeatureExtraction extends FeatureExtraction {
  def fromOcc(occ: DBpediaResourceOccurrence): List[(String, Double)] = {
    List(
      ("P(e)", Math.exp(occ.featureValue[Double]("P(e)").get)),
      ("P(c|e)", occ.contextualScore), // feature value "P(c|e)" is too small for exp, e.g. exp(-434)== 0 with double precision
      ("P(s|e)", Math.exp(occ.featureValue[Double]("P(s|e)").get)))
  }
}

class MergedTwoFeatureExtraction extends FeatureExtraction {
  def fromOcc(occ: DBpediaResourceOccurrence): List[(String, Double)] = {
    List(
      (DBMergedDisambiguator.PStat, occ.featureValue[Double](DBMergedDisambiguator.PStat).get),
      (DBMergedDisambiguator.PGraph, occ.featureValue[Double](DBMergedDisambiguator.PGraph).getOrElse(0.0)))
  }
}

class MergedSemiLinearFeatureExtraction extends FeatureExtraction {
  def fromOcc(occ: DBpediaResourceOccurrence): List[(String, Double)] = {
    List(
      ("P(e)", Math.exp(occ.featureValue[Double]("P(e)").get)),
      ("P(c|e)", occ.contextualScore), // feature value "P(c|e)" is too small for exp, e.g. exp(-434)== 0 with double precision
      ("P(s|e)", Math.exp(occ.featureValue[Double]("P(s|e)").get)),
      (DBMergedDisambiguator.PGraph, occ.featureValue[Double](DBMergedDisambiguator.PGraph).getOrElse(0.0)))
  }
}