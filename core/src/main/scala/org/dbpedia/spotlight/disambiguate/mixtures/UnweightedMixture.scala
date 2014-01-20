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
    val p_se = occurrence.featureValue("P(s|e)") match { 
      case Some(p) => p.asInstanceOf[Double]
      case None => 0.0
    }
    
    val p_e = occurrence.featureValue("P(e)") match { 
      case Some(p) => p.asInstanceOf[Double]
      case None => 0.0
    }
    
    val p_ce = occurrence.featureValue("P(c|e)") match { 
      case Some(p) => p.asInstanceOf[Double]
      case None => 0.0
    }
    
    //TODO remove verboose score logging again
    SpotlightLog.debug(this.getClass, "%s --> %s [sim: %.3f, P(s|e): %.3f, P(c|e): %.3f, P(e): %.3f]", 
    		occurrence.textOffset + ":" + occurrence.surfaceForm.name, occurrence.resource.uri, score, p_se, p_ce, p_e)
        
    score
  }

  override def toString = "UnweightedMixture[%s]".format(features.mkString(","))

}