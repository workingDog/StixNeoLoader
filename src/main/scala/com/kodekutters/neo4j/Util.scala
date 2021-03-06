package com.kodekutters.neo4j

import org.neo4j.driver.v1.Session
import java.io.{File, InputStream, _}
import java.util.UUID

import com.kodekutters.stix._
import com.kodekutters.stix.Bundle
import play.api.libs.json.Json

import scala.io.Source
import scala.language.implicitConversions
import scala.language.postfixOps

object Util {

  val objectRefs = "object_refs"
  val observedDataRefs = "observed_data_refs"
  val whereSightedRefs = "where_sighted_refs"
  val markingObjRefs = "marking_object_refs"
  val createdByRefs = "created_by"

  // the file to write the cypher statements to
  var pw: PrintWriter = _

  var cypherFile: String = _

  def apply(session: Session) = new Util(session)

  def setCypherFile(theFile: String) {
    cypherFile = theFile
    if (cypherFile != null) {
      println("cypher statements will be in: " + cypherFile)
      pw = new PrintWriter(new java.io.File(cypherFile))
    }
  }

  def close() {
    if (pw != null) pw.close()
  }

  /**
    * read a Bundle from the input source
    *
    * @param source the input InputStream
    * @return a Bundle option
    */
  def loadBundle(source: InputStream): Option[Bundle] = {
    // read a STIX bundle from the InputStream
    val jsondoc = Source.fromInputStream(source).mkString
    // create a bundle object from it
    Json.fromJson[Bundle](Json.parse(jsondoc)).asOpt match {
      case None => println("-----> ERROR invalid bundle JSON in zip file: \n"); None
      case Some(bundle) => Option(bundle)
    }
  }

