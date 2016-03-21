name := "tabula"

version := "1.0.1-SNAPSHOT"

organization := "org.phasanix"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq (
  "org.scala-lang"     % "scala-reflect" % scalaVersion.value,
  "org.scalatest"      %% "scalatest"    % "2.2.4" % "test",
  "org.apache.commons" %  "commons-csv"  % "1.2",
  "org.apache.poi"     %  "poi"          % "3.14",
  "org.apache.poi"     %  "poi-ooxml"    % "3.14"
)

resolvers ++= Seq (
  Resolver.defaultLocal
)


