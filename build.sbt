import Dependencies._

name := "akka-util"

version := "0.1"

scalaVersion := "2.13.5"

resolvers ++= Seq(
    "Maven Central" at "https://repo1.maven.org/maven2/",
    "JCenter" at "https://jcenter.bintray.com",
    "Sonatype Staging" at "https://s01.oss.sonatype.org/service/local/repositories/snapshots/content",
)

lazy val root = ( project in file( "." ) )
  .settings( libraryDependencies ++= akka )
