package it.polimi.genomics.dieutth.RegionsOperators.SelectRegions

import it.polimi.genomics.core.DataStructures.RegionOperator
import it.polimi.genomics.core.DataTypes.GRECORD
import it.polimi.genomics.core.exception.SelectFormatException
import it.polimi.genomics.dieutth.GMQLSparkDieuExecutor
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.slf4j.LoggerFactory

/**
  * Created by abdulrahman kaitoua on 25/05/15.
  */
object StoreRD {
  private final val logger = LoggerFactory.getLogger(StoreRD.getClass);
  private final val ENCODING = "UTF-8"

  @throws[SelectFormatException]
  def apply(executor: GMQLSparkDieuExecutor, path: String, value: RegionOperator, sc: SparkContext): RDD[GRECORD] = {
    val input = executor.implement_rd(value, sc)
    input
  }
}
