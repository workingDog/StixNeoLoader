package com.kodekutters.neo4j

import java.io.{File, InputStream}
import java.util.UUID

import com.kodekutters.stix._
import com.kodekutters.stix.Bundle
import io.circe.generic.auto._
import io.circe.parser.decode

import scala.io.Source
import scala.language.implicitConversions
import scala.language.postfixOps
import org.neo4j.driver.v1.{AuthTokens, GraphDatabase}

import scala.collection.JavaConverters._
import com.typesafe.config.ConfigFactory


/**
  * loads Stix-2.1 objects and relationships into a Neo4j graph database
  *
  * @author R. Wathelet June 2017
  *
  *         ref: https://github.com/workingDog/scalastix
  */
object Neo4jLoader {
  val objectRefs = "object_refs"
  val observedDataRefs = "observed_data_refs"
  val whereSightedRefs = "where_sighted_refs"
  val markingObjRefs = "marking_object_refs"

  // must use this constructor, class is private
  def apply(inFile: String, conf: String) = new Neo4jLoader(inFile, conf)

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
    decode[Bundle](jsondoc) match {
      case Left(failure) => println("-----> ERROR invalid bundle JSON in zip file: \n"); None
      case Right(bundle) => Option(bundle)
    }
  }

}

/**
  * loads Stix-2.1 objects (nodes) and relationships (edges) into a Neo4j database
  *
  * @param inFile   the input file to process
  * @param confFile the configuration file to use
  */
class Neo4jLoader private(inFile: String, confFile: String) {

  import Neo4jLoader._

  private val config = ConfigFactory.parseFile(new File(confFile))

  private val driver = GraphDatabase.driver(
    "bolt://" + config.getString("host") + "/" + config.getString("port"),
    AuthTokens.basic(config.getString("name"), config.getString("psw")))

  private val session = driver.session

  // generate a unique random id
  private def newId = UUID.randomUUID().toString

  // process a Stix object according to its type
  private def convertObj(obj: StixObj) = {
    obj match {
      case stix if stix.isInstanceOf[SDO] => convertSDO(stix.asInstanceOf[SDO])
      case stix if stix.isInstanceOf[SRO] => convertSRO(stix.asInstanceOf[SRO])
      case stix if stix.isInstanceOf[StixObj] => convertStixObj(stix.asInstanceOf[StixObj])
      case stix => // do nothing for now
    }
  }

  /**
    * read a bundle of Stix objects from the input file,
    * convert it to neo4j node and relations and load it into the db
    */
  def convertBundleFile(): Unit = {
    // read a STIX bundle from the inFile
    val jsondoc = Source.fromFile(inFile).mkString
    // create a bundle object from it, convert its objects to nodes and relations
    decode[Bundle](jsondoc) match {
      case Left(failure) => println("\n-----> ERROR reading bundle in file: " + inFile)
      case Right(bundle) => bundle.objects.foreach(convertObj(_))
    }
    // all done   
    session.close()
    driver.close()
  }

  /**
    * read Stix bundles from the input zip file and
    * convert them to neo4j nodes and relations and load them into the db
    */
  def convertBundleZipFile(): Unit = {
    // get the zip file
    import scala.collection.JavaConverters._
    val rootZip = new java.util.zip.ZipFile(new File(inFile))
    // for each entry file
    rootZip.entries.asScala.filter(_.getName.toLowerCase.endsWith(".json")).foreach(f => {
      loadBundle(rootZip.getInputStream(f)) match {
        case Some(bundle) => bundle.objects.foreach(convertObj(_))
        case None => println("-----> ERROR invalid bundle JSON in zip file: \n")
      }
    })
    // all done   
    session.close()
    driver.close()
  }

  /**
    * For processing very large text files.
    *
    * read Stix objects one by one from the input file,
    * convert them to neo4j nodes and relations and load them into the db
    *
    * The input file must contain a Stix object on one line ending with a new line.
    *
    */
  def convertStixFile(): Unit = {
    // read a STIX object from the inFile, one line at a time
    for (line <- Source.fromFile(inFile).getLines) {
      // create a Stix object from it and convert it to node or relation
      decode[StixObj](line) match {
        case Left(failure) => println("\n-----> ERROR reading StixObj in file: " + inFile + " line: " + line)
        case Right(stixObj) => convertObj(stixObj)
      }
    }
    // all done   
    session.close()
    driver.close()
  }

