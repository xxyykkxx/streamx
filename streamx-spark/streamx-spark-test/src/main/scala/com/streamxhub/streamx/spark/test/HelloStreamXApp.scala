/*
 * Copyright (c) 2019 The StreamX Project
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.streamxhub.streamx.spark.test

import com.streamxhub.streamx.spark.connector.kafka.source.KafkaSource
import com.streamxhub.streamx.spark.core.SparkStreaming
import org.apache.spark.SparkConf
import org.apache.spark.streaming.StreamingContext
import scalikejdbc.{ConnectionPool, DB, SQL}

object HelloStreamXApp extends SparkStreaming {

  /**
   * 用户设置sparkConf参数,如,spark序列化:
   * conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
   * // 注册要序列化的自定义类型。
   * conf.registerKryoClasses(Array(classOf[User], classOf[Order],...))
   *
   * @param conf
   */
  override def configure(conf: SparkConf): Unit = {}


  override def handle(ssc: StreamingContext): Unit = {

    val sparkConf = ssc.sparkContext.getConf
    val jdbcURL = sparkConf.get("spark.sink.mysql.jdbc.url")
    val user = sparkConf.get("spark.sink.mysql.user")
    val password = sparkConf.get("spark.sink.mysql.password")

    val source = new KafkaSource[String, String](ssc)

    val line = source.getDStream[String](x => (x.value))

    line.flatMap(_.split(" ")).map(_ -> 1).reduceByKey(_ + _)
      .foreachRDD((rdd, time) => {

        //transform 业务处理
        rdd.foreachPartition(iter => {

          //sink 数据落盘到MySQL
          ConnectionPool.singleton(jdbcURL, user, password)

          //        DB.autoCommit { implicit session =>
          //          val sql =
          //            s"""
          //               |create table if not exists word_count (
          //               |`word` varchar(255),
          //               |`count` int(255),
          //               |UNIQUE INDEX `INX`(`word`)
          //               |)
          //          """.stripMargin
          //          SQL(sql).execute.apply()
          //        }

          DB.localTx(implicit session => {
            iter.foreach(x => {
              val sql = s"replace into word_count(`word`,`count`) values('${x._1}',${x._2})"
              SQL(sql).update()
            })
          })
        })
        //提交offset
        source.updateOffset(time)
      })
  }

}
