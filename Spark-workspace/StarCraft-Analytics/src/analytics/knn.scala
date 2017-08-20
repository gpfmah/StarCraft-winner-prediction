package analytics

import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.evaluation.{MulticlassClassificationEvaluator,BinaryClassificationEvaluator}
import org.apache.spark.sql.types.StructType
import org.apache.spark.ml.feature.{IndexToString, StringIndexer, VectorIndexer}
import org.apache.spark.ml.tuning.{CrossValidator, CrossValidatorModel, ParamGridBuilder}
import org.apache.spark.sql.types._
import org.apache.spark.ml.param.ParamMap

import org.apache.spark.sql.types.StructType
import org.apache.spark.ml.tuning.CrossValidatorModel
import org.apache.spark.ml.PipelineModel
import org.apache.spark.ml.evaluation.{BinaryClassificationEvaluator,MulticlassClassificationEvaluator}

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions

import org.apache.spark.ml.classification.KNNClassifier

// OFF logging
import org.apache.log4j.Logger
import org.apache.log4j.Level

object knn {
  def main(args: Array[String]) {
    val spark = SparkSession
      .builder()
      .appName("StarCraft winner prediction - KNN")
      .getOrCreate()
      
    import spark.implicits._

      Logger.getLogger("org").setLevel(Level.WARN)
      Logger.getLogger("akka").setLevel(Level.WARN)

    val dataSchema = new StructType()
      .add("ReplayID","string")
      .add("Duration","int")
      .add("Frame", "int")
      .add("Minerals", "int")
      .add("Gas", "int")
      .add("Supply", "int")
      .add("TotalMinerals", "int")
      .add("TotalGas", "int")
      .add("TotalSupply", "int")
      .add("GroundUnitValue", "int")
      .add("BuildingValue", "int")
      .add("AirUnitValue", "int")
      .add("ObservedEnemyGroundUnitValue", "int")
      .add("ObservedEnemyBuildingValue", "int")
      .add("ObservedEnemyAirUnitValue", "int")
      .add("EnemyMinerals", "int")
      .add("EnemyGas", "int")
      .add("EnemySupply", "int")
      .add("EnemyTotalMinerals", "int")
      .add("EnemyTotalGas", "int")
      .add("EnemyTotalSupply", "int")
      .add("EnemyGroundUnitValue", "int")
      .add("EnemyBuildingValue", "int")
      .add("EnemyAirUnitValue", "int")
      .add("EnemyObservedEnemyGroundUnitValue", "int")
      .add("EnemyObservedEnemyBuildingValue", "int")
      .add("EnemyObservedEnemyAirUnitValue", "int")
      .add("ObservedResourceValue", "double")
      .add("EnemyObservedResourceValue", "double")
      .add("Winner", "string")
      .add("Races", "string")
      


    val assembler = new VectorAssembler()
      .setInputCols(Array("Frame","Minerals","Gas","Supply","TotalMinerals","TotalGas","TotalSupply",
                          "GroundUnitValue","BuildingValue","AirUnitValue",
                          "ObservedEnemyGroundUnitValue","ObservedEnemyBuildingValue","ObservedEnemyAirUnitValue",
                          "EnemyMinerals","EnemyGas","EnemySupply","EnemyTotalMinerals","EnemyTotalGas","EnemyTotalSupply",
                          "EnemyGroundUnitValue","EnemyBuildingValue","EnemyAirUnitValue",
                          "EnemyObservedEnemyGroundUnitValue","EnemyObservedEnemyBuildingValue","EnemyObservedEnemyAirUnitValue",
                          "ObservedResourceValue","EnemyObservedResourceValue"
                          ,"indexedRaces"
                        ))
      .setOutputCol("features")

    println("Loading data")
    val trainData = spark.read.option("header","true")
      .option("inferSchema","false")
      .schema(dataSchema)
      .csv("trainData.csv")

    val rawTestData = spark.read.option("header","true")
      .option("inferSchema","false")
      .schema(dataSchema)
      .csv("testData.csv")
          
    



    val winnerIndexer = new StringIndexer()
      .setInputCol("Winner")
      .setOutputCol("indexedWinner")

    val racesIndexer = new StringIndexer()
      .setInputCol("Races")
      .setOutputCol("indexedRaces")

    // Automatically identify categorical features, and index them.
    // Set maxCategories so features with > 6 distinct values are treated as continuous.
    val featureIndexer = new VectorIndexer()
      .setInputCol("features")
      .setOutputCol("indexedFeatures")
      .setMaxCategories(6)

    // Convert indexed labels back to original labels.
    val labelConverter = new IndexToString()
      .setInputCol("prediction")
      .setOutputCol("predictedLabel")

    val knn = new KNNClassifier()
      .setLabelCol(winnerIndexer.getOutputCol)
      .setFeaturesCol(featureIndexer.getOutputCol)
      .setTopTreeSize(trainData.count().toInt / 500)
      .setK(3)

    
    
    // Chain indexers and forest in a Pipeline.
    val pipeline:Pipeline = new Pipeline()
      .setStages(Array(racesIndexer, winnerIndexer, assembler, featureIndexer, knn))
      
    val knnModel = pipeline fit trainData

    println("Creating evaluators")

    val evaluator = new MulticlassClassificationEvaluator()
      .setMetricName("accuracy")
      .setLabelCol("indexedWinner")
      .setPredictionCol("prediction")

    val evaluatorF1 = new MulticlassClassificationEvaluator()
      .setMetricName("f1")
      .setLabelCol("indexedWinner")
      .setPredictionCol("prediction")

    val evaluatorAUC = new BinaryClassificationEvaluator()
      .setLabelCol("indexedWinner")
      .setRawPredictionCol("rawPrediction")
      
    println("Getting predictions")
    
    println("Filtering Test (if needed)")
       
    var strArgs = ""
    
    args(0).split(" ").map(a => {
      
      println("Testing with frames = " + a)
      
      strArgs = "_" + a
      
      val testData = rawTestData.where($"Frame" < a.toDouble)
      
      val predictions = knnModel transform testData
      
      val accuracy = evaluator evaluate predictions
      
      val AUC = -1
      
      val F1 = evaluatorF1 evaluate predictions
      
      val csv = "KNN\n" +
      accuracy + "\n" +
      AUC + "\n" +
      F1 + "\n"


      new java.io.PrintWriter("measuresKNN" + strArgs + ".csv") { write(csv); close() }
    })
  }
}