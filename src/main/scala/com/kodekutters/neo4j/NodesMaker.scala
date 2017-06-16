package com.kodekutters.neo4j

import java.util.UUID

import com.kodekutters.stix._
import org.neo4j.driver.v1.Session


/**
  * create Neo4j nodes and internal relations from a Stix object
  *
  */
object NodesMaker {
  def apply(session: Session) = new NodesMaker(session)
}

/**
  * create Neo4j nodes and internal relations from a Stix object
  *
  */
class NodesMaker(session: Session) {

  import Util._

  val util = new Util(session)

  // create nodes and internal relations from a Stix object
  def createNodes(obj: StixObj) = {
    obj match {
      case stix if stix.isInstanceOf[SDO] => createSDONode(stix.asInstanceOf[SDO])
      case stix if stix.isInstanceOf[SRO] => createSRONode(stix.asInstanceOf[SRO])
      case stix if stix.isInstanceOf[StixObj] => createStixObjNode(stix.asInstanceOf[StixObj])
      case _ => // do nothing for now
    }
  }

  // create nodes and internal relations from a SDO
  def createSDONode(x: SDO) = {
    // common elements
    val labelsString = Util.toStringArray(x.labels)
    val granular_markings_ids = toIdArray(x.granular_markings)
    val external_references_ids = toIdArray(x.external_references)
    val object_marking_refs_arr = toStringIds(x.object_marking_refs)
    val nodeAndLabel = asCleanLabel(x.`type`) + ":" + asCleanLabel(x.`type`) + ":SDO"

    def commonPart() = s"CREATE ($nodeAndLabel {id:'${x.id.toString()}'" +
      s",type:'${x.`type`}'" +
      s",created:'${x.created.time}',modified:'${x.modified.time}'" +
      s",revoked:${x.revoked.getOrElse("false")},labels:$labelsString" +
      s",confidence:${x.confidence.getOrElse(0)}" +
      s",external_references:$external_references_ids" +
      s",lang:'${clean(x.lang.getOrElse(""))}'" +
      s",object_marking_refs:$object_marking_refs_arr" +
      s",granular_markings:$granular_markings_ids" +
      s",created_by_ref:'${x.created_by_ref.getOrElse("").toString}'"

    x.`type` match {

      case AttackPattern.`type` =>
        val y = x.asInstanceOf[AttackPattern]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        val script = commonPart() +
          s",name:'${clean(y.name)}',description:'${clean(y.description.getOrElse(""))}'" +
          s",kill_chain_phases:$kill_chain_phases_ids" + "})"
        session.run(script)
        util.createKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      case Identity.`type` =>
        val y = x.asInstanceOf[Identity]
        val script = commonPart() +
          s",name:'${clean(y.name)}',identity_class:'${clean(y.identity_class)}'" +
          s",sectors:${toStringArray(y.sectors)}" +
          s",contact_information:'${clean(y.contact_information.getOrElse(""))}'" +
          s",description:'${clean(y.description.getOrElse(""))}'" + "})"
        session.run(script)

      case Campaign.`type` =>
        val y = x.asInstanceOf[Campaign]
        val script = commonPart() +
          s",name:'${clean(y.name)}',objective:'${clean(y.objective.getOrElse(""))}'" +
          s",aliases:${toStringArray(y.aliases)}" +
          s",first_seen:'${clean(y.first_seen.getOrElse("").toString)}" +
          s",last_seen:'${clean(y.last_seen.getOrElse("").toString)}" +
          s",description:'${clean(y.description.getOrElse(""))}'" + "})"
        session.run(script)

      case CourseOfAction.`type` =>
        val y = x.asInstanceOf[CourseOfAction]
        val script = commonPart() +
          s",name:'${clean(y.name)}',description:'${clean(y.description.getOrElse(""))}'" + "})"
        session.run(script)

      case IntrusionSet.`type` =>
        val y = x.asInstanceOf[IntrusionSet]
        val script = commonPart() +
          s",name:'${clean(y.name)}',description:'${clean(y.description.getOrElse(""))}'" +
          s",aliases:${toStringArray(y.aliases)}" +
          s",first_seen:'${clean(y.first_seen.getOrElse("").toString)}'" +
          s",last_seen:'${clean(y.last_seen.getOrElse("").toString)}'" +
          s",goals:${toStringArray(y.goals)}" +
          s",resource_level:'${clean(y.resource_level.getOrElse(""))}'" +
          s",primary_motivation:'${clean(y.primary_motivation.getOrElse(""))}'" +
          s",secondary_motivations:${toStringArray(y.secondary_motivations)}" + "})"
        session.run(script)

      case Malware.`type` =>
        val y = x.asInstanceOf[Malware]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        val script = commonPart() +
          s",name:'${clean(y.name)}',description:'${clean(y.description.getOrElse(""))}'" +
          s",kill_chain_phases:$kill_chain_phases_ids" + "})"
        session.run(script)
        util.createKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      case Report.`type` =>
        val y = x.asInstanceOf[Report]
        val object_refs_ids = toIdArray(y.object_refs)
        val script = commonPart() +
          s",name:'${clean(y.name)}',published:'${y.published.toString()}'" +
          s",object_refs_ids:$object_refs_ids" +
          s",description:'${clean(y.description.getOrElse(""))}'" + "})"
        session.run(script)

      case ThreatActor.`type` =>
        val y = x.asInstanceOf[ThreatActor]
        val script = commonPart() +
          s",name:'${clean(y.name)}',description:'${clean(y.description.getOrElse(""))}'" +
          s",aliases:${toStringArray(y.aliases)}" +
          s",roles:${toStringArray(y.roles)}" +
          s",goals:${toStringArray(y.goals)}" +
          s",sophistication:'${clean(y.sophistication.getOrElse(""))}'" +
          s",resource_level:'${clean(y.resource_level.getOrElse(""))}'" +
          s",primary_motivation:'${clean(y.primary_motivation.getOrElse(""))}'" +
          s",secondary_motivations:${toStringArray(y.secondary_motivations)}" +
          s",personal_motivations:${toStringArray(y.personal_motivations)}" + "})"
        session.run(script)

      case Tool.`type` =>
        val y = x.asInstanceOf[Tool]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        val script = commonPart() +
          s",name:'${clean(y.name)}',description:'${clean(y.description.getOrElse(""))}'" +
          s",kill_chain_phases:$kill_chain_phases_ids" +
          s",tool_version:'${clean(y.tool_version.getOrElse(""))}'" + "})"
        session.run(script)
        util.createKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      case Vulnerability.`type` =>
        val y = x.asInstanceOf[Vulnerability]
        val script = commonPart() +
          s",name:'${clean(y.name)}',description:'${clean(y.description.getOrElse(""))}'" + "})"
        session.run(script)

      case Indicator.`type` =>
        val y = x.asInstanceOf[Indicator]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        val script = commonPart() +
          s",name:'${clean(y.name.getOrElse(""))}',description:'${clean(y.description.getOrElse(""))}'" +
          s",pattern:'${clean(y.pattern)}'" +
          s",valid_from:'${y.valid_from.toString()}'" +
          s",valid_until:'${clean(y.valid_until.getOrElse("").toString)}'" +
          s",kill_chain_phases:$kill_chain_phases_ids" + "})"
        session.run(script)
        util.createKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      // todo  objects: Map[String, Observable],
      case ObservedData.`type` =>
        val y = x.asInstanceOf[ObservedData]
        val script = commonPart() +
          s",first_observed:'${y.first_observed.toString()}'" +
          s",last_observed:'${y.last_observed.toString()}'" +
          s",number_observed:'${y.number_observed}'" +
          s",description:'${clean(y.description.getOrElse(""))}'" + "})"
        session.run(script)

      case _ => // do nothing for now
    }

    // create the external_references
    util.createExternRefs(x.id.toString(), x.external_references, external_references_ids)
    // create the granular_markings
    util.createGranulars(x.id.toString(), x.granular_markings, granular_markings_ids)

  }

