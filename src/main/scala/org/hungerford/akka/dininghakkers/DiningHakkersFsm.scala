package org.hungerford.akka.dininghakkers

import akka.NotUsed
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import org.hungerford.akka.dininghakkers.Chopstick._
import org.hungerford.akka.dininghakkers.Hakker._

import scala.concurrent.duration._

// Akka adaptation of
// http://www.dalnefre.com/wp/2010/08/dining-philosophers-in-humus/

/*
 * A Chopstick is an actor, it can be taken, and put back
 */
object ChopstickFsm {

    def apply( ) : Behavior[ ChopstickMessage ] = available()

    //When a Chopstick is taken by a hakker
    //It will refuse to be taken by other hakkers
    //But the owning hakker can put it back
    private def takenBy(
                         hakker : ActorRef[ ChopstickAnswer ]
                       ) : Behavior[ ChopstickMessage ] = {
        Behaviors.receive {
            case (ctx, Take( otherHakker )) =>
                otherHakker ! Busy( ctx.self )
                Behaviors.same
            case (_, Put( `hakker` )) =>
                available()
            case _ =>
                // here and below it's left to be explicit about partial definition,
                // but can be omitted when Behaviors.receiveMessagePartial is in use
                Behaviors.unhandled
        }
    }

    //When a Chopstick is available, it can be taken by a hakker
    private def available( ) : Behavior[ ChopstickMessage ] = {
        Behaviors.receivePartial {
            case (ctx, Take( hakker )) =>
                hakker ! Taken( ctx.self )
                takenBy( hakker )
        }
    }
}

/*
 * A hakker is an awesome dude or dudette who either thinks about hacking or has to eat ;-)
 */
object HakkerFsm {
    def apply( name : String, left : ActorRef[ ChopstickMessage ], right : ActorRef[ ChopstickMessage ] ) : Behavior[ Command ] =
        Behaviors.setup { ctx =>
            new HakkerFsm( ctx, name, left, right ).waiting
        }
}

class HakkerFsm( ctx : ActorContext[ Command ],
                 name : String,
                 left : ActorRef[ ChopstickMessage ],
                 right : ActorRef[ ChopstickMessage ] ) {

    import Hakker._

    private val adapter = ctx.messageAdapter( HandleChopstickAnswer )

    val waiting : Behavior[ Command ] =
        Behaviors.receiveMessagePartial {
            case Eat =>
                ctx.log.info( "{} starts to think", name )
                startThinking( ctx, 5.seconds, 3.seconds )
        }

    //When a hakker is thinking it can become hungry
    //and try to pick up its chopsticks and eat
    private val thinking : Behavior[ Command ] = {
        Behaviors.receiveMessagePartial {
            case Eat =>
                left ! Take( adapter )
                right ! Take( adapter )
                waitForFirstChopstick
        }
    }

    //When a hakker is hungry it tries to pick up its chopsticks and eat
    //When it picks one up, it goes into wait for the other
    //If the hakkers first attempt at grabbing a chopstick fails,
    //it starts to wait for the response of the other grab
    private lazy val waitForFirstChopstick : Behavior[ Command ] =
    Behaviors.receiveMessagePartial {
        case HandleChopstickAnswer( Taken( `left` ) ) =>
            waitForOtherChopstick( chopstickToWaitFor = right, takenChopstick = left )

        case HandleChopstickAnswer( Taken( `right` ) ) =>
            waitForOtherChopstick( chopstickToWaitFor = left, takenChopstick = right )

        case HandleChopstickAnswer( Busy( _ ) ) =>
            firstChopstickDenied
    }

    //When a hakker is waiting for the last chopstick it can either obtain it
    //and start eating, or the other chopstick was busy, and the hakker goes
    //back to think about how he should obtain his chopsticks :-)
    private def waitForOtherChopstick(
                                       chopstickToWaitFor : ActorRef[ ChopstickMessage ],
                                       takenChopstick : ActorRef[ ChopstickMessage ]
                                     ) : Behavior[ Command ] = {
        Behaviors.receiveMessagePartial {
            case HandleChopstickAnswer( Taken( `chopstickToWaitFor` ) ) =>
                ctx.log.info(
                    "{} has picked up {} and {} and starts to eat",
                    name,
                    left.path.name,
                    right.path.name
                )
                startEating( ctx, 5.seconds, 3.seconds )

            case HandleChopstickAnswer( Busy( `chopstickToWaitFor` ) ) =>
                takenChopstick ! Put( adapter )
                startThinking( ctx, 10.milliseconds, 5.seconds )
        }
    }

    //When a hakker is eating, he can decide to start to think,
    //then he puts down his chopsticks and starts to think
    private lazy val eating : Behavior[ Command ] = {
        Behaviors.receiveMessagePartial {
            case Think =>
                ctx.log.info( "{} puts down his chopsticks and starts to think", name )
                left ! Put( adapter )
                right ! Put( adapter )
                startThinking( ctx, 5.seconds, 3.seconds )
        }
    }

    //When the results of the other grab comes back,
    //he needs to put it back if he got the other one.
    //Then go back and think and try to grab the chopsticks again
    private lazy val firstChopstickDenied : Behavior[ Command ] = {
        Behaviors.receiveMessagePartial {
            case HandleChopstickAnswer( Taken( chopstick ) ) =>
                chopstick ! Put( adapter )
                startThinking( ctx, 10.seconds, 5.seconds )
            case HandleChopstickAnswer( Busy( _ ) ) =>
                startThinking( ctx, 10.seconds, 5.seconds )
        }
    }

    private def startThinking( ctx : ActorContext[ Command ],
                               duration : FiniteDuration,
                               plusOrMinus : FiniteDuration ) : Behavior[ Command ] = {
        val dur = rndDuration( duration, plusOrMinus )
        Behaviors.withTimers[ Command ] { timers =>
            timers.startSingleTimer( Eat, Eat, dur )
            thinking
        }
    }

    private def startEating( ctx : ActorContext[ Command ],
                             duration : FiniteDuration,
                             plusOrMinus : FiniteDuration ) : Behavior[ Command ] = {
        val dur = rndDuration( duration, plusOrMinus )
        Behaviors.withTimers[ Command ] { timers =>
            timers.startSingleTimer( Think, Think, dur )
            eating
        }
    }
}

object DiningHakkersFsm extends DiningHakkers {

    override def chopstickBehavior : Behavior[ ChopstickMessage ] = ChopstickFsm.apply()

    override def hakkerBehavior( name : String, left : ActorRef[ ChopstickMessage ],
                                 right : ActorRef[ ChopstickMessage ] ) : Behavior[ Command ] = {
        HakkerFsm( name, left, right )
    }

}