  // clean the string, i.e. escape special char
  def clean(s: String) = s.replace("\\", """\\""").replace("'", """\'""").
    replace("\"", """\""").replace("\n", """\n""").replace("\r", """\r""").
    replace("\b", """\b""").replace("\f", """\f""").replace("\t", """\t""")

  // make an array of id values from the input list
  def toIdArray(dataList: Option[List[Any]]) = {
    val t = for (s <- dataList.getOrElse(List.empty)) yield s"'${UUID.randomUUID().toString}'" + ","
    if (t.nonEmpty) "[" + t.mkString.reverse.substring(1).reverse + "]" else "[]"
  }

  // make an array of cleaned string values from the input list
  def toStringArray(dataList: Option[List[String]]) = {
    val t = for (s <- dataList.getOrElse(List.empty)) yield s"'${clean(s)}'" + ","
    if (t.nonEmpty) "[" + t.mkString.reverse.substring(1).reverse + "]" else "[]"
  }

  // make an array of id strings --> no cleaning done here
  def toStringIds(dataList: Option[List[Identifier]]) = {
    val t = for (s <- dataList.getOrElse(List.empty)) yield s"'${s.toString()}'" + ","
    if (t.nonEmpty) "[" + t.mkString.reverse.substring(1).reverse + "]" else "[]"
  }

  // the Neo4j :LABEL and :TYPE cannot deal with "-", so clean and replace with "_"
  def asCleanLabel(s: String) = clean(s).replace("-", "_").replace(":", "").replace(";", "").replace(",", "")

}

class Util(session: Session) {

  import Util._

  def runScript(theScript: String) = {
    if (session != null) session.run(theScript)
    if (pw != null) pw.write(theScript)
  }

  // create the created-by relation between idString and the Identifier
  def createCreatedBy(idString: String, tgtOpt: Option[Identifier]) = {
    tgtOpt.map(tgt => {
      val relScript = s"MATCH (source {id:'$idString'}), (target {id:'${tgt.toString()}'}) " +
        s"CREATE (source)-[:CREATED_BY]->(target)"
      runScript(relScript)
    })
  }

  // create the marking object
  def createMarkingObjRefs(idString: String, definition: MarkingObject, definition_id: String) = {
    val mark: String = definition match {
      case s: StatementMarking => clean(s.statement)
      case s: TPLMarking => clean(s.tlp.value)
      case _ => ""
    }
    val nodeScript = s"CREATE (${Util.markingObjRefs}:${Util.markingObjRefs} {marking_id:'$definition_id',marking:'$mark'})"
    runScript(nodeScript)
    // write the markingObj relationships with the given id
    val relScript = s"MATCH (source {id:'$idString'}), (target {marking_id:'$definition_id'}) " +
      s"CREATE (source)-[:HAS_MARKING_OBJECT]->(target)"
    runScript(relScript)
  }

  // create the kill_chain_phases
  def createKillPhases(idString: String, kill_chain_phases: Option[List[KillChainPhase]], kill_chain_phases_ids: String) = {
    val killphases = for (s <- kill_chain_phases.getOrElse(List.empty))
      yield (clean(s.kill_chain_name), clean(s.phase_name), asCleanLabel(s.`type`))
    if (killphases.nonEmpty) {
      val temp = kill_chain_phases_ids.replace("[", "").replace("]", "")
      val kp = (temp.split(",") zip killphases).foreach({ case (a, (b, c, d)) =>
        val script = s"CREATE ($d:kill_chain_phase {kill_chain_phase_id:$a,kill_chain_name:'$b',phase_name:'$c'})"
        runScript(script)
      })
      for (k <- temp.split(",")) {
        val relScript = s"MATCH (source {id:'$idString'}), (target {kill_chain_phase_id:$k}) " +
          s"CREATE (source)-[:HAS_KILL_CHAIN_PHASE]->(target)"
        runScript(relScript)
      }
    }
  }

  // create the external_references
  def createExternRefs(idString: String, external_references: Option[List[ExternalReference]], external_references_ids: String) = {
    val externRefs = for (s <- external_references.getOrElse(List.empty))
      yield (clean(s.source_name), clean(s.description.getOrElse("")),
        clean(s.url.getOrElse("")), clean(s.external_id.getOrElse("")), asCleanLabel(s.`type`))
    if (externRefs.nonEmpty) {
      val temp = external_references_ids.replace("[", "").replace("]", "")
      val kp = (temp.split(",") zip externRefs).foreach(
        { case (a, (b, c, d, e, f)) =>
          val script = s"CREATE ($f:external_reference {external_reference_id:$a" +
            s",source_name:'$b',description:'$c',url:'$d',external_id:'$e'})"
          runScript(script)
        }
      )
      // write the external_reference relationships with the given ids
      for (k <- temp.split(",")) {
        val relScript = s"MATCH (source {id:'$idString'}), (target {external_reference_id:$k}) " +
          s"CREATE (source)-[:HAS_EXTERNAL_REF]->(target)"
        runScript(relScript)
      }
    }
  }

  // create the granular_markings
  def createGranulars(idString: String, granular_markings: Option[List[GranularMarking]], granular_markings_ids: String) = {
    val granulars = for (s <- granular_markings.getOrElse(List.empty))
      yield (toStringArray(Option(s.selectors)), clean(s.marking_ref.getOrElse("")),
        clean(s.lang.getOrElse("")), asCleanLabel(s.`type`))
    if (granulars.nonEmpty) {
      val temp = granular_markings_ids.replace("[", "").replace("]", "")
      val kp = (temp.split(",") zip granulars).foreach(
        { case (a, (b, c, d, e)) =>
          val script = s"CREATE ($e:granular_marking {granular_marking_id:$a" +
            s",selectors:$b,marking_ref:'$c',lang:'$d'})"
          runScript(script)
        }
      )
      // write the granular_markings relationships with the given ids
      for (k <- temp.split(",")) {
        val relScript = s"MATCH (source {id:'$idString'}), (target {granular_marking_id:$k}) " +
          s"CREATE (source)-[:HAS_GRANULAR_MARKING]->(target)"
        runScript(relScript)
      }
    }
  }

  // create the object_refs
  def createObjRefs(idString: String, object_refs: Option[List[Identifier]], object_refs_ids: String, typeName: String) = {
    val objRefs = for (s <- object_refs.getOrElse(List.empty)) yield clean(s.toString())
    if (objRefs.nonEmpty) {
      val temp = object_refs_ids.replace("[", "").replace("]", "")
      val kp = (temp.split(",") zip objRefs).foreach({ case (a, b) =>
        val script = s"CREATE (${asCleanLabel(typeName)} {object_ref_id:$a,identifier:'$b'})"
        runScript(script)
      })
      // write the object_refs relationships with the given ids
      for (k <- temp.split(",")) {
        val relScript = s"MATCH (source {id:'$idString'}), (target {object_ref_id:$k}) " +
          s"CREATE (source)-[:$typeName]->(target)"
        runScript(relScript)
      }
    }
  }

  // create relations between the idString and the list of object_refs SDO id
  def createRelToObjRef(idString: String, object_refs: Option[List[Identifier]], relName: String) = {
    for (s <- object_refs.getOrElse(List.empty)) {
      val relScript = s"MATCH (source {id:'$idString'}), (target {id:'${s.toString()}'}) " +
        s"CREATE (source)-[:$relName]->(target)"
      runScript(relScript)
    }
  }

  // create the created-by-ref relation
  def createdByRel(sourceId: String, tgtOpt: Option[Identifier]) = {
    tgtOpt.map(tgt => {
      val relScript = s"MATCH (source {id:'$sourceId'}), (target {id:'${tgt.toString()}'}) " +
        s"CREATE (source)-[:CREATED_BY]->(target)"
      runScript(relScript)
    }
    )
  }
}
