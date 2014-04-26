package org.dbpedia.spotlight.learning

import java.io.File

import org.dbpedia.spotlight.corpus.AidaCorpus
import org.dbpedia.spotlight.db._
import org.dbpedia.spotlight.disambiguate.ParagraphDisambiguator
import org.dbpedia.spotlight.disambiguate.mixtures._
import org.dbpedia.spotlight.evaluation.EvalUtils
import org.dbpedia.spotlight.io.AnnotatedTextSource
import org.dbpedia.spotlight.log.SpotlightLog
import org.dbpedia.spotlight.model.{ AnnotatedParagraph, DBpediaResourceOccurrence, Factory, SurfaceFormOccurrence }
import org.dbpedia.spotlight.model.SpotlightConfiguration.DisambiguationPolicy

object CorpusLearner {
  val correctTarget = 1.0
  val incorrectTarget = 0.0

  def main(args: Array[String]) {
    val corpus: AnnotatedTextSource = AidaCorpus.fromFile(new File(args(1))) // /path/to/CoNLL-YAGO.tsv
    SpotlightLog.info(this.getClass(), "Loaded corpus %s with %d documents.", corpus.name, corpus.foldLeft(0)((acc, doc) => acc + 1))

    val db = SpotlightModel.fromFolder(new File(args(0)))
    // add tokenizer for DBTwoStepDisambiguator to prevent exception
    val statDisambiguator = db.disambiguators.get(DisambiguationPolicy.Default).disambiguator.asInstanceOf[DBTwoStepDisambiguator]
    statDisambiguator.tokenizer = db.tokenizer;
    val mergedDisambiguator = db.disambiguators.get(DisambiguationPolicy.Merged).disambiguator

    val featureNormalizer = new MergedSemiLinearFeatureNormalizer()
    val trainingDataHandlers = List(
      new DumpTsvTrainingDataHandler("%s-%s.data.tsv".format(corpus.name, EvalUtils.now())),
      new DumpVowpalTrainingDataHandler("%s-%s.data.vw".format(corpus.name, EvalUtils.now())),
      new LinRegTrainingDataHandler("%s-%s.weights.tsv".format(corpus.name, EvalUtils.now())))

    val corpusLearner = new CorpusLearner(trainingDataHandlers, featureNormalizer)
    corpusLearner.learnFeatureWeights(corpus, mergedDisambiguator)
  }

}

class CorpusLearner(val trainingDataHandlers: List[TrainingDataHandler], val featureNormalizer: FeatureNormalizer) {
  /**
   * for each document of gs
   * 1. get all gold standard spots
   * 2. get bestK for each spot
   * 3. for each gs spot
   * 3.1. bestK: get scores for highest ranked incorrect entity
   * 3.2. bestK: get scores for correct gs entity
   */
  def learnFeatureWeights(corpus: AnnotatedTextSource,
    disambiguator: ParagraphDisambiguator) = {
    val stats = new EntityStats()

    // filter all documents without annotations and all NIL annotations
    //TODO use OccurrenceFilters from EvaluateParagraphDisambiguator 
    val filteredCorpus = corpus.filter(_.occurrences.nonEmpty).map(doc => {
      val filteredOccs = doc.occurrences.filter(!_.resource.uri.equals(AidaCorpus.nilUri))
      new AnnotatedParagraph(doc.id, doc.text, filteredOccs)
    })

    filteredCorpus.foreach(doc => {
      SpotlightLog.info(this.getClass(), "Starting with document %s (%d occurrences)", doc.id, doc.occurrences.size)
      val bestKMap = disambiguator.bestK(Factory.paragraph().from(doc), 10) // get bestK of disambiguator
      doc.occurrences.foreach(goldStandardEntity => {
        val sfo = Factory.SurfaceFormOccurrence.from(goldStandardEntity)
        val bestKEntities = bestKMap(sfo)

        val correctEntity = bestKEntities.find(_.resource.uri.equals(goldStandardEntity.resource.uri))
        handleEntityCase(CorpusLearner.correctTarget, correctEntity, sfo, stats)

        val firstIncorrectEntity = bestKEntities.find(!_.resource.uri.equals(goldStandardEntity.resource.uri))
        handleEntityCase(CorpusLearner.incorrectTarget, firstIncorrectEntity, sfo, stats)
      })
    })
    SpotlightLog.info(this.getClass(), "%s", stats)
    trainingDataHandlers.foreach(_.finish())
  }

  def handleEntityCase(target: Double, entity: Option[DBpediaResourceOccurrence], sfo: SurfaceFormOccurrence, stats: EntityStats) = {
    entity match {
      case Some(occ) => {
        SpotlightLog.debug(this.getClass(), "Found %s target entity %s", target, occ.resource.uri)
        stats.add(target, true)
        val featuresScores = featureNormalizer.fromOcc(occ)
        trainingDataHandlers.foreach(_.addCase(target, featuresScores, occ))
      }
      case None => {
        SpotlightLog.debug(this.getClass(), "No incorrect entity found for %d:%s", sfo.textOffset, sfo.surfaceForm.name)
        stats.add(target, false)
      }
    }
  }
}

class EntityStats {
  var correctFound = 0
  var correctNotFound = 0
  var incorrectFound = 0
  var incorrectNotFound = 0

  def add(target: Double, found: Boolean) = {
    if (target == CorpusLearner.incorrectTarget) {
      if (found) {
        incorrectFound += 1
      } else {
        incorrectNotFound += 1
      }
    } else {
      if (found) {
        correctFound += 1
      } else {
        correctNotFound += 1
      }
    }
  }

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