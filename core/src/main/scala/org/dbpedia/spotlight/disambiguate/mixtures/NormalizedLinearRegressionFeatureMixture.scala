package org.dbpedia.spotlight.disambiguate.mixtures

import org.dbpedia.spotlight.model.DBpediaResourceOccurrence

class NormalizedLinearRegressionFeatureMixture(weightedFeatures: List[Pair[String, Double]], offset: Double, val featureNormalizer: FeatureNormalizer) extends Mixture(0) {
  lazy val weightedFeatureScores = weightedFeatures.map(_._2)
  
  def getScore(occurrence: DBpediaResourceOccurrence): Double = {
    val normalizedFeatureScores = featureNormalizer.fromOcc(occurrence).map(_._2)
    //TODO add error checking: ensure that both lists have the same length and the same feature names at each position
    (weightedFeatureScores, normalizedFeatureScores).zipped.map(_ * _).sum + offset
  }
  
  override def toString = this.getClass().getSimpleName()
}