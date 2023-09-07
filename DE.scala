// Databricks notebook source
// MAGIC %scala
// MAGIC
// MAGIC // Ensure Spark is correctly initialized before loading data
// MAGIC import org.apache.spark.sql.SparkSession
// MAGIC val spark = SparkSession.builder.appName("PowerAnalysis").getOrCreate()
// MAGIC
// MAGIC // Load the CSV file
// MAGIC val df = spark.read
// MAGIC   .format("csv")
// MAGIC   .option("inferSchema", "false")
// MAGIC   .option("header", "true")
// MAGIC   .load("/FileStore/tables/Folds5x2_pp.csv")
// MAGIC
// MAGIC // Display the DataFrame
// MAGIC display(df)
// MAGIC

// COMMAND ----------

// MAGIC %scala
// MAGIC
// MAGIC df .printSchema()

// COMMAND ----------

df.select("AT","V","AP","RH","PE").describe().show()

// COMMAND ----------

// Create Temporary View so we can perform Spark SQL on Data
df.createOrReplaceTempView("PowerAnalysis")

// COMMAND ----------

// MAGIC %sql
// MAGIC
// MAGIC select * from PowerAnalysis;

// COMMAND ----------

// MAGIC %md
// MAGIC ## Visualizing the Data
// MAGIC
// MAGIC To understand our data, we will look for correlations between features and the label. This can be important when choosing a model. For example, if features and a label are linearly correlated, a linear model like Linear Regression can perform well. If the relationship is very non-linear, more complex models such as Decision Trees can be better suited.
// MAGIC
// MAGIC We can use Databrick’s built-in visualization tools to view each of our predictors in relation to the label column as a scatter plot to see the correlation between the predictors and the label.
// MAGIC
// MAGIC **Exploratory Data Analysis (EDA)** is an approach/philosophy for data analysis that employs a variety of techniques (mostly graphical) to:
// MAGIC
// MAGIC - Maximize insight into a data set.
// MAGIC - Uncover underlying structure.
// MAGIC - Extract important variables.
// MAGIC - Detect outliers and anomalies.
// MAGIC - Test underlying assumptions.
// MAGIC - Develop parsimonious models.
// MAGIC - Determine optimal factor settings.
// MAGIC
// MAGIC
// MAGIC

// COMMAND ----------

// MAGIC %md
// MAGIC ## Temperature vs Power

// COMMAND ----------

// MAGIC %sql
// MAGIC
// MAGIC select AT as Temperature, PE as Power from PowerAnalysis

// COMMAND ----------

// MAGIC %md
// MAGIC ## Power VS Exhaust Vaccum

// COMMAND ----------

// MAGIC %sql
// MAGIC
// MAGIC select PE as Power, V as ExhaustVaccum from PowerAnalysis 

// COMMAND ----------

// MAGIC %md
// MAGIC ## Power VS Pressure

// COMMAND ----------

// MAGIC %sql
// MAGIC
// MAGIC select PE as Power, AP as Pressure from PowerAnalysis 

// COMMAND ----------

// MAGIC %md
// MAGIC ## Power VS Humidity

// COMMAND ----------

// MAGIC %sql
// MAGIC
// MAGIC select PE as Power, AP as Pressure from PowerAnalysis 
// MAGIC

// COMMAND ----------

// MAGIC %md
// MAGIC ## Split the Data
// MAGIC It is common practice when building machine learning models to split the source data, using some of it to train the model and reserving some to test the trained model. In this project, you will use 80% of the data for training, and reserve 20% for testing.

// COMMAND ----------

import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

// Assuming your DataFrame is named "df"

// Convert string columns to numeric and assign them to a new DataFrame
val dfNumeric = df
  .withColumn("AT", df("AT").cast(DoubleType))
  .withColumn("V", df("V").cast(DoubleType))
  .withColumn("AP", df("AP").cast(DoubleType))
  .withColumn("RH", df("RH").cast(DoubleType))
  .withColumn("PE", df("PE").cast(DoubleType)) // Cast the "PE" column as well


dfNumeric.printSchema()

val train_rows = train.count()
val test_rows = test.count()
println("Training Rows: " + train_rows + " Testing Rows: " + test_rows)

// COMMAND ----------

// MAGIC %md
// MAGIC ## Prepare the Training Data
// MAGIC To train the Regression model, you need a training data set that includes a vector of numeric features, and a label column. In this project, you will use the VectorAssembler class to transform the feature columns into a vector, and then rename the PE column to the label.
// MAGIC
// MAGIC ## VectorAssembler()
// MAGIC VectorAssembler(): is a transformer that combines a given list of columns into a single vector column. It is useful for combining raw features and features generated by different feature transformers into a single feature vector, in order to train ML models like logistic regression and decision trees.
// MAGIC
// MAGIC VectorAssembler accepts the following input column types: all numeric types, boolean type, and vector type.
// MAGIC
// MAGIC In each row, the values of the input columns will be concatenated into a vector in the specified order.

