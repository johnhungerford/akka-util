package org.hungerford.akka.dininghakkers

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.hungerford.akka.dininghakkers.Chopstick.ChopstickMessage
import org.hungerford.akka.dininghakkers.Hakker.Command

import java.util.UUID
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object ChopstickSubActors {
    import Chopstick._

    def apply( ) : Behavior[ ChopstickMessage ] = {
        Behaviors.setup( ctx => new ChopstickSubActors( ctx ) )
    }
}

class ChopstickSubActors( ctx : ActorContext[ ChopstickMessage ] ) extends AbstractBehavior[ ChopstickMessage ]( ctx ) {

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
object HakkerSubActors {
    def apply( name : String, left : ActorRef[ ChopstickMessage ], right : ActorRef[ ChopstickMessage ] ) : Behavior[ Command ] =
        Behaviors.setup { ctx =>
            new HakkerSubActors( ctx, name, left, right )
        }
}

class HakkerSubActors( ctx : ActorContext[ Command ],
                   name : String,
                   left : ActorRef[ ChopstickMessage ],
                   right : ActorRef[ ChopstickMessage ] )
  extends AbstractBehavior[ Command ]( ctx ) {

    import Hakker._
    import Chopstick._

    // Enum of various hakker states
    private trait HakkerState
    private case object Waiting extends HakkerState
    private case object TryingToEat extends HakkerState
    private case object Eating extends HakkerState
    private case object Thinking extends HakkerState

    // MUTABLE STATE
    private var currentState : HakkerState = Waiting
    private var takeLeftChopstickSuccess : Option[ Boolean ] = None
    private var takeRightChopstickSuccess : Option[ Boolean ] = None

    private val adapter = ctx.messageAdapter( HandleChopstickAnswer )

    trait PhaseMessages extends Command
    case object FinishedWaiting extends PhaseMessages
    case object FinishedEating extends PhaseMessages
    case object FinishedThinking extends PhaseMessages
    case object GotChopsticks extends PhaseMessages
    case object FailedToGetChopsticks extends PhaseMessages

    private lazy val waitHandler = ctx.spawn( waitingBehavior( ctx.self ), "waitHandler" )
    private var eatHandler : Option[ ActorRef[ Command ] ] = None
    private var thinkHandler : Option[ ActorRef[ Command ] ] = None
    private var tryToEatHandler : Option[ ActorRef[ Command ] ] = None

    override def onMessage( msg : Command ) : Behavior[ Command ] = msg match {
            case FinishedWaiting =>
                thinkHandler = Some( ctx.spawn( thinkingBehavior( ctx.self, 5.seconds, 3.seconds ),
                                                s"thinkHandler-${UUID.randomUUID()}" ) )
                ctx.log.info( "{} finished waiting and started thinking", name )
                currentState = Thinking
                Behaviors.same
            case FinishedEating =>
                eatHandler = None
                thinkHandler = Some( ctx.spawn( thinkingBehavior( ctx.self, 5.seconds, 3.seconds ),
                                                s"thinkHandler-${UUID.randomUUID()}" ) )
                ctx.log.info( "{} finished eating and started thinking", name )
                currentState = Thinking
                Behaviors.same
            case FinishedThinking =>
                thinkHandler = None
                tryToEatHandler = Some( ctx.spawn( tryingToEatBehavior( ctx.self ),
                                                   s"tryToEatHandler-${UUID.randomUUID()}" ) )
                ctx.log.info( "{} finished thinking and started trying to eat", name )
                currentState = TryingToEat
                Behaviors.same
            case GotChopsticks =>
                tryToEatHandler = None
                eatHandler = Some( ctx.spawn( eatingBehavior( ctx.self, 5.seconds, 3.seconds ),
                                              s"eatHandler-${UUID.randomUUID()}" ) )
                ctx.log.info( "{} got both chopsticks and started eating", name )
                currentState = Eating
                Behaviors.same
            case FailedToGetChopsticks =>
                tryToEatHandler = None
                thinkHandler = Some( ctx.spawn( thinkingBehavior( ctx.self, 10.seconds, 5.seconds ),
                                                s"thinkHandler-${UUID.randomUUID()}" ) )
                ctx.log.info( "{} couldn't get both chopsticks and started thinking", name )
                currentState = Thinking
                Behaviors.same

            case otherMsg =>
                currentState match {
                    case Waiting =>
                        waitHandler ! otherMsg
                        Behaviors.same
                    case Eating =>
                        eatHandler.get ! otherMsg
                        Behaviors.same
                    case Thinking =>
                        thinkHandler.get ! otherMsg
                        Behaviors.same
                    case TryingToEat =>
                        tryToEatHandler.get ! otherMsg
                        Behaviors.same
                }
    }


    private def waitingBehavior( parent : ActorRef[ Command ] ) : Behavior[ Command ] = Behaviors.receiveMessage {
        case Eat =>
            parent ! FinishedWaiting
            Behaviors.stopped
        case Think =>
            parent ! FinishedWaiting
            Behaviors.stopped
        case _ =>
            Behaviors.same
    }

    private def eatingBehavior(
        parent : ActorRef[ Command ],
        duration : FiniteDuration,
        plusOrMinus : FiniteDuration
    ) : Behavior[ Command ] = Behaviors.setup( ctx => {
        ctx.scheduleOnce( rndDuration( duration, plusOrMinus ), ctx.self, Think )
        Behaviors.receiveMessage {
            case Think =>
                parent ! FinishedEating
                Behaviors.stopped
            case _ =>
                Behaviors.same
        }
    } )

    private def thinkingBehavior(
        parent : ActorRef[ Command ],
        duration : FiniteDuration,
        plusOrMinus : FiniteDuration
    ) : Behavior[ Command ] = Behaviors.setup( ctx => {
        ctx.scheduleOnce( rndDuration( duration, plusOrMinus ), ctx.self, Eat )
        Behaviors.receiveMessage {
            case Eat =>
                parent ! FinishedThinking
                Behaviors.stopped
            case _ =>
                Behaviors.same
        }
    } )

    private def tryingToEatBehavior( parent : ActorRef[ Command ] ) : Behavior[ Command ] = Behaviors.setup( ctx => {
        left ! Take( adapter )
        right ! Take( adapter )

        Behaviors.receiveMessage {
            case HandleChopstickAnswer( Busy( `left` ) ) =>
                if ( takeRightChopstickSuccess.nonEmpty ) {
                    if ( takeRightChopstickSuccess.get ) right ! Put( adapter )
                    ctx.log.info( "{} could not pick up {} so he put down {}", name, left.path.name, right.path.name )
                    takeLeftChopstickSuccess = None
                    takeRightChopstickSuccess = None
                    parent ! FailedToGetChopsticks
                    Behaviors.stopped
                } else {
                    takeLeftChopstickSuccess = Some( false )
                    ctx.log.info( "{} could not pick up {}", name, left.path.name )
                    Behaviors.same
                }

            case HandleChopstickAnswer( Busy( `right` ) ) =>
                if ( takeLeftChopstickSuccess.nonEmpty ) {
                    if ( takeLeftChopstickSuccess.get ) left ! Put( adapter )
                    takeLeftChopstickSuccess = None
                    takeRightChopstickSuccess = None
                    parent ! FailedToGetChopsticks
                    ctx.log.info( "{} could not pick up {} so he put down {}", name, right.path.name, left.path.name )
                    Behaviors.stopped
                } else {
                    takeRightChopstickSuccess = Some( false )
                    ctx.log.info( "{} could not pick up {}", name, right.path.name )
                    Behaviors.same
                }

            // If one of the chopsticks was able to be taken successfully
            case HandleChopstickAnswer( Taken( `left` ) ) =>
                if ( takeRightChopstickSuccess.nonEmpty ) {
                    if ( !takeRightChopstickSuccess.get ) {
                        left ! Put( adapter )
                        takeLeftChopstickSuccess = None
                        takeRightChopstickSuccess = None
                        parent ! FailedToGetChopsticks
                        ctx.log.info( "{} picked up {} but because {} could not be picked up put it down", name, left.path.name, right.path.name )
                        Behaviors.stopped
                    } else {
                        parent ! GotChopsticks
                        ctx.log.info( "{} picked up {}", name, left.path.name )
                        Behaviors.stopped
                    }
                } else {
                    takeLeftChopstickSuccess = Some( true )
                    ctx.log.info( "{} picked up {}", name, left.path.name )
                    Behaviors.same
                }

            case HandleChopstickAnswer( Taken( `right` ) ) =>
                if ( takeLeftChopstickSuccess.nonEmpty ) {
                    if ( !takeLeftChopstickSuccess.get ) {
                        right ! Put( adapter )
                        takeLeftChopstickSuccess = None
                        takeRightChopstickSuccess = None
                        parent ! FailedToGetChopsticks
                        ctx.log.info( "{} picked up {} but because {} could not be picked up put it down", name, right.path.name, left.path.name )
                        Behaviors.stopped
                    } else {
                        parent ! GotChopsticks
                        ctx.log.info( "{} picked up {}", name, right.path.name )
                        Behaviors.stopped
                    }
                } else {
                    takeRightChopstickSuccess = Some( true )
                    ctx.log.info( "{} picked up {}", name, right.path.name )
                    Behaviors.same
                }

            case _ =>
                Behaviors.same
        }
    } )

}

object DiningHakkersSubActors extends DiningHakkers {

    override def chopstickBehavior : Behavior[ ChopstickMessage ] = ChopstickSubActors.apply()

    override def hakkerBehavior( name : String, left : ActorRef[ ChopstickMessage ],
                                 right : ActorRef[ ChopstickMessage ] ) : Behavior[ Command ] = {
        HakkerSubActors( name, left, right )
    }

}
