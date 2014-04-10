package org.dbpedia.spotlight.learning

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

import org.dbpedia.spotlight.corpus.AidaCorpus
import org.dbpedia.spotlight.db.DBTwoStepDisambiguator
import org.dbpedia.spotlight.db.SpotlightModel
import org.dbpedia.spotlight.disambiguate.ParagraphDisambiguator
import org.dbpedia.spotlight.evaluation.EvalUtils
import org.dbpedia.spotlight.io.AnnotatedTextSource
import org.dbpedia.spotlight.log.SpotlightLog
import org.dbpedia.spotlight.model.AnnotatedParagraph
import org.dbpedia.spotlight.model.DBpediaResourceOccurrence
import org.dbpedia.spotlight.model.Factory
import org.dbpedia.spotlight.model.SpotlightConfiguration.DisambiguationPolicy
import org.dbpedia.spotlight.model.SurfaceFormOccurrence

import breeze.linalg._
import breeze.regress.LinearRegression

object FeatureWeightsLearner {

  def main(args: Array[String]) {
    val corpus: AnnotatedTextSource = AidaCorpus.fromFile(new File(args(1))) // /path/to/CoNLL-YAGO.tsv
    SpotlightLog.info(this.getClass(), "Loaded corpus %s with %d documents.", corpus.name, corpus.foldLeft(0)((acc, doc) => acc + 1))

    val writer = new PrintWriter(new FileWriter("%s-%s.scores.log".format(corpus.name, EvalUtils.now())))

    val db = SpotlightModel.fromFolder(new File(args(0)))
    db.disambiguators.get(DisambiguationPolicy.Default).disambiguator.asInstanceOf[DBTwoStepDisambiguator].tokenizer = db.tokenizer;
    val mergedDisambiguator = db.disambiguators.get(DisambiguationPolicy.Merged) //TODO read DisambiguationPolicy from args

    val numFeatures = 4
    val k = 10
    learnWeights(corpus, mergedDisambiguator.disambiguator, k, numFeatures, writer)
  }

  def learnWeights(corpus: AnnotatedTextSource, disambiguator: ParagraphDisambiguator, k: Int, numFeatures: Int, writer: PrintWriter) = {
    // for each document of gs
    // 1. get all gold standard spots
    // 2. get bestK for each spot
    // 3. for each gs spot
    //    3.1. bestK: get scores for highest ranked incorrect entity
    //    3.2. bestK: get scores for correct gs entity

    // filter all documents without annotations and all NIL annotations
    val filteredCorpus = corpus.filter(_.occurrences.nonEmpty).map(doc => {
      val filteredOccs = doc.occurrences.filter(!_.resource.uri.equals(AidaCorpus.nilUri))
      new AnnotatedParagraph(doc.id, doc.text, filteredOccs)
    })

    // we want to reserve 2 rows in the matrix for each surface form, 1 for a correct and 1 for an incorrect entity  
    val numTargets = filteredCorpus.foldLeft(0)((acc, doc) => acc + 2 * doc.occurrences.size)
    val features = DenseMatrix.zeros[Double](numTargets, numFeatures)
    val targets = DenseVector.zeros[Double](numTargets)

    var i = 0
    filteredCorpus.foreach(doc => {
      val paragraph = Factory.paragraph().from(doc)
      val bestKMap: Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]] = disambiguator.bestK(paragraph, k)

      doc.occurrences.foreach(gsOcc => {
        val sfo = Factory.SurfaceFormOccurrence.from(gsOcc)
        val occs = bestKMap(sfo)

        SpotlightLog.debug(this.getClass(), "\tRanked entities for sf %d:%s:", sfo.textOffset, sfo.surfaceForm.name)
        occs.foreach(occ => {
          SpotlightLog.debug(this.getClass(), "id: %d, uri: %s, scores: %s", occ.resource.id, occ.resource.uri, occ.similarityScore)
        })

        val correctEntity = occs.find(_.resource.uri.equals(gsOcc.resource.uri))
        correctEntity match {
          case Some(occ) => {
            SpotlightLog.debug(this.getClass(), "Found correct entity %s", occ.resource.uri)
            EntityStats.correctFound += 1
            val featureScores = getFeatureScores(occ)
            writer.printf("1%s%n", toTsvVector(featureScores))
            features(i, ::) := featureScores.t
            targets(i) = 1
          }
          case None => {
            SpotlightLog.debug(this.getClass(), "Correct entity %s not found for sf %d:%s", gsOcc.resource.uri, gsOcc.textOffset, gsOcc.surfaceForm.name)
            EntityStats.correctNotFound += 1
          }
        }
        i += 1

        val incorrectEntity = occs.find(!_.resource.uri.equals(gsOcc.resource.uri))
        incorrectEntity match {
          case Some(occ) => {
            SpotlightLog.debug(this.getClass(), "Found incorrect entity %s", occ.resource.uri)
            EntityStats.incorrectFound += 1
            val featureScores = getFeatureScores(occ)
            writer.printf("0%s%n", toTsvVector(featureScores))
            features(i, ::) := featureScores.t
            targets(i) = 0
          }
          case None => {
            SpotlightLog.debug(this.getClass(), "No incorrect entity found for %d:%s", gsOcc.textOffset, gsOcc.surfaceForm.name)
            EntityStats.incorrectNotFound += 1
          }
        }
        i += 1
      })
    })

    writer.flush()
    writer.close()

    SpotlightLog.info(this.getClass(), "%s", EntityStats)

    val regressed = LinearRegression.regress(features, targets)
    SpotlightLog.info(this.getClass(), "Weights: %s", regressed.activeValuesIterator.mkString("\t"))
  }

  def getFeatureScores(occ: DBpediaResourceOccurrence): DenseVector[Double] = {
    DenseVector(Math.exp(occ.featureValue[Double]("P(e)").get),
      occ.contextualScore, // feature value "P(c|e)" is too small for exp, e.g. exp(-434)== 0 with double precision
      Math.exp(occ.featureValue[Double]("P(s|e)").get),
      occ.featureValue[Double]("P(g|e)").getOrElse(0.0))
  }

  def toTsvVector(vector: DenseVector[Double]): String = {
    vector.activeValuesIterator.foldLeft(new StringBuilder())((builder, score) => builder ++= "\t%.12f".format(score)).toString()
  }

  object EntityStats {
    var correctFound = 0
    var correctNotFound = 0
    var incorrectFound = 0
    var incorrectNotFound = 0

    override def toString(): String = {
      val sum = correctFound + correctNotFound
      if (sum != incorrectFound + incorrectNotFound) {
        "Stats have not been tracked correctly; sum of correct cases (%d) does not equal sum of incorrect cases (%d)!".format(
          sum, incorrectFound + incorrectNotFound)
      }

      "Entity Statistics for %d cases: correct found: %d (%.2f%%), incorrect found:%d (%.2f%%)".format(
        sum,
        correctFound, 100 * correctFound.toDouble / sum,
        incorrectFound, 100 * incorrectFound.toDouble / sum)
    }
  }
}

