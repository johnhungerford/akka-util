package org.hungerford.akka.dininghakkers

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.hungerford.akka.dininghakkers.Chopstick.ChopstickAnswer

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

/**
 * Adapted from the Akka example:
 * https://developer.lightbend.com/start/?group=akka&project=akka-samples-fsm-scala
 */


object Chopstick {
    sealed trait ChopstickMessage
    final case class Take(ref: ActorRef[ChopstickAnswer]) extends ChopstickMessage
    final case class Put(ref: ActorRef[ChopstickAnswer]) extends ChopstickMessage

    sealed trait ChopstickAnswer
    final case class Taken(chopstick: ActorRef[ChopstickMessage])
      extends ChopstickAnswer
    final case class Busy(chopstick: ActorRef[ChopstickMessage])
      extends ChopstickAnswer
}

object Hakker {
    trait Command
    case object Think extends Command
    case object Eat extends Command
    final case class HandleChopstickAnswer( msg: ChopstickAnswer )
      extends Command

    private val r : Random = new scala.util.Random

    def rndBool() : Boolean = r.nextBoolean()

    def rndDuration( dur : FiniteDuration, plusOrMinus : FiniteDuration ) : FiniteDuration = {
        val pmVal = r.nextLong( plusOrMinus._1 + 1 )
        val pOrM = FiniteDuration( pmVal, plusOrMinus._2 )
        val plus = r.nextBoolean()
        if ( plus ) dur + pOrM
        else dur - pOrM
    }
}

object DiningHakkers {
    sealed trait DiningHakkersMessage
    case class NewHakkers( names : Seq[ String ] ) extends DiningHakkersMessage
    case object Start extends DiningHakkersMessage
    case object Stop extends DiningHakkersMessage
    case object Exit extends DiningHakkersMessage
}

trait DiningHakkers {

    import Chopstick._
    import Hakker._
    import DiningHakkers._

    def chopstickBehavior : Behavior[ ChopstickMessage ]
    def hakkerBehavior( name : String, left : ActorRef[ ChopstickMessage ], right : ActorRef[ ChopstickMessage ] ) : Behavior[ Command ]

    def apply(): Behavior[ DiningHakkersMessage ] = Behaviors.setup { context =>
        var hakkerNames : Seq[ String ] = Nil
        var chopsticks : Seq[ ActorRef[ ChopstickMessage ] ] = Nil
        var hakkers : Seq[ ActorRef[ Command ] ] = Nil

        def newNames( names : Seq[ String ] ) : Unit = hakkerNames = names.distinct

        def initDiners() : Unit = {
            chopsticks = hakkerNames.zipWithIndex.map {
                case (_, i) => context.spawn( chopstickBehavior, "Chopstick" + i )
            }
            hakkers = hakkerNames.zipWithIndex.map {
                case (name, i) => context.spawn( hakkerBehavior( name, chopsticks( i ), chopsticks( ( i + 1 ) % chopsticks.length ) ), name )
            }
        }

        def stopDining() : Unit = {
            hakkers.foreach( h => context.stop( h ) )
            chopsticks.foreach( c => context.stop( c ) )
            hakkers = Nil
            chopsticks = Nil
        }

        //Signal all hakkers that they should start thinking, and watch the show
        Behaviors.receiveMessagePartial {
            case NewHakkers( names ) =>
                context.log.info( "UPDATING HAKKERS" )
                newNames( names )
                Behaviors.same

            case Start =>
                if ( hakkerNames != Nil && hakkers == Nil ) {
                    initDiners()
                    hakkers.foreach( _ ! Eat )
                } else if ( hakkers != Nil ) context.log.info( "Already started..." )
                else context.log.info( "No hakkers to dine" )
                Behaviors.same

            case Stop =>
                stopDining()
                Behaviors.same

            case Exit =>
                stopDining()
                Behaviors.stopped
        }
    }

}

object SingleDiningHakker {
    import Chopstick._
    import Hakker._

    def apply( name : String ) : Behavior[ Command ] = Behaviors.setup( ctx => {
        val chopstick1 = ctx.spawn( ChopstickNaive(), "chopstick-1" )
        val chopstick2 = ctx.spawn( ChopstickNaive(), "chopstick-2" )
        HakkerNaive( name, chopstick1, chopstick2 )
    } )
}