  /**
    * For processing very large zip files.
    *
    * read Stix objects one by one from the input zip file,
    * convert them to neo4j nodes and relations and load them into the db
    *
    * There can be one or more file entries in the zip file,
    * each file must have the extension .json.
    *
    * Each entry file must have a Stix object on one line ending with a new line.
    *
    */
  def convertStixZipFile(): Unit = {
    // get the input zip file
    val rootZip = new java.util.zip.ZipFile(new File(inFile))
    // for each entry file
    rootZip.entries.asScala.filter(_.getName.toLowerCase.endsWith(".json")).foreach(f => {
      // get the lines from the entry file
      val inputLines = Source.fromInputStream(rootZip.getInputStream(f)).getLines
      // read a Stix object from the inputLines, one line at a time
      for (line <- inputLines) {
        // create a Stix object from it, convert and write it out
        decode[StixObj](line) match {
          case Left(failure) => println("\n-----> ERROR reading StixObj in file: " + f.getName + " line: " + line)
          case Right(stixObj) => convertObj(stixObj)
        }
      }
    })
    // all done   
    session.close()
    driver.close()
  }

  // process the SDO
  def convertSDO(x: SDO) = {
    // common elements
    val labelsString = toStringArray(x.labels)
    val granular_markings_ids = toIdArray(x.granular_markings)
    val external_references_ids = toIdArray(x.external_references)
    val object_marking_refs_arr = toStringIds(x.object_marking_refs)
    val nodeLabel = asCleanLabel(x.`type`)

    def commonPart(node: String) = s"CREATE (${asCleanLabel(node)}:SDO {id:'${x.id.toString()}'" +
      s",type:'${x.`type`}'" +
      s",created:'${x.created.time}',modified:'${x.modified.time}'" +
      s",revoked:${x.revoked.getOrElse("false")},labels:$labelsString" +
      s",confidence:${x.confidence.getOrElse(0)}" +
      s",external_references:$external_references_ids" +
      s",lang:'${clean(x.lang.getOrElse(""))}'" +
      s",object_marking_refs:$object_marking_refs_arr" +
      s",granular_markings:$granular_markings_ids" +
      s",created_by_ref:'${x.created_by_ref.getOrElse("")}'"

    // write the external_references
    writeExternRefs(x.id.toString(), x.external_references, external_references_ids)
    // write the granular_markings
    writeGranulars(x.id.toString(), x.granular_markings, granular_markings_ids)

    x.`type` match {

      case AttackPattern.`type` =>
        val y = x.asInstanceOf[AttackPattern]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        val script = commonPart(AttackPattern.`type`) +
          s",name:'${clean(y.name)}',description:'${clean(y.description.getOrElse(""))}'" +
          s",kill_chain_phases:$kill_chain_phases_ids" + "})"
        session.run(script)
        writeKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      case Identity.`type` =>
        val y = x.asInstanceOf[Identity]
        val script = commonPart(Identity.`type`) +
          s",name:'${clean(y.name)}',identity_class:'${clean(y.identity_class)}'" +
          s",sectors:${toStringArray(y.sectors)}" +
          s",contact_information:'${clean(y.contact_information.getOrElse(""))}'" +
          s",description:'${clean(y.description.getOrElse(""))}'" + "})"
        session.run(script)

      case Campaign.`type` =>
        val y = x.asInstanceOf[Campaign]
        val script = commonPart(Campaign.`type`) +
          s",name:'${clean(y.name)}',objective:'${clean(y.objective.getOrElse(""))}'" +
          s",aliases:${toStringArray(y.aliases)}" +
          s",first_seen:'${clean(y.first_seen.getOrElse("").toString)}" +
          s",last_seen:'${clean(y.last_seen.getOrElse("").toString)}" +
          s",description:'${clean(y.description.getOrElse(""))}'" + "})"
        session.run(script)

      case CourseOfAction.`type` =>
        val y = x.asInstanceOf[CourseOfAction]
        val script = commonPart(CourseOfAction.`type`) +
          s",name:'${clean(y.name)}',description:'${clean(y.description.getOrElse(""))}'" + "})"
        session.run(script)

      case IntrusionSet.`type` =>
        val y = x.asInstanceOf[IntrusionSet]
        val script = commonPart(IntrusionSet.`type`) +
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
        val script = commonPart(Malware.`type`) +
          s",name:'${clean(y.name)}',description:'${clean(y.description.getOrElse(""))}'" +
          s",kill_chain_phases:$kill_chain_phases_ids" + "})"
        session.run(script)
        writeKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      case Report.`type` =>
        val y = x.asInstanceOf[Report]
        val object_refs_ids = toIdArray(y.object_refs)
        val script = commonPart(Report.`type`) +
          s",name:'${clean(y.name)}',published:'${y.published}'" +
          s",object_refs_ids:$object_refs_ids" +
          s",description:'${clean(y.description.getOrElse(""))}'" + "})"
        session.run(script)
        writeObjRefs(y.id.toString(), y.object_refs, object_refs_ids, Neo4jLoader.objectRefs)

      case ThreatActor.`type` =>
        val y = x.asInstanceOf[ThreatActor]
        val script = commonPart(ThreatActor.`type`) +
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
        val script = commonPart(Tool.`type`) +
          s",name:'${clean(y.name)}',description:'${clean(y.description.getOrElse(""))}'" +
          s",kill_chain_phases:$kill_chain_phases_ids" +
          s",tool_version:'${clean(y.tool_version.getOrElse(""))}'" + "})"
        session.run(script)
        writeKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      case Vulnerability.`type` =>
        val y = x.asInstanceOf[Vulnerability]
        val script = commonPart(Vulnerability.`type`) +
          s",name:'${clean(y.name)}',description:'${clean(y.description.getOrElse(""))}'" + "})"
        session.run(script)

      case Indicator.`type` =>
        val y = x.asInstanceOf[Indicator]
        val kill_chain_phases_ids = toIdArray(y.kill_chain_phases)
        val script = commonPart(Indicator.`type`) +
          s",name:'${clean(y.name.getOrElse(""))}',description:'${clean(y.description.getOrElse(""))}'" +
          s",pattern:'${clean(y.pattern)}'" +
          s",valid_from:'${y.valid_from.toString()}'" +
          s",valid_until:'${clean(y.valid_until.getOrElse("").toString)}'" +
          s",kill_chain_phases:$kill_chain_phases_ids" + "})"
        session.run(script)
        writeKillPhases(y.id.toString(), y.kill_chain_phases, kill_chain_phases_ids)

      // todo  objects: Map[String, Observable],
      case ObservedData.`type` =>
        val y = x.asInstanceOf[ObservedData]
        val script = commonPart(ObservedData.`type`) +
          s",first_observed:'${y.first_observed.toString()}" +
          s",last_observed:'${y.last_observed.toString()}" +
          s",number_observed:'${y.number_observed}" +
          s",description:'${clean(y.description.getOrElse(""))}'" + "})"
        session.run(script)

      case _ => // do nothing for now
    }
  }

