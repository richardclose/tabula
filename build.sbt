name := "tabula"

version := "1.0.5-SNAPSHOT"

organization := "org.phasanix"

crossScalaVersions := Seq ("2.11.8", "2.12.3")

scalaVersion := "2.12.3"

libraryDependencies ++= Seq (
  "org.scala-lang"     % "scala-reflect" % scalaVersion.value,
  "org.scalatest"      %% "scalatest"    % "3.2.0-SNAP4" % "test",
  "org.apache.commons" %  "commons-csv"  % "1.4",
  "org.apache.poi"     %  "poi"          % "3.16",
  "org.apache.poi"     %  "poi-ooxml"    % "3.16"
)

resolvers ++= Seq (
  Resolver.defaultLocal
)


