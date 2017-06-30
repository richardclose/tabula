name := "tabula"

version := "1.0.4-SNAPSHOT"

organization := "org.phasanix"

crossScalaVersions := Seq ("2.11.8", "2.12.1")

scalaVersion := "2.12.1"

libraryDependencies ++= Seq (
  "org.scala-lang"     % "scala-reflect" % scalaVersion.value,
  "org.scalatest"      %% "scalatest"    % "3.2.0-SNAP4" % "test",
  "org.apache.commons" %  "commons-csv"  % "1.4",
  "org.apache.poi"     %  "poi"          % "3.15",
  "org.apache.poi"     %  "poi-ooxml"    % "3.15"
)

resolvers ++= Seq (
  Resolver.defaultLocal
)


