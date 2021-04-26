import sbt._

object Dependencies {

    val slf4jVersion = "1.7.20"
    val logbackVersion = "1.2.3"

    val akkaVersion = "2.6.9"

    val akka = Seq( "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion )


    val logging = Seq( "org.slf4j" % "slf4j-api" % slf4jVersion,
                       "ch.qos.logback" % "logback-classic" % logbackVersion )

}
