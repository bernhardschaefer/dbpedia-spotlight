package org.dbpedia.spotlight.learning

import java.io.{ PrintWriter, FileWriter }
import scala.collection.mutable.ListBuffer
import org.dbpedia.spotlight.log.SpotlightLog
import org.dbpedia.spotlight.model.DBpediaResourceOccurrence
import breeze.linalg.{ DenseMatrix, DenseVector }
import breeze.regress.LinearRegression

trait TrainingDataHandler {
  def addCase(target: Double, features: List[(String, Double)], occ: DBpediaResourceOccurrence)
  def finish()
}

class LinRegTrainingDataHandler(val fileName: String) extends TrainingDataHandler {
  val trainingDataBuffer = new ListBuffer[Pair[Double, List[Double]]]()

  def addCase(target: Double, features: List[(String, Double)], occ: DBpediaResourceOccurrence) = {
    trainingDataBuffer += Pair(target, features.map(_._2))
  }

  def finish() = {
    val trainingData = trainingDataBuffer.toList
    if (trainingData.nonEmpty) {
      val numTargets = trainingData.size
      val numFeatures = trainingData(0)._2.size
      val features = DenseMatrix.tabulate(numTargets, numFeatures)((x, y) => trainingData(x)._2(y))
      val targets = DenseVector.tabulate(numTargets)(i => trainingData(i)._1.toDouble)
      val regressed = LinearRegression.regress(features, targets)
      val weightsTsvString = regressed.activeValuesIterator.mkString("\t")
      SpotlightLog.info(this.getClass(), "Lin Reg Weights: %s", weightsTsvString)
      val writer = new PrintWriter(new FileWriter(fileName))
      writer.println(weightsTsvString)
      writer.flush()
      writer.close()
    }
  }
}

/**
 * Creates a vowpal wabbit compatible training set file.
 */
class DumpVowpalTrainingDataHandler(val fileName: String) extends TrainingDataHandler {
  val writer = new PrintWriter(new FileWriter(fileName))

  def addCase(target: Double, features: List[(String, Double)], occ: DBpediaResourceOccurrence) = {
    // vowpal rabbit input format: https://github.com/JohnLangford/vowpal_wabbit/wiki/Input-format
    // 1 foo[53]->bar| P(e):0.001753937852 P(c/e):0.090909090909 P(s/e):0.443444273363 P(g/e):0.000000000000
    // execute as follows: ./vw file.data.vw --invert_hash file.vw.model
    val featuresString = features.foldLeft(new StringBuilder())((builder, pair) => {
      builder ++= " %s:%.12f".format(pair._1, pair._2)
    }).toString()
    val sfName = occ.surfaceForm.name.replaceAll("\\|", "/").replaceAll(" ", "_")
    writer.println("%f %s[%d]->%s|%s".format(target, sfName, occ.textOffset, occ.resource.uri, featuresString))
  }

  def finish() = {
    writer.flush()
    writer.close()
  }
}

class DumpTsvTrainingDataHandler(val fileName: String) extends TrainingDataHandler {
  val writer = new PrintWriter(new FileWriter(fileName))

  def addCase(target: Double, features: List[(String, Double)], occ: DBpediaResourceOccurrence) = {
    val featureTsvString = features.foldLeft(new StringBuilder())((builder, pair) => {
      builder ++= "\t%.12f".format(pair._2)
    }).toString()
    writer.println(target + featureTsvString)
  }

  def finish() = {
    writer.flush()
    writer.close()
  }
}
