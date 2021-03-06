package org.sameersingh.ervisualizer.data

import com.typesafe.scalalogging.slf4j.Logging

import scala.collection.mutable
import scala.collection.mutable.HashMap

/**
 * @author sameer
 * @since 1/25/15.
 */
class DBStore(docs: DocumentStore) extends Logging {
  type Id = String

  val maxDBs = 20
  val dbMap = new HashMap[Id, DB]
  val dbQueue = new mutable.Queue[Id]()
  val queryMap = new HashMap[String, Id]
  val queryIdMap = new HashMap[Id, String]

  def query(string: String): (Id, DB) = {
    val id = queryId(string)
    val odb = dbMap.get(id)
    id -> odb.getOrElse({
      val docIds = docs.query(string)
      val result = EntityInfoReader.read()
      logger.info("Reading " + docIds.size + " docs.")
      val inDB = result.asInstanceOf[InMemoryDB]
      NLPReader.readDocs(docIds.map(id => docs(id)).iterator, inDB)
      NLPReader.addRelationInfo(inDB)
      NLPReader.removeSingletonEntities(inDB)
      val freeMem = (Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory()) / (1024 * 1024 * 1024)
      logger.info("Free memory  (Kbytes): " + Runtime.getRuntime().freeMemory() / 1024)
      logger.info("Total memory (Kbytes): " + Runtime.getRuntime().totalMemory() / 1024)
      logger.info("Max memory   (Kbytes): " + Runtime.getRuntime().maxMemory() / 1024)
      logger.info("Free memory (GBs): " + freeMem + ", DBs: " + dbMap.size)
      if(dbMap.size >=1 && (dbMap.size >= maxDBs || freeMem < 1)) {
        val id = dbQueue.dequeue()
        logger.info("Dequeuing " + id + " for query: \"" + queryIdMap(id) + "\"")
        dbMap.remove(id)
      }
      dbQueue += id
      dbMap(id) = result
      result
    })
  }

  def id(id: String) = {
    dbMap.getOrElse(id, query(queryIdMap(id))._2)
  }

  private def queryId(string: String): Id = {
    queryMap.getOrElseUpdate(string, {
      val id = "db" + queryMap.size
      queryIdMap(id) = string
      id
    })
  }

}
