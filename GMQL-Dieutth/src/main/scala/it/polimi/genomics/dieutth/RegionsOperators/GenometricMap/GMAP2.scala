package it.polimi.genomics.dieutth.RegionsOperators.GenometricMap

import com.google.common.hash.Hashing
import it.polimi.genomics.core.DataStructures.{MetaJoinOperator, RegionAggregate, RegionOperator}
import it.polimi.genomics.core.DataTypes._
import it.polimi.genomics.core.exception.SelectFormatException
import it.polimi.genomics.core.{DataTypes, GRecordKey}
import it.polimi.genomics.core.{GValue, GString, GDouble}
import org.apache.spark.rdd.RDD
import org.slf4j.LoggerFactory
import scala.collection.Map


/**
 * Created by abdulrahman kaitoua on 08/08/15.
 */
object GMAP2 {
  private final val logger = LoggerFactory.getLogger(this.getClass);

  @throws[SelectFormatException]
  def apply(aggregator : List[RegionAggregate.RegionsToRegion], flat : Boolean, ref : RDD[FlinkRegionType], exp : RDD[(Long, String, Long, Long, Char, Long, Array[GValue])], BINNING_PARAMETER : Long) : RDD[GRECORD] = {
    logger.info("----------------GMAP2 executing..")
    val expBinned = binDS(exp,aggregator,BINNING_PARAMETER)
    val refBinnedRep = binDS(ref,BINNING_PARAMETER)

//    refBinnedRep.collect().foreach(println _)
//    expBinned.collect().foreach(println _)
    val RefExpJoined = refBinnedRep.leftOuterJoin(expBinned)
      .flatMap { grouped =>
      val key = grouped._1;
      val ref = grouped._2._1;
      val exp = grouped._2._2
      val aggregation = Hashing.md5().newHasher().putString(key._1 + key._2 + ref._1 + ref._2+ref._3+ref._4.mkString("/"),java.nio.charset.Charset.defaultCharset()).hash().asLong()
      if (!exp.isDefined) {
        None//(aggregation,((key._1, key._2,ref._1,ref._2,ref._3),ref._4,Array[List[GValue]](),0l,0l,0l,0l))
      } else {
        val e = exp.get
//        println((ref._1 < e._2 && e._1 < ref._2),(ref._3.equals('*') || e._3.equals('*') || ref._3.equals(e._3)),((ref._1/BINNING_PARAMETER).toInt.equals(key._3) ||  (e._1/BINNING_PARAMETER).toInt.equals(key._3)))

        if(/* cross */
        /* space overlapping */
          (ref._1 < e._2 && e._1 < ref._2)
            && /* same strand */
            (ref._3.equals('*') || e._3.equals('*') || ref._3.equals(e._3))
            && /* first comparison */
            ((ref._1/BINNING_PARAMETER).toInt.equals(key._3) ||  (e._1/BINNING_PARAMETER).toInt.equals(key._3))
        )
         Some (aggregation,((key._1,key._2,ref._1,ref._2,ref._3),ref._4,exp.get._4,exp.get._1,exp.get._2,exp.get._1,exp.get._2))
        else None
//          (aggregation,((key._1, key._2,ref._1,ref._2,ref._3),ref._4,Array[List[GValue]](),0l,0l,0l,0l))
      }
    }
    // print out the resutl for debuging
    //    RefExpJoined.collect().map(x=>println ("after left join: "+x._1,x._2._1,x._2._2.mkString("/"),x._2._3.size,x._2._4))

//    RefExpJoined.collect.foreach(println(_))

    val reduced  = RefExpJoined.reduceByKey{(l,r)=>
      val values: Array[List[GValue]] =
        if(l._3.size > 0) {
          (for(i <- (0 to (l._3.size-1)))
            yield
            (l._3(i) :::
              r._3(i))).toArray
        }
          // l._3.zip(r._3).map((a) => a._1 ++ a._2)
        else if(r._3.size >0)
          r._3
        else l._3
      val startMin =Math.min(l._4, r._4)
      val endMax = Math.max(l._5, r._5)
      val startMax = Math.max(l._6, r._6)
      val endMin = Math.min(l._7, r._7)
//      println("min/max ",startMax,endMax)
      (l._1,l._2,values, startMin, endMax, if(startMax < endMin) startMax else 0L, if(endMin > startMax) endMin else 0L)
    }

    //Aggregate Exp Values (reduced)

    val output = reduced.map{res =>
      var i =0;
      val start : Double = if(flat) res._2._4 else res._2._1._3
      val end : Double = if (flat) res._2._5 else res._2._1._4

      val newVal:Array[GValue] = aggregator.map{f=> val out = f.fun(res._2._3(i));i = i+1; out}.toArray
      (new GRecordKey(res._2._1._1,res._2._1._2,start.toLong,end.toLong,res._2._1._5),
        res._2._2  ++ newVal
          // Jaccard 1
          :+ { if(res._2._5-res._2._4 != 0){ GDouble(Math.abs((end.toDouble-start)/(res._2._5-res._2._4))) } else { GDouble(0) } }
          // Jaccard 2
          :+ { if(res._2._5-res._2._4 != 0){ GDouble(Math.abs((res._2._7.toDouble-res._2._6)/(res._2._5-res._2._4))) } else { GDouble(0) } }
        )
    }

    output

    //    }else None
  }
  def binDS(rdd : RDD[(Long, String, Long, Long, Char, Long, Array[GValue])],aggregator: List[RegionAggregate.RegionsToRegion],bin: Long )
  : RDD[((Long, String, Int), (Long, Long, Char, Array[List[GValue]]))] =
    rdd.flatMap { x =>

      val startbin = (x._3 / bin).toInt
      val stopbin = (x._4 / bin).toInt
      val newVal: Array[List[GValue]] = aggregator
        .map((f : RegionAggregate.RegionsToRegion) => {
        List(x._7(f.index))
      }).toArray
      ( startbin to stopbin).map( i=>
        ((x._1, x._2, i), (x._3, x._4, x._5, newVal))
      )
    }
  def binDS(rdd:RDD[FlinkRegionType],bin: Long )
  : RDD[((Long, String, Int), ( Long, Long, Char, Array[GValue]))] =
    rdd.flatMap { x =>

      val startbin = (x._3 / bin).toInt
      val stopbin = (x._4 / bin).toInt
      ( startbin to stopbin).map( i=>
        ((x._1, x._2, i), (x._3, x._4, x._5, x._6))
      )
    }

}