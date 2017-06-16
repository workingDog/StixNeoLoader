package com.kodekutters.neo4j

import com.kodekutters.stix._
import org.neo4j.driver.v1.Session


/**
  * create Neo4j relations from a Stix object
  *
  */
object RelationsMaker {
  def apply(session: Session) = new RelationsMaker(session)
}

class RelationsMaker(session: Session) {

  import Util._

  val util = new Util(session)

  // create relations from the stix object
  def createRelations(obj: StixObj) = {
    obj match {
      case stix if stix.isInstanceOf[SDO] => createSDORel(stix.asInstanceOf[SDO])
      case stix if stix.isInstanceOf[SRO] => createSRORel(stix.asInstanceOf[SRO])
      case stix if stix.isInstanceOf[StixObj] => createStixObjRel(stix.asInstanceOf[StixObj])
      case _ => // do nothing for now
    }
  }

  // create relations (to other SDO, Marking etc...) for the input SDO
  def createSDORel(x: SDO) = {
    // the object marking relations
    util.createRelToObjRef(x.id.toString(), x.object_marking_refs, "HAS_MARKING")
    // the created_by relation
    util.createCreatedBy(x.id.toString(), x.created_by_ref)

    x.`type` match {

      case Report.`type` =>
        val y = x.asInstanceOf[Report]
        // create relations between the Report id and the list of object_refs SDO id
        util.createRelToObjRef(y.id.toString(), y.object_refs, "REFERS_TO")

      // todo  objects: Map[String, Observable],
      //  case ObservedData.`type` =>
      //    val y = x.asInstanceOf[ObservedData]

      case _ => // do nothing more
    }
  }

  // the Relationship and Sighting
  def createSRORel(x: SRO) = {
    def commonPart() = s"id:'${x.id.toString()}'" +
      s",type:'${x.`type`}'" +
      s",created:'${x.created.time}',modified:'${x.modified.time}'" +
      s",revoked:${x.revoked.getOrElse("false")},labels:${toStringArray(x.labels)}" +
      s",confidence:${x.confidence.getOrElse(0)}" +
      s",external_references:${toIdArray(x.external_references)}" +
      s",lang:'${clean(x.lang.getOrElse(""))}'" +
      s",object_marking_refs:${toStringIds(x.object_marking_refs)}" +
      s",granular_markings:${toIdArray(x.granular_markings)}" +
      s",created_by_ref:'${x.created_by_ref.getOrElse("").toString}'"

    util.createRelToObjRef(x.id.toString(), x.object_marking_refs, "HAS_MARKING")
    // the created_by relation
    util.createCreatedBy(x.id.toString(), x.created_by_ref)

    if (x.isInstanceOf[Relationship]) {
      val y = x.asInstanceOf[Relationship]
      val props = commonPart() +
        s",source_ref:'${y.source_ref.toString()}'" +
        s",target_ref:'${y.target_ref.toString()}'" +
        s",relationship_type:'${clean(y.relationship_type)}'" +
        s",description:'${clean(y.description.getOrElse(""))}'"
      val lbl = asCleanLabel(y.relationship_type) + ":" + asCleanLabel(y.relationship_type)
      val script = s"MATCH (source {id:'${y.source_ref.toString()}'}), (target {id:'${y.target_ref.toString()}'}) " +
        s"CREATE (source)-[$lbl {$props}]->(target)"
      session.run(script)
    }
    else { // a Sighting
      val y = x.asInstanceOf[Sighting]
      val observed_data_ids = toIdArray(y.observed_data_refs)
      val where_sighted_refs_ids = toIdArray(y.where_sighted_refs)
      val props = commonPart() +
        s",sighting_of_ref:'${y.sighting_of_ref.toString}'" +
        s",first_seen:'${y.first_seen.getOrElse("").toString}'" +
        s",last_seen:'${y.last_seen.getOrElse("").toString}'" +
        s",count:${y.count.getOrElse(0)}" +
        s",summary:'${y.summary.getOrElse("")}'" +
        s",observed_data_id:$observed_data_ids" +
        s",where_sighted_refs_id:$where_sighted_refs_ids" +
        s",description:'${clean(y.description.getOrElse(""))}'"
      val lbl = Sighting.`type` + ":" + Sighting.`type`
      val script = s"MATCH (source {id:'${y.sighting_of_ref.toString}'}), (target {id:'${y.sighting_of_ref.toString}'}) " +
        s"CREATE (source)-[$lbl {$props}]->(target)"
      session.run(script)
      util.createObjRefs(y.id.toString(), y.observed_data_refs, observed_data_ids, "OBSERVED_DATA")
      util.createObjRefs(y.id.toString(), y.where_sighted_refs, where_sighted_refs_ids, "WHERE_SIGHTED")
    }
  }

  // convert MarkingDefinition and LanguageContent
  def createStixObjRel(stixObj: StixObj) = {
    stixObj match {
      case x: MarkingDefinition =>
        // the object marking relations
        util.createRelToObjRef(x.id.toString(), x.object_marking_refs, "HAS_MARKING")
        // the created_by relation
        util.createdByRel(x.id.toString(), x.created_by_ref)

      // todo <----- contents: Map[String, Map[String, String]]
      case x: LanguageContent =>
        // the object marking relations
        util.createRelToObjRef(x.id.toString(), x.object_marking_refs, "HAS_MARKING")
        // the created_by relation
        util.createdByRel(x.id.toString(), x.created_by_ref)
    }
  }

}