  // the Relationship and Sighting
  def convertSRO(x: SRO) = {
    // common elements
    val labelsString = toStringArray(x.labels)
    val granular_markings_ids = toIdArray(x.granular_markings)
    val external_references_ids = toIdArray(x.external_references)
    val object_marking_refs_arr = toStringIds(x.object_marking_refs)

    def commonPart() = s"id:'${x.id.toString()}'" +
      s",type:'${x.`type`}'" +
      s",created:'${x.created.time}',modified:'${x.modified.time}'" +
      s",revoked:${x.revoked.getOrElse("false")},labels:$labelsString" +
      s",confidence:${x.confidence.getOrElse(0)}" +
      s",external_references:$external_references_ids" +
      s",lang:'${clean(x.lang.getOrElse(""))}'" +
      s",object_marking_refs:$object_marking_refs_arr" +
      s",granular_markings:$granular_markings_ids" +
      s",created_by_ref:'${x.created_by_ref.getOrElse("")}'"

    // write the external_references
    writeExternRefs(x.id.toString(), x.external_references, external_references_ids)
    // write the granular_markings
    writeGranulars(x.id.toString(), x.granular_markings, granular_markings_ids)

    if (x.isInstanceOf[Relationship]) {
      val y = x.asInstanceOf[Relationship]
      val props = commonPart() +
        s",source_ref:'${y.source_ref.toString()}'" +
        s",target_ref:'${y.target_ref.toString()}'" +
        s",relationship_type:'${asCleanLabel(y.relationship_type)}'" +
        s",description:'${clean(y.description.getOrElse(""))}'"

      val script = s"MATCH (source {id:'${y.source_ref.toString()}'}), (target {id:'${y.target_ref.toString()}'}) " +
        s"CREATE (source)-[${asCleanLabel(y.relationship_type)}:SRO {$props}]->(target)"
      session.run(script)
    }
    else { // must be a Sighting todo ----> target_ref  observed_data_refs heading
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

      val script = s"MATCH (source {id:'${y.sighting_of_ref.toString}'}), (target {id:'${y.sighting_of_ref.toString}'}) " +
        s"CREATE (source)-[${Sighting.`type`}:SRO {$props}]->(target)"
      session.run(script)

      writeObjRefs(y.id.toString(), y.observed_data_refs, observed_data_ids, Neo4jLoader.observedDataRefs)
      writeObjRefs(y.id.toString(), y.where_sighted_refs, where_sighted_refs_ids, Neo4jLoader.whereSightedRefs)
    }
  }

