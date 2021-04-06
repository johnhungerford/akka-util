package org.hungerford.akka.dininghakkers

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.hungerford.akka.dininghakkers.Chopstick.ChopstickMessage
import org.hungerford.akka.dininghakkers.Hakker.Command

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object ChopstickNaive {
    import Chopstick._

    def apply( ) : Behavior[ ChopstickMessage ] = {
        Behaviors.setup( ctx => new ChopstickNaive( ctx ) )
    }
}

class ChopstickNaive( ctx : ActorContext[ ChopstickMessage ] ) extends AbstractBehavior[ ChopstickMessage ]( ctx ) {

    import Chopstick._

    // MUTABLE STATE
    // Indicates a) whether chopstick has been taken, and b) by whom
    private var takenBy : Option[ ActorRef[ ChopstickAnswer ] ] = None

    override def onMessage( msg : ChopstickMessage ) : Behavior[ ChopstickMessage ] = msg match {
        case Take( hakker ) =>
            if ( takenBy.isEmpty ) {
                takenBy = Some( hakker )
                hakker ! Taken( ctx.self )
            } else hakker ! Busy( ctx.self )
            Behaviors.same

        case Put( hakker ) =>
            if ( takenBy.contains( hakker ) ) {
                takenBy = None
                Behaviors.same
            } else Behaviors.unhandled
    }
}

/*
 * A hakker is an awesome dude or dudette who either thinks about hacking or has to eat ;-)
 */
object HakkerNaive {
    def apply( name : String, left : ActorRef[ ChopstickMessage ], right : ActorRef[ ChopstickMessage ] ) : Behavior[ Command ] =
        Behaviors.setup { ctx =>
            new HakkerNaive( ctx, name, left, right )
        }
}

class HakkerNaive( ctx : ActorContext[ Command ],
                   name : String,
                   left : ActorRef[ ChopstickMessage ],
                   right : ActorRef[ ChopstickMessage ] )
  extends AbstractBehavior[ Command ]( ctx ) {

    import Hakker._
    import Chopstick._

    // MUTABLE STATE
    private var takeLeftChopstickSuccess : Option[ Boolean ] = None
    private var takeRightChopstickSuccess : Option[ Boolean ] = None

    private val adapter = ctx.messageAdapter( HandleChopstickAnswer )

    override def onMessage( msg : Command ) : Behavior[ Command ] = {
        msg match {
            case Eat =>
                left ! Take( adapter )
                right ! Take( adapter )
                ctx.log.info( "{} began trying to eat", name )
                Behaviors.same

            case Think =>
                left ! Put( adapter )
                right ! Put( adapter )
                takeLeftChopstickSuccess = None
                takeRightChopstickSuccess = None
                ctx.scheduleOnce( rndDuration( 5.seconds, 3.seconds ), ctx.self, Eat )
                ctx.log.info( "{} started thinking", name )
                Behaviors.same

            case HandleChopstickAnswer( Busy( `left` ) ) =>
                if ( takeRightChopstickSuccess.nonEmpty ) {
                    if ( takeRightChopstickSuccess.get ) right ! Put( adapter )
                    takeLeftChopstickSuccess = None
                    takeRightChopstickSuccess = None
                    ctx.scheduleOnce( rndDuration( 10.seconds, 5.seconds ), ctx.self, Eat )
                    ctx.log.info( "{} could not pick up {} so he put down {} and started to think", name, left.path.name, right.path.name )
                } else if ( takeRightChopstickSuccess.isEmpty ) {
                    takeLeftChopstickSuccess = Some( false )
                    ctx.log.info( "{} could not pick up {}", name, left.path.name )
                }
                Behaviors.same


            case HandleChopstickAnswer( Busy( `right` ) ) =>
                if ( takeLeftChopstickSuccess.nonEmpty ) {
                    if ( takeLeftChopstickSuccess.get ) left ! Put( adapter )
                    takeLeftChopstickSuccess = None
                    takeRightChopstickSuccess = None
                    ctx.scheduleOnce( rndDuration( 10.seconds, 5.seconds ), ctx.self, Eat )
                    ctx.log.info( "{} could not pick up {} so he put down {} and started to think", name, right.path.name, left.path.name )
                } else if ( takeLeftChopstickSuccess.isEmpty ) {
                    takeRightChopstickSuccess = Some( false )
                    ctx.log.info( "{} could not pick up {}", name, right.path.name )
                }
                Behaviors.same

            // If one of the chopsticks was able to be taken successfully
            case HandleChopstickAnswer( Taken( `left` ) ) =>
                if ( takeRightChopstickSuccess.nonEmpty ) {
                    if ( !takeRightChopstickSuccess.get ) {
                        left ! Put( adapter )
                        takeLeftChopstickSuccess = None
                        takeRightChopstickSuccess = None
                        ctx.scheduleOnce( rndDuration( 10.seconds, 5.seconds ), ctx.self, Eat )
                        ctx.log.info( "{} picked up {} but because {} could not be picked up put it down and started to think", name, left.path.name, right.path.name )
                    } else {
                        ctx.scheduleOnce( rndDuration( 10.seconds, 5.seconds ), ctx.self, Think )
                        ctx.log.info( "{} picked up {} and started to eat", name, left.path.name )
                    }
                } else {
                    takeLeftChopstickSuccess = Some( true )
                    ctx.log.info( "{} picked up {}", name, left.path.name )
                }
                Behaviors.same

            case HandleChopstickAnswer( Taken( `right` ) ) =>
                if ( takeLeftChopstickSuccess.nonEmpty ) {
                    if ( !takeLeftChopstickSuccess.get ) {
                        right ! Put( adapter )
                        takeLeftChopstickSuccess = None
                        takeRightChopstickSuccess = None
                        ctx.scheduleOnce( rndDuration( 10.seconds, 5.seconds ), ctx.self, Eat )
                        ctx.log.info( "{} picked up {} but because {} could not be picked up put it down and started to think", name, right.path.name, left.path.name )
                    } else {
                        ctx.scheduleOnce( rndDuration( 10.seconds, 5.seconds ), ctx.self, Think )
                        ctx.log.info( "{} picked up {} and started to eat", name, right.path.name )
                    }
                } else {
                    takeRightChopstickSuccess = Some( true )
                    ctx.log.info( "{} picked up {}", name, right.path.name )
                }
                Behaviors.same
        }
    }
}

object DiningHakkersNaive extends DiningHakkers {

    override def chopstickBehavior : Behavior[ ChopstickMessage ] = ChopstickNaive.apply()

    override def hakkerBehavior( name : String, left : ActorRef[ ChopstickMessage ],
                                 right : ActorRef[ ChopstickMessage ] ) : Behavior[ Command ] = {
        HakkerNaive( name, left, right )
    }

}