  // the Relationship and Sighting
  def createSRONode(x: SRO) = {
    val nodeAndLabel = asCleanLabel(x.`type`) + "_node:" + asCleanLabel(x.`type`) + "_node:SRO"
    val script = s"CREATE ($nodeAndLabel {id:'${x.id.toString()}',type:'${x.`type`}'})"
    session.run(script)
    // create the external_references
    util.createExternRefs(x.id.toString(), x.external_references, toIdArray(x.external_references))
    // create the granular_markings
    util.createGranulars(x.id.toString(), x.granular_markings, toIdArray(x.granular_markings))
  }

  // convert MarkingDefinition and LanguageContent
  def createStixObjNode(stixObj: StixObj) = {

    stixObj match {

      case x: MarkingDefinition =>
        val definition_id = UUID.randomUUID().toString
        val granular_markings_ids = toIdArray(x.granular_markings)
        val external_references_ids = toIdArray(x.external_references)
        val object_marking_refs_arr = toStringIds(x.object_marking_refs)
        val nodeAndLabel = asCleanLabel(x.`type`) + ":" + asCleanLabel(x.`type`) + ":StixObj"

        def commonPart() = s"CREATE ($nodeAndLabel {id:'${x.id.toString()}'" +
          s",type:'${x.`type`}'" +
          s",created:'${x.created.time}'" +
          s",definition_type:'${clean(x.definition_type)}'" +
          s",definition_id:'$definition_id'" +
          s",external_references:$external_references_ids" +
          s",object_marking_refs:$object_marking_refs_arr" +
          s",granular_markings:$granular_markings_ids" +
          s",created_by_ref:'${x.created_by_ref.getOrElse("").toString}'" + "})"

        session.run(commonPart())
        // create the external_references
        util.createExternRefs(x.id.toString(), x.external_references, external_references_ids)
        // create the granular_markings
        util.createGranulars(x.id.toString(), x.granular_markings, granular_markings_ids)
        // create the marking object definition
        util.createMarkingObjRefs(x.id.toString(), x.definition, definition_id)

      // todo <----- contents: Map[String, Map[String, String]]
      case x: LanguageContent =>
        val labelsString = toStringArray(x.labels)
        val granular_markings_ids = toIdArray(x.granular_markings)
        val external_references_ids = toIdArray(x.external_references)
        val object_marking_refs_arr = toStringIds(x.object_marking_refs)
        val nodeAndLabel = asCleanLabel(x.`type`) + ":" + asCleanLabel(x.`type`) + ":StixObj"

        def commonPart() = s"CREATE ($nodeAndLabel {id:'${x.id.toString()}'" +
          s",type:'${x.`type`}'" +
          s",created:'${x.created.time}'" +
          s",modified:'${x.modified.time}'" +
          s",object_modified:'${x.object_modified}'" +
          s",object_ref:'${x.object_ref.toString()}'" +
          s",labels:'$labelsString'" +
          s",revoked:${x.revoked.getOrElse("false")}" +
          s",external_references:$external_references_ids" +
          s",object_marking_refs:$object_marking_refs_arr" +
          s",granular_markings:$granular_markings_ids" +
          s",created_by_ref:'${x.created_by_ref.getOrElse("").toString}'" + "})"

        session.run(commonPart())
        // create the external_references
        util.createExternRefs(x.id.toString(), x.external_references, external_references_ids)
        // create the granular_markings
        util.createGranulars(x.id.toString(), x.granular_markings, granular_markings_ids)

    }
  }

}
