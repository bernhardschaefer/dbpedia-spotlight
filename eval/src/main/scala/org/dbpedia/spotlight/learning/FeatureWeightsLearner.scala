package org.dbpedia.spotlight.learning

import java.io.File
import scala.collection.mutable.ListBuffer
import org.dbpedia.spotlight.corpus.AidaCorpus
import org.dbpedia.spotlight.db.SpotlightModel
import org.dbpedia.spotlight.disambiguate.ParagraphDisambiguator
import org.dbpedia.spotlight.io.AnnotatedTextSource
import org.dbpedia.spotlight.log.SpotlightLog
import org.dbpedia.spotlight.model.DBpediaResourceOccurrence
import org.dbpedia.spotlight.model.Factory
import org.dbpedia.spotlight.model.SpotlightConfiguration.DisambiguationPolicy
import org.dbpedia.spotlight.model.SurfaceFormOccurrence
import org.dbpedia.spotlight.model.DBpediaResourceOccurrence
import breeze.regress.LinearRegression
import breeze.linalg.DenseMatrix
import breeze.linalg.DenseVector
import org.dbpedia.spotlight.db.DBTwoStepDisambiguator
import org.dbpedia.spotlight.db.DBTwoStepDisambiguator

object FeatureWeightsLearner {

  def main(args: Array[String]) {
    //TODO error handling
    val corpus = AidaCorpus.fromFile(new File(args(1))) // /path/to/AIDA-YAGO2-dataset.tsv
    val db = SpotlightModel.fromFolder(new File(args(0)))
    db.disambiguators.get(DisambiguationPolicy.Default).disambiguator.asInstanceOf[DBTwoStepDisambiguator].tokenizer = db.tokenizer;
    //TODO read DisambiguationPolicy from args
    val mergedDisambiguator = db.disambiguators.get(DisambiguationPolicy.Merged)
    learnWeights(corpus, mergedDisambiguator.disambiguator)
  }

  def learnWeights(corpus: AnnotatedTextSource, disambiguator: ParagraphDisambiguator) = {
    // 1. get all gold standard spots
    val gsOccs: List[DBpediaResourceOccurrence] = corpus.foldLeft(ListBuffer[DBpediaResourceOccurrence]())((acc, paragraph) => acc ++= paragraph.occurrences).toList
    val spots: List[SurfaceFormOccurrence] = gsOccs.map(occ =>
      new SurfaceFormOccurrence(occ.surfaceForm, occ.context, occ.textOffset, occ.provenance))

    // 2. get bestK for each spot
    val globalParagraph = Factory.paragraph().from(spots)
    val bestK: Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]] = disambiguator.bestK(globalParagraph, 20)

    // 3. for each gs spot
    //    3.1. bestK: get weights for highest ranked incorrect entity
    //    3.2. bestK: get weights for correct gs entity
    gsOccs.foreach(gsOcc => {
      val spot = spots.find(sfo => sfo.surfaceForm.equals(gsOcc.surfaceForm) && sfo.textOffset.equals(gsOcc.textOffset)).get
      val occs = bestK(spot).sortBy(_.textOffset)

      val incorrectEntity = occs.find(!_.resource.equals(gsOcc.resource))
      val correctEntity = occs.find(_.resource.equals(gsOcc.resource))

      incorrectEntity match {
        case Some(occ) => printf("0%s%n", formatWeights(occ))
        case None => printf("No incorrect entity found for %s[pos %d]%n", gsOcc.surfaceForm.name, gsOcc.textOffset)
      }

      correctEntity match {
        case Some(occ) => printf("1%s%n", formatWeights(occ))
        case None => printf("No correct entity found for %s[pos %d]%n", gsOcc.surfaceForm.name, gsOcc.textOffset)
      }

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
      occ.featureValue[Double]("P(g|e)").get)
  }
}
