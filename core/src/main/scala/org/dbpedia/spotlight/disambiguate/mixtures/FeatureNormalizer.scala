package org.dbpedia.spotlight.disambiguate.mixtures

import org.dbpedia.spotlight.graphdb.DBMergedDisambiguator
import org.dbpedia.spotlight.model.DBpediaResourceOccurrence

trait FeatureNormalizer {
  def fromOcc(occ: DBpediaResourceOccurrence): List[(String, Double)]
}

class DefaultFeatureNormalizer extends FeatureNormalizer {
  def fromOcc(occ: DBpediaResourceOccurrence): List[(String, Double)] = {
    // QuickFix: use fallback since NIL Entity has no P(s|e) without spotting
    // scores are logarithmic scale, -1000 is very low 
    List("P(e)", "P(c|e)", "P(s|e)").map(f => (f, occ.featureValue[Double](f).getOrElse(-1000.0)))
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
        case None => 0.0 // QuickFix: use fallback since NIL Entity has no P(s|e) without spotting
      }),
      (DBMergedDisambiguator.PGraph, occ.featureValue[Double](DBMergedDisambiguator.PGraph).getOrElse(0.0)))
  }
}