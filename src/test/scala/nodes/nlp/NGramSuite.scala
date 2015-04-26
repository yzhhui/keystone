package nodes.nlp

import pipelines.{LocalSparkContext, Transformer}

import org.apache.spark.SparkContext

import org.scalatest.FunSuite

class NGramSuite extends FunSuite with LocalSparkContext {

  val tokenizer = Transformer { x: String => x.split(" ").toSeq }

  test("NGramsFeaturizer") {
    sc = new SparkContext("local[2]", "NGramSuite")
    val rdd = sc.parallelize(Seq("Pipelines are awesome", "NLP is awesome"), 2)

    def run(orders: Seq[Int]) = {
      val pipeline = tokenizer then
        new NGramsFeaturizer(orders)

      pipeline(rdd)
        .mapPartitions(_.flatten) // for comparison
        .collect()
        .toSeq.map(_.toSeq) // for comparison
    }

    val unigrams = Seq(
      Seq("Pipelines"), Seq("are"), Seq("awesome"),
      Seq("NLP"), Seq("is"), Seq("awesome")
    )
    assert(run(Seq(1)) === unigrams)

    val bigramTrigrams = Seq(
      Seq("Pipelines", "are"), Seq("Pipelines", "are", "awesome"), Seq("are", "awesome"),
      Seq("NLP", "is"), Seq("NLP", "is", "awesome"), Seq("is", "awesome")
    )
    assert(run(2 to 3) === bigramTrigrams)

    assert(run(Seq(6)) === Seq.empty, "returns 6-grams when there aren't any")
  }

  test("NGramsCounts") {
    sc = new SparkContext("local[2]", "NGramSuite")
    val rdd = sc.parallelize(Seq("Pipelines are awesome", "NLP is awesome"), 2)

    def run(orders: Seq[Int]) = {
      val pipeline = tokenizer then
        new NGramsFeaturizer(orders) then
        new NGramsCounts

      pipeline(rdd).collect().toSet
    }

    def liftToNGram(tuples: Set[(Seq[String], Int)]) =
      tuples.map { case (toks, count) => (new NGram[String](toks), count) }

    val unigramCounts = Set((Seq("awesome"), 2),
      (Seq("Pipelines"), 1), (Seq("are"), 1), (Seq("NLP"), 1), (Seq("is"), 1))

    assert(run(Seq(1)) === liftToNGram(unigramCounts),
      "unigrams incorrectly counted")
    assert(run(2 to 3).forall(_._2 == 1),
      "some 2-gram or 3-gram occurs once but is incorrectly counted")
  }

  test("NGramsCounts (noAdd)") {
    implicit val ngramOrdering = new Ordering[NGram[String]] {
      def compare(a: NGram[String], b: NGram[String]): Int = {
        if (a.words.length != b.words.length) -1
        else if (a.words.length > b.words.length) 1
        else {
          a.words.zip(b.words).foreach { case (x, y) =>
            if (x.compare(y) < 0) return -1
            else if (x.compare(y) > 0) return 1
          }
          0
        }
      }
    }

    sc = new SparkContext("local[2]", "NGramSuite")
    val rdd = sc.parallelize(Seq("Pipelines are awesome", "NLP is awesome"), 2)

    def run(orders: Seq[Int]) = {
      val pipeline = tokenizer then
        new NGramsFeaturizer(orders) then
        new NGramsCounts("noAdd")

      pipeline(rdd).collect().toSeq.sortBy(_._1)
    }

    def liftToNGram(tuples: Seq[(Seq[String], Int)]) =
      tuples.map { case (toks, count) => (new NGram[String](toks), count) }
        .sortBy(_._1)

    val unigramCounts = Seq((Seq("awesome"), 1), (Seq("awesome"), 1),
      (Seq("Pipelines"), 1), (Seq("are"), 1), (Seq("NLP"), 1), (Seq("is"), 1))

    assert(run(Seq(1)) === liftToNGram(unigramCounts),
      "unigrams incorrectly counted")
    assert(run(2 to 3).forall(_._2 == 1),
      "some 2-gram or 3-gram occurs once but is incorrectly counted")
  }

}
