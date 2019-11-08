lazy val root = (project in file("."))
  .settings(
    organization := "monetdb",
    name := "monetdb-jdbc",
    version := "2.18-SNAPSHOT",
    publishMavenStyle := true,
    crossPaths := false,
    autoScalaLibrary := false,
    publishArtifact in (Compile, packageDoc) := false, // turn off javadoc,
    credentials += Credentials("Artifactory Realm", "artifactory.cerno.dk", sys.env.get("ARTIFACTORY_USERNAME").mkString, sys.env.get("ARTIFACTORY_PASSWORD").mkString),
    resolvers +=
    "Artifactory" at "https://artifactory.cerno.dk/artifactory/cerno/",
    publishTo := Some("Artifactory Realm" at "https://artifactory.cerno.dk/artifactory/cerno;build.timestamp=" + new java.util.Date().getTime)
  )