  // convert MarkingDefinition and LanguageContent
  def convertStixObj(stixObj: StixObj) = {

    stixObj match {

      case x: MarkingDefinition =>
        val definition_id = newId
        val granular_markings_ids = toIdArray(x.granular_markings)
        val external_references_ids = toIdArray(x.external_references)
        val object_marking_refs_arr = toStringIds(x.object_marking_refs)

        def commonPart(node: String) = s"CREATE (${asCleanLabel(node)}:StixObj {id:'${x.id.toString()}'" +
          s",type:'${x.`type`}'" +
          s",created:'${x.created.time}'" +
          s",definition_type:'${clean(x.definition_type)}'" +
          s",definition_id:'$definition_id'" +
          s",external_references:$external_references_ids" +
          s",object_marking_refs:$object_marking_refs_arr" +
          s",granular_markings:$granular_markings_ids" +
          s",created_by_ref:'${x.created_by_ref.getOrElse("")}'" + "})"

        // write the external_references
        writeExternRefs(x.id.toString(), x.external_references, external_references_ids)
        // write the granular_markings
        writeGranulars(x.id.toString(), x.granular_markings, granular_markings_ids)
        // write the marking object definition
        writeMarkingObjRefs(x.id.toString(), x.definition, definition_id)
        session.run(commonPart(MarkingDefinition.`type`))

      // todo <----- contents: Map[String, Map[String, String]]
      case x: LanguageContent =>
        val labelsString = toStringArray(x.labels)
        val granular_markings_ids = toIdArray(x.granular_markings)
        val external_references_ids = toIdArray(x.external_references)
        val object_marking_refs_arr = toStringIds(x.object_marking_refs)

        def commonPart(node: String) = s"CREATE (${asCleanLabel(node)}:StixObj {id:'${x.id.toString()}'" +
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
          s",created_by_ref:'${x.created_by_ref.getOrElse("")}'" + "})"

        // write the external_references
        writeExternRefs(x.id.toString(), x.external_references, external_references_ids)
        // write the granular_markings
        writeGranulars(x.id.toString(), x.granular_markings, granular_markings_ids)
        session.run(commonPart(LanguageContent.`type`))

    }
  }

  //--------------------------------------------------------------------------------------------

  // write the marking object
  def writeMarkingObjRefs(idString: String, definition: MarkingObject, definition_id: String) = {
    val mark: String = definition match {
      case s: StatementMarking => clean(s.statement) + ",statement"
      case s: TPLMarking => clean(s.tlp.value) + ",tlp"
      case _ => ""
    }
    val nodeScript = s"CREATE (${Neo4jLoader.markingObjRefs} {marking_id:'$definition_id',marking:'$mark'})"
    session.run(nodeScript)
    // write the markingObj relationships with the given id
    val relScript = s"MATCH (source {id:'$idString'}), (target {marking_id:'$definition_id'}) " +
      s"CREATE (source)-[:HAS_MARKING_OBJECT]->(target)"
    session.run(relScript)
  }

  // write the kill_chain_phases
  def writeKillPhases(idString: String, kill_chain_phases: Option[List[KillChainPhase]], kill_chain_phases_ids: String) = {
    val killphases = for (s <- kill_chain_phases.getOrElse(List.empty))
      yield (clean(s.kill_chain_name), clean(s.phase_name), asCleanLabel(s.`type`))
    if (killphases.nonEmpty) {
      val temp = kill_chain_phases_ids.replace("[", "").replace("]", "")
      val kp = (temp.split(",") zip killphases).foreach({ case (a, (b, c, d)) =>
        val script = s"CREATE ($d {kill_chain_phase_id:$a,kill_chain_name:'$b',phase_name:'$c'})"
        session.run(script)
      })
      for (k <- temp.split(",")) {
        val relScript = s"MATCH (source {id:'$idString'}), (target {kill_chain_phase_id:$k}) " +
          s"CREATE (source)-[:HAS_KILL_CHAIN_PHASE]->(target)"
        session.run(relScript)
      }
    }
  }

