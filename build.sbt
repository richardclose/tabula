name := "tabula"

version := "1.0.6-SNAPSHOT"

organization := "org.phasanix"

scalaVersion := "2.13.5"

libraryDependencies ++= Seq (
  "org.scala-lang"     % "scala-reflect"   % scalaVersion.value,
  "org.scalatest"      %% "scalatest"      % "3.2.5" % "test",
  "org.apache.commons" %  "commons-csv"    % "1.8",
  "org.apache.poi"     %  "poi"            % "5.0.0",
  "org.apache.poi"     %  "poi-ooxml"      % "5.0.0"
)

resolvers ++= Seq (
  Resolver.defaultLocal
)


