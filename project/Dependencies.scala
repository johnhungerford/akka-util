import sbt._

object Dependencies {

    val akkaVersion = "2.6.9"

    val akka = Seq( "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
                    "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test )

}
