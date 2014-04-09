package org.dbpedia.spotlight.learning

import java.io.File
import scala.collection.mutable.ListBuffer
import org.dbpedia.spotlight.corpus.AidaCorpus
import org.dbpedia.spotlight.db.DBTwoStepDisambiguator
import org.dbpedia.spotlight.db.SpotlightModel
import org.dbpedia.spotlight.disambiguate.ParagraphDisambiguator
import org.dbpedia.spotlight.io.AnnotatedTextSource
import org.dbpedia.spotlight.log.SpotlightLog
import org.dbpedia.spotlight.model.DBpediaResourceOccurrence
import org.dbpedia.spotlight.model.Factory
import org.dbpedia.spotlight.model.SpotlightConfiguration.DisambiguationPolicy
import org.dbpedia.spotlight.model.SurfaceFormOccurrence
import org.dbpedia.spotlight.graphdb.DBGraphDisambiguator

object FeatureWeightsLearner {

  def main(args: Array[String]) {
    val k = 7
    //TODO error handling
    val corpus: AnnotatedTextSource = AidaCorpus.fromFile(new File(args(1))) // /path/to/AIDA-YAGO2-dataset.tsv
    SpotlightLog.info(this.getClass(), "Loaded corpus %s with %d documents.", corpus.name, corpus.foldLeft(0)((acc, doc) => acc + 1))
    val db = SpotlightModel.fromFolder(new File(args(0)))
    db.disambiguators.get(DisambiguationPolicy.Default).disambiguator.asInstanceOf[DBTwoStepDisambiguator].tokenizer = db.tokenizer;
    //TODO read DisambiguationPolicy from args
    val mergedDisambiguator = db.disambiguators.get(DisambiguationPolicy.Merged)
    learnWeights(corpus, mergedDisambiguator.disambiguator, k)
  }

  def learnWeights(corpus: AnnotatedTextSource, disambiguator: ParagraphDisambiguator, k: Int) = {
    // for each document of gs
    // 1. get all gold standard spots
    // 2. get bestK for each spot
    // 3. for each gs spot
    //    3.1. bestK: get weights for highest ranked incorrect entity
    //    3.2. bestK: get weights for correct gs entity

    corpus.filter(_.occurrences.nonEmpty).foreach(doc => {
      val spots: List[SurfaceFormOccurrence] = doc.occurrences.map(occ =>
        new SurfaceFormOccurrence(occ.surfaceForm, occ.context, occ.textOffset, occ.provenance, -1))

      val globalParagraph = Factory.paragraph().from(spots)
      val bestK: Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]] = disambiguator.bestK(globalParagraph, k)

      doc.occurrences.foreach(gsOcc => {
        val spot = spots.find(sfo => sfo.surfaceForm.equals(gsOcc.surfaceForm) && sfo.textOffset.equals(gsOcc.textOffset)).get
        val occs = bestK(spot).sortBy(_.textOffset)

        occs.foreach(occ => {
          SpotlightLog.info(this.getClass, "%s --> %s [%s]",
            occ.textOffset + ":" + occ.surfaceForm.name, occ.resource.uri, formatWeights(occ))
        })

        val incorrectEntity = occs.find(!_.resource.equals(gsOcc.resource))
        val correctEntity = occs.find(_.resource.equals(gsOcc.resource))

        incorrectEntity match {
          case Some(occ) => SpotlightLog.info(this.getClass(), "0%s%n", formatWeights(occ))
          case None => SpotlightLog.info(this.getClass(), "No incorrect entity found for %s[pos %d]%n", gsOcc.surfaceForm.name, gsOcc.textOffset)
        }

        correctEntity match {
          case Some(occ) => SpotlightLog.info(this.getClass(), "1%s%n", formatWeights(occ))
          case None => SpotlightLog.info(this.getClass(), "No correct entity found for %s[pos %d]%n", gsOcc.surfaceForm.name, gsOcc.textOffset)
        }

      })
    })

    //TODO use breeze linear regression
    // check https://github.com/dbpedia-spotlight/dbpedia-spotlight/blob/48fc19d9002c496a3c39c816dce3816b885fcbf4/index/src/main/scala/org/dbpedia/spotlight/db/SpotterTuner.scala

    //    val x = DenseMatrix.zeros[Double](ny, nx)
    //    val y = DenseVector.zeros[Double](ny)
    //    
    //    val regressed = LinearRegression.regress(x, y)
    //    println(regressed.activeValuesIterator.mkString(" "))
  }

  def formatWeights(occ: DBpediaResourceOccurrence): String = {
    "\t%f\t%f\t%f\t%f".format(
      occ.featureValue[Double]("P(e)").get,
      occ.featureValue[Double]("P(c|e)").get,
      occ.featureValue[Double]("P(s|e)").get,
      occ.featureValue[Double]("P(g|e)").getOrElse(0.0))
  }
}
