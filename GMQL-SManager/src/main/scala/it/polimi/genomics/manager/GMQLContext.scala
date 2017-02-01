package it.polimi.genomics.manager

import it.polimi.genomics.core
import it.polimi.genomics.core.{BinSize, ImplementationPlatform}
import it.polimi.genomics.flink.FlinkImplementation.FlinkImplementation
import it.polimi.genomics.repository.FSRepository.LFSRepository
import it.polimi.genomics.repository.{GMQLRepository, Utilities}
import it.polimi.genomics.spark.implementation.GMQLSparkExecutor
import org.apache.spark.SparkContext

/**
  * Created by abdulrahman on 23/01/2017.
  */
case class GMQLContext(val implPlatform: core.ImplementationPlatform.Value,val gMQLRepository: GMQLRepository,val  outputFormat: core.GMQLOutputFormat.Value, val binSize:BinSize = BinSize(),val username:String = Utilities().USERNAME, sc:SparkContext = null) {
  sc.setLogLevel("INFO")
  def this() = this(core.ImplementationPlatform.SPARK,new LFSRepository(),core.GMQLOutputFormat.TAB)
  def this(gMQLRepository: GMQLRepository) = this(core.ImplementationPlatform.SPARK,gMQLRepository,core.GMQLOutputFormat.TAB)
  def this(gMQLRepository: GMQLRepository,outputFormat: core.GMQLOutputFormat.Value) = this(core.ImplementationPlatform.SPARK,gMQLRepository,outputFormat)

  val implementation = if(implPlatform == ImplementationPlatform.SPARK){
    new GMQLSparkExecutor(binSize = binSize, sc=sc  , outputFormat = outputFormat)
  }else /*if(executionType.toLowerCase() == GMQLExecute.FLINK)*/{
    new FlinkImplementation( binSize=binSize, outputFormat = outputFormat)
  }
}