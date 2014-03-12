package org.dbpedia.spotlight.disambiguate.mixtures

import org.dbpedia.spotlight.model.{Feature, DBpediaResourceOccurrence}
import org.dbpedia.spotlight.util.MathUtil
import org.dbpedia.spotlight.log.SpotlightLog

/**
 * Multiplication of scores/probabilities. Assumes probabilities are logarithms.
 *
 * @author Joachim Daiber
 */

class UnweightedMixture(features: Set[String]) extends Mixture(1) {

  def getScore(occurrence: DBpediaResourceOccurrence): Double = {
    val fs = occurrence.features.values.filter({ f: Feature => features.contains(f.featureName) }).map(_.value.asInstanceOf[Double])
    val score = MathUtil.lnproduct(fs)
    
    //TODO remove verbose score logging again
    val fsMap = features.foldLeft(Map[String, Double]())(
      (acc, f) => acc + (f -> (occurrence.featureValue[Double](f).getOrElse(Double.NaN))))

    SpotlightLog.debug(this.getClass, "%s --> %s [sim: %.3f, P(s|e): %.3f, P(c|e): %.3f, P(e): %.3f]",
      occurrence.textOffset + ":" + occurrence.surfaceForm.name, occurrence.resource.uri, score,
      fsMap("P(s|e)"), fsMap("P(c|e)"), fsMap("P(e)"))

    score
  }

  override def toString = "UnweightedMixture[%s]".format(features.mkString(","))

}