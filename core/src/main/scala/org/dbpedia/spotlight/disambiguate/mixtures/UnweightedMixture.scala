package org.dbpedia.spotlight.disambiguate.mixtures

import org.dbpedia.spotlight.model.{Feature, DBpediaResourceOccurrence}
import breeze.numerics._
import org.dbpedia.spotlight.log.SpotlightLog

/**
 * Multiplication of scores/probabilities. Assumes probabilities are logarithms.
 *
 * @author Joachim Daiber
 */

class UnweightedMixture(features: Set[String]) extends Mixture(1) {

  def getScore(occurrence: DBpediaResourceOccurrence): Double = {
    val fs = occurrence.features.values.filter({ f: Feature => features.contains(f.featureName) }).map(_.value.asInstanceOf[Double])
    val score = fs.foldLeft(0.0)((x: Double, y: Double) => if(x == -inf || y == -inf) -inf else x + y)
    
    // Set("P(e)", "P(c|e)", "P(s|e)")
  
    var scoreMap: Map[String, Double] = Map()
    
    for ((score, count) <- fs.zipWithIndex) {
    
    }
    
      //TODO remove verboose score logging again
    SpotlightLog.debug(this.getClass, "sf: %s, res: %s, sim: %.3f, P(s|e): %.3f, P(c|e): %.3f, P(e): %.3f", 
    		occurrence.textOffset + ":" + occurrence.surfaceForm.name, occurrence.resource.uri, occurrence.similarityScore, 
            occurrence.featureValue("P(s|e)").asInstanceOf[Double], occurrence.featureValue("P(c|e)").asInstanceOf[Double], 
            occurrence.featureValue("P(e)").asInstanceOf[Double])
        
    score
  }

  override def toString = "UnweightedMixture[%s]".format(features.mkString(","))

}