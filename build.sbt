name := "tabula"

version := "1.0.6-SNAPSHOT"

organization := "org.phasanix"

crossScalaVersions := Seq ("2.11.12", "2.12.4")

scalaVersion := "2.12.4"

libraryDependencies ++= Seq (
  "org.scala-lang"     % "scala-reflect" % scalaVersion.value,
  "org.scalatest"      %% "scalatest"    % "3.2.0-SNAP7" % "test",
  "org.apache.commons" %  "commons-csv"  % "1.5",
  "org.apache.poi"     %  "poi"          % "3.17",
  "org.apache.poi"     %  "poi-ooxml"    % "3.17"
)

resolvers ++= Seq (
  Resolver.defaultLocal
)