  // write the external_references
  def writeExternRefs(idString: String, external_references: Option[List[ExternalReference]], external_references_ids: String) = {
    val externRefs = for (s <- external_references.getOrElse(List.empty))
      yield (clean(s.source_name), clean(s.description.getOrElse("")),
        clean(s.url.getOrElse("")), clean(s.external_id.getOrElse("")), asCleanLabel(s.`type`))
    if (externRefs.nonEmpty) {
      val temp = external_references_ids.replace("[", "").replace("]", "")
      val kp = (temp.split(",") zip externRefs).foreach(
        { case (a, (b, c, d, e, f)) =>
          val script = s"CREATE (${asCleanLabel(e)} {external_reference_id:$a" +
            s",source_name:'$b',description:'$c',url:'$d',external_id:'$f'})"
          session.run(script)
        }
      )
      // write the external_reference relationships with the given ids
      for (k <- temp.split(",")) {
        val relScript = s"MATCH (source {id:'$idString'}), (target {external_reference_id:$k}) " +
          s"CREATE (source)-[:HAS_EXTERNAL_REF]->(target)"
        session.run(relScript)
      }
    }
  }

  // write the granular_markings
  def writeGranulars(idString: String, granular_markings: Option[List[GranularMarking]], granular_markings_ids: String) = {
    val granulars = for (s <- granular_markings.getOrElse(List.empty))
      yield (toStringArray(Option(s.selectors)), clean(s.marking_ref.getOrElse("")),
        clean(s.lang.getOrElse("")), asCleanLabel(s.`type`))
    if (granulars.nonEmpty) {
      val temp = granular_markings_ids.replace("[", "").replace("]", "")
      val kp = (temp.split(",") zip granulars).foreach(
        { case (a, (b, c, d, e)) =>
          val script = s"CREATE ($d {granular_marking_id:$a" +
            s",selectors:$b,marking_ref:'$c',lang:'$d'})"
          session.run(script)
        }
      )
      // write the granular_markings relationships with the given ids
      for (k <- temp.split(",")) {
        val relScript = s"MATCH (source {id:'$idString'}), (target {granular_marking_id:$k}) " +
          s"CREATE (source)-[:HAS_GRANULAR_MARKING]->(target)"
        session.run(relScript)
      }
    }
  }

  // write the object_refs
  def writeObjRefs(idString: String, object_refs: Option[List[Identifier]], object_refs_ids: String, typeName: String) = {
    val objRefs = for (s <- object_refs.getOrElse(List.empty)) yield clean(s.toString())
    if (objRefs.nonEmpty) {
      val temp = object_refs_ids.replace("[", "").replace("]", "")
      val kp = (temp.split(",") zip objRefs).foreach({ case (a, b) =>
        val script = s"CREATE (${asCleanLabel(typeName)} {object_ref_id:$a,identifier:'$b'})"
        session.run(script)
      })
      // write the object_refs relationships with the given ids
      for (k <- temp.split(",")) {
        val rtype = "HAS_" + asCleanLabel(typeName.toUpperCase)
        val relScript = s"MATCH (source {id:'$idString'}), (target {object_ref_id:$k}) " +
          s"CREATE (source)-[:$rtype]->(target)"
        session.run(relScript)
      }
    }
  }

  // clean the string, i.e. replace unwanted char
  private def clean(s: String) = s.replace(",", " ").replace(":", " ").replace("\'", " ").replace(";", " ").replace("\"", "").replace("\\", "").replace("\n", "").replace("\r", "")

  // make an array of id values from the input list
  private def toIdArray(dataList: Option[List[Any]]) = {
    val t = for (s <- dataList.getOrElse(List.empty)) yield s"'$newId'" + ","
    if (t.nonEmpty) "[" + t.mkString.reverse.substring(1).reverse + "]" else "[]"
  }

  // make an array of cleaned string values from the input list
  private def toStringArray(dataList: Option[List[String]]) = {
    val t = for (s <- dataList.getOrElse(List.empty)) yield s"'${clean(s)}'" + ","
    if (t.nonEmpty) "[" + t.mkString.reverse.substring(1).reverse + "]" else "[]"
  }

  // make an array of id strings --> no cleaning done here
  private def toStringIds(dataList: Option[List[Identifier]]) = {
    val t = for (s <- dataList.getOrElse(List.empty)) yield s"'${s.toString()}'" + ","
    if (t.nonEmpty) "[" + t.mkString.reverse.substring(1).reverse + "]" else "[]"
  }

  // the Neo4j :LABEL and :TYPE cannot deal with "-", so clean and replace with "_"
  private def asCleanLabel(s: String) = clean(s).replace("-", "_")

}
