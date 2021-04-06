import sbt._

object Dependencies {

    val slf4jVersion = "1.7.20"
    val logbackVersion = "1.2.3"

    val betterFilesVersion = "3.8.0"

    val akkaVersion = "2.6.9"
    val akkaHttpVersion = "10.2.1"

    val scalaTestVersion = "3.1.4"
    val mockitoVersion = "1.16.0"

    val okhttpVersion = "4.1.0"

    val scalaFpVersion = "1.0-SNAPSHOT"


    val akka = Seq( "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
                    "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test )

    val akkaHttp = Seq( "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion )

    val logging = Seq( "org.slf4j" % "slf4j-api" % slf4jVersion,
                       "ch.qos.logback" % "logback-classic" % logbackVersion )

    val betterFiles = Seq( "com.github.pathikrit" %% "better-files" % betterFilesVersion )

    val okhttp = Seq( "com.squareup.okhttp3" % "okhttp" % okhttpVersion,
                      "com.squareup.okhttp3" % "mockwebserver" % okhttpVersion )

    val testing = Seq( "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
                       "org.mockito" %% "mockito-scala-scalatest" % mockitoVersion % Test )

    val scalaFp = Seq( "io.github.johnhungerford.fp" %% "scala-fp" % scalaFpVersion )
}