// COMMAND ----------


// Split the data into training (80%) and testing (20%)
val Array(train, test) = dfNumeric.randomSplit(Array(0.8, 0.2), seed = 12345)

// Define the feature columns
val featureColumns = Array("AT", "V", "AP", "RH")

// Create a VectorAssembler instance
val assembler = new VectorAssembler()
  .setInputCols(featureColumns)
  .setOutputCol("features")
  .setHandleInvalid("keep") // or "skip" if you prefer to skip rows with null values


// Transform the training data

val trainWithFeatures = assembler.transform(train).select($"features", $"PE".alias("label"))
trainWithFeatures.show(false)



// COMMAND ----------

// Assuming trainWithFeatures is a DataFrame with a header row
// Drop the first row (header row) and then remove rows with NaN values
val cleanedTrainWithFeatures = trainWithFeatures.filter(col("AT").isNotNull && col("V").isNotNull && col("AP").isNotNull && col("RH").isNotNull && col("PE").isNotNull)

// Display the cleaned DataFrame
cleanedTrainWithFeatures.show(false)



// COMMAND ----------

// MAGIC %md
// MAGIC ## Train a Regression Model
// MAGIC Next, you need to train a Regression model using the training data. To do this, create an instance of the Linear Regression algorithm you want to use and use its fit method to train a model based on the training DataFrame. In this project, you will use a Logistic Regression Classifier algorithm – though you can use the same technique for any of the regression algorithms supported in the spark.ml API

// COMMAND ----------

import org.apache.spark.ml.regression.LinearRegression


val lr = new LinearRegression()
  .setLabelCol("label")
  .setFeaturesCol("features")
  .setMaxIter(10)
  .setRegParam(0.3)

val model = lr.fit(cleanedTrainWithFeatures) // Use the 'training' DataFrame for model training

println("Model Trained!")


// COMMAND ----------

// MAGIC %md
// MAGIC ## Prepare the Testing Data
// MAGIC Now that you have a trained model, you can test it using the testing data you reserved previously. First, you need to prepare the testing data in the same way as you did the training data by transforming the feature columns into a vector. This time you’ll rename the PE column to trueLabel.

// COMMAND ----------

// Create a VectorAssembler instance
val assembler = new VectorAssembler()
  .setInputCols(featureColumns)
  .setOutputCol("features")
  .setHandleInvalid("keep") // or "skip" if you prefer to skip rows with null values


// Transform the training data

val testWithFeatures = assembler.transform(test).select($"features", $"PE".alias("Truelabel"))
testWithFeatures.show(false)

// COMMAND ----------

// MAGIC %md
// MAGIC ## Test the Model
// MAGIC
// MAGIC Now you’re ready to use the transform method of the model to generate some predictions. You can use this approach to predict the PE; but in this case, you are using the test data which includes a known true label value, so you can compare the PE
// MAGIC

// COMMAND ----------


val prediction = model.transform(testWithFeatures)
val predicted = prediction.select("features", "prediction", "TrueLabel")
predicted.show()


// COMMAND ----------

// MAGIC %md
// MAGIC ## Examine the Predicted and Actual Values
// MAGIC You can plot the predicted values against the actual values to see how accurately the model has predicted. In a perfect model, the resulting scatter plot should form a perfect diagonal line with each predicted value being identical to the actual value – in practice, some variance is to be expected. Run the cells below to create a temporary table from the predicted DataFrame and then retrieve the predicted and actual label values using SQL. You can then display the results as a scatter plot, specifying – as the function to show the unaggregated values.

// COMMAND ----------

predicted.createOrReplaceTempView("regressionPredictions")

// COMMAND ----------

// MAGIC %sql
// MAGIC
// MAGIC SELECT trueLabel, prediction FROM regressionPredictions

// COMMAND ----------

// MAGIC %md
// MAGIC ## Retrieve the Root Mean Square Error (RMSE)
// MAGIC There are a number of metrics used to measure the variance between predicted and actual values. Of these, the root mean square error (RMSE) is a commonly used value that is measured in the same units as the predicted and actual values – so in this case, the RMSE indicates the average number of minutes between predicted and actual PE values. You can use the RegressionEvaluator class to retrieve the RMSE.

// COMMAND ----------

import org.apache.spark.ml.evaluation.RegressionEvaluator

val evaluator = new RegressionEvaluator().setLabelCol("Truelabel").setPredictionCol("prediction").setMetricName("rmse")
val rmse = evaluator.evaluate(prediction)
println("Root Mean Square Error (RMSE): " + (rmse))
