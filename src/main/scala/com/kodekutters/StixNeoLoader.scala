package com.kodekutters

import com.kodekutters.neo4j.Neo4jLoader
import scala.language.implicitConversions
import scala.language.postfixOps

/**
  * loads a Stix json file containing STIX objects, or
  * a Stix zip file containing one or more of entry files,
  * into a neo4j graph database
  *
  * @author R. Wathelet June 2017
  *
  */
object StixNeoLoader {

  val usage =
    """Usage:
       java -jar stixneoloader-1.0.jar --json stix_file.json config_file
        or
       java -jar stixneoloader-1.0.jar --zip stix_file.zip config_file

       config_file is optional, the default is application.conf
       the options --jsonx and --zipx can also be used for large files""".stripMargin

  /**
    * loads a Stix json file containing STIX objects, or
    * a Stix zip file containing one or more of entry files,
    * into a neo4j graph database
    */
  def main(args: Array[String]) {
    if (args.isEmpty)
      println(usage)
    else {
      val conf = if (args.length == 3) args(2).trim else ""
      val confFile = if (conf.isEmpty) new java.io.File(".").getCanonicalPath + "/application.conf" else conf
      val neoLoader = new Neo4jLoader(args(1), confFile)
      args(0) match {
        case "--json" => neoLoader.processBundleFile()
        case "--zip" => neoLoader.processBundleZipFile()
        case "--jsonx" => neoLoader.processStixFile()
        case "--zipx" => neoLoader.processStixZipFile()
        case x => println("unknown option: " + x + "\n"); println(usage)
      }
    }
  }

}


