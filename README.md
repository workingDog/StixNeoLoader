## Loads STIX-2.1 to a Neo4j graph database

### No longer maintained, see [StixToNeoDB](https://github.com/workingDog/StixToNeoDB) or [StixLoader](https://github.com/workingDog/stixloader) instead.

This application **StixNeoLoader**, loads [STIX-2.1](https://docs.google.com/document/d/1yvqWaPPnPW-2NiVCLqzRszcx91ffMowfT5MmE9Nsy_w/edit#) 
objects and relations from json and zip files into a [Neo4j](https://neo4j.com/) graph database. 

The [OASIS](https://www.oasis-open.org/) open standard Structured Threat Information Expression [STIX-2.1](https://docs.google.com/document/d/1yvqWaPPnPW-2NiVCLqzRszcx91ffMowfT5MmE9Nsy_w/edit#) 
is a language for expressing cyber threat and observable information.

[Neo4j](https://neo4j.com/) "is a highly scalable native graph database that leverages data 
relationships as first-class entities, helping enterprises build intelligent applications 
to meet today’s evolving data challenges."
In essence, a graph database and processing engine that is used here for storing Stix objects 
and their relationships.
 
**StixNeoLoader** converts [STIX-2.1](https://docs.google.com/document/d/1yvqWaPPnPW-2NiVCLqzRszcx91ffMowfT5MmE9Nsy_w/edit#) 
domain objects (SDO) and relationships (SRO) to [Neo4j Cypher](https://neo4j.com/developer/cypher-query-language/) 
nodes and relations statements. The statements are then executed to load the data into a Neo4j graph database. 
This allows adding new nodes and relations to an existing Neo4j graph database.
        
### References
 
1) [Neo4j](https://neo4j.com/)

2) [Cypher](https://neo4j.com/developer/cypher-query-language/) 

3) [ScalaStix](https://github.com/workingDog/scalastix)

4) [STIX-2.1](https://docs.google.com/document/d/1yvqWaPPnPW-2NiVCLqzRszcx91ffMowfT5MmE9Nsy_w/edit)

### Dependencies and requirements

Depends on the scala [ScalaStix](https://github.com/workingDog/scalastix) library.

Java 8 is required and Neo4j should be installed.

### Installation and packaging

The easiest way to compile and package the application from source is to use [SBT](http://www.scala-sbt.org/).
To assemble the application and all its dependencies into a single jar file type:

    sbt assembly

This will produce "stixneoloader-1.0.jar" in the "./target/scala-2.12" directory.

For convenience a **"stixneoloader-1.0.jar"** file is in the "distrib" directory ready for use.

### Usage

**StixNeoLoader** must have a configuration file containing the values for the **host** and **port** of 
the Neo4j server. In addition the user **name** and **password** is required. 
To also output all cypher statements to a text file, add a **cypherFile** field in the configuration file.
See **application.conf** for an example setup.

For example, to create a new test database, first create a directory, say "neodb". Launch the "Neo4j-3.2.1" app and 
select "neodb" as the database location and click start. Once the status is "started", open a browser 
on "http://localhost:7474". Change the password, say "xxxx" and put this in the "application.conf" file. 
**StixNeoLoader** will connect to this database (while status is started) using the "application.conf" file 
name and password.

To load the Stix objects into a Neo4j graph database, simply type at the prompt:
 
    java -jar stixneoloader-1.0.jar --json stix_file.json config_file
    or
    java -jar stixneoloader-1.0.jar --zip stix_file.zip config_file
 
With the option **--json** the input file "stix_file.json" is the file containing a 
bundle of Stix objects you want to convert, and "config_file" is the optional configuration file to use. 
If the configuration file argument is absent, the default "application.conf" in the current directory is used.
In this case ensure that the **application.conf** file is in the same directory as the jar file.
 
With the option **--zip** the input file must be a zip file with one or more entry files containing a single bundle of Stix objects 
in each.
 
 #### For very large files
 
 To process very large files use the following options:
 
     java -jar stixtoneo4j-1.0.jar --jsonx stix_file.json config_file
     or
     java -jar stixtoneo4j-1.0.jar --zipx stix_file.zip config_file
 
 With the **--jsonx** option the input file must contain a Stix object on one line 
 ending with a new line. Similarly when using the **--zipx** option, each input zip file entries must 
 contain a Stix object on one line ending with a new line. When using these options 
 the processing is done one line at a time.
 
### Status

never finished, use [StixToNeoDB](https://github.com/workingDog/StixToNeoDB) instead.

Using Scala 2.12, Java 8 and SBT-1.0.3


