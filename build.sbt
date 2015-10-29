lazy val root = (project in file("."))
  .settings(
    organization := "monetdb",
    name := "monetdb-jdbc",
    version := "2.18-SNAPSHOT",
    publishMavenStyle := true,
    crossPaths := false,
    autoScalaLibrary := false,
    publishArtifact in (Compile, packageDoc) := false // turn off javadoc    
  )
