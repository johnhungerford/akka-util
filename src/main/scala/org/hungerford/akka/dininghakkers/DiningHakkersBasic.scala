package org.hungerford.akka.dininghakkers

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, AbstractBehavior}
import org.hungerford.akka.dininghakkers.Chopstick.{Busy, ChopstickAnswer, ChopstickMessage, Put, Take, Taken}
import org.hungerford.akka.dininghakkers.Hakker.{Command, Eat, HandleChopstickAnswer, Think}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object ChopstickBasic {
    def apply( ) : Behavior[ ChopstickMessage ] = {
        Behaviors.setup( ctx => new ChopstickBasic( ctx ) )
    }
}

class ChopstickBasic( ctx : ActorContext[ ChopstickMessage ] ) extends AbstractBehavior[ ChopstickMessage ]( ctx ) {

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
object HakkerBasic {
    def apply( name : String, left : ActorRef[ ChopstickMessage ], right : ActorRef[ ChopstickMessage ] ) : Behavior[ Command ] =
        Behaviors.setup { ctx =>
            new HakkerBasic( ctx, name, left, right )
        }
}

class HakkerBasic( ctx : ActorContext[ Command ],
                   name : String,
                   left : ActorRef[ ChopstickMessage ],
                   right : ActorRef[ ChopstickMessage ] )
  extends AbstractBehavior[ Command ]( ctx ) {

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

    override def onMessage( msg : Command ) : Behavior[ Command ] = {
        currentState match {
            case Waiting =>
                msg match {
                    case Eat =>
                        currentState = Thinking
                        ctx.scheduleOnce( 5.seconds, ctx.self, Eat )
                        ctx.log.info( "{} started thinking" )
                        Behaviors.same
                    case _ => Behaviors.unhandled
                }

            case Thinking =>
                msg match {
                    case Eat =>
                        currentState = TryingToEat
                        left ! Take( adapter )
                        right ! Take( adapter )
                        ctx.log.info( "{} began trying to eat", name )
                        Behaviors.same
                    case _ => Behaviors.unhandled
                }

            case TryingToEat =>
                msg match {
                    // If one of the chopsticks is already taken by someone else
                    case HandleChopstickAnswer( Busy( `left` ) ) =>
                        if ( takeRightChopstickSuccess.nonEmpty ) {
                            if ( takeRightChopstickSuccess.get ) right ! Put( adapter )
                            currentState = Thinking
                            takeLeftChopstickSuccess = None
                            takeRightChopstickSuccess = None
                            ctx.scheduleOnce( 7.seconds, ctx.self, Eat )
                            ctx.log.info( "{} could not pick up {} so he put down {} and started to think", name, left.path.name, right.path.name )
                        } else if ( takeRightChopstickSuccess.isEmpty ) {
                            takeLeftChopstickSuccess = Some( false )
                            ctx.log.info( "{} could not pick up {}", name, left.path.name )
                        }
                        Behaviors.same
                    case HandleChopstickAnswer( Busy( `right` ) ) =>
                        if ( takeLeftChopstickSuccess.nonEmpty ) {
                            if ( takeLeftChopstickSuccess.get ) left ! Put( adapter )
                            currentState = Thinking
                            takeLeftChopstickSuccess = None
                            takeRightChopstickSuccess = None
                            ctx.scheduleOnce( 4.seconds, ctx.self, Eat )
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
                                currentState = Thinking
                                takeLeftChopstickSuccess = None
                                takeRightChopstickSuccess = None
                                ctx.scheduleOnce( 10.seconds, ctx.self, Eat )
                                ctx.log.info( "{} picked up {} but because {} could not be picked up put it down and started to think", name, left.path.name, right.path.name )
                            } else {
                                currentState = Eating
                                ctx.scheduleOnce( 8.seconds, ctx.self, Think )
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
                                currentState = Thinking
                                takeLeftChopstickSuccess = None
                                takeRightChopstickSuccess = None
                                ctx.scheduleOnce( 6.seconds, ctx.self, Eat )
                                ctx.log.info( "{} picked up {} but because {} could not be picked up put it down and started to think", name, right.path.name, left.path.name )
                            } else {
                                currentState = Eating
                                ctx.scheduleOnce( 9.seconds, ctx.self, Think )
                                ctx.log.info( "{} picked up {} and started to eat", name, left.path.name )
                            }
                        } else {
                            takeRightChopstickSuccess = Some( true )
                            ctx.log.info( "{} picked up {}", name, left.path.name )
                        }
                        Behaviors.same

                    // The hakker should only be getting chopstick responses at this
                    // point
                    case _ => Behaviors.unhandled
                }

            case Eating =>
                msg match {
                    case Think =>
                        currentState = Thinking
                        left ! Put( adapter )
                        takeLeftChopstickSuccess = None
                        right ! Put( adapter )
                        takeRightChopstickSuccess = None
                        ctx.scheduleOnce( 5.seconds, ctx.self, Eat )
                        ctx.log.info( "{} started to think", name )
                        Behaviors.same

                    case _ => Behaviors.unhandled
                }

        }
    }
}

object DiningHakkersBasic extends DiningHakkers {

    override def chopstickBehavior : Behavior[ ChopstickMessage ] = ChopstickBasic.apply()

    override def hakkerBehavior( name : String, left : ActorRef[ ChopstickMessage ],
                                 right : ActorRef[ ChopstickMessage ] ) : Behavior[ Command ] = {
        HakkerBasic( name, left, right )
    }

}
