package org.hungerford.akka.behaviorphase

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

sealed trait BehaviorDecision[ +T, StateType ]

case object Stay extends BehaviorDecision[ Nothing, Nothing ]
case class StayWith[ StateType ]( newState : StateType ) extends BehaviorDecision[ Nothing, StateType ]
case class Return[ T, StateType ]( value : T ) extends BehaviorDecision[ T, StateType ]

trait BehaviorPhase[ MessageType, +T ] {

    def behavior( returnBehavior : T => Behavior[ MessageType ] )( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ]

    def loop( times : Int ) : BehaviorPhase[ MessageType, T ] = {
        if ( times < 1 ) throw new IllegalArgumentException( "Cannot loop less than 1 time" )
        else if ( times == 1 ) this
        else flatMap( _ => loop( times - 1 ) )
    }

    def loop : BehaviorPhase[ MessageType, Unit ] = flatMap( _ => loop )

    def doWhile( condition : T => Boolean ) : BehaviorPhase[ MessageType, T ] = {
        this.flatMap( t => {
            if ( condition( t ) ) doWhile( condition )
            else Skip[ MessageType, T ]( t )
        } )
    }

    def fold[ B ]( init : B )( fn : ((B, T)) => B )( times : Int ) : BehaviorPhase[ MessageType, B ] = {
        if ( times < 0 ) throw new IllegalArgumentException( "Cannot loopFold less than 0 times" )
        else if ( times == 0 ) Skip( init )
        else if ( times == 1 ) map( t => fn( (init, t) ) )
        else map( t => fn( (init, t) ) ).flatMap( b => fold( b )( fn )( times - 1 ) )
    }

    def foldWhile[ B ]( init : B )( foldFn : (B, T) => B )( condition : (T, B) => Boolean ) : BehaviorPhase[ MessageType, B ] = {
        this.flatMap( t => {
            val b = foldFn( init, t )
            if ( condition( t, b ) ) foldWhile( b )( foldFn )( condition )
            else Skip[ MessageType, B ]( b )
        } )
    }

    def doFirst( fn : ActorContext[ MessageType ] => Unit ) : BehaviorPhase[ MessageType, T ] = {
        Do[ MessageType, Unit ]( fn ).flatMap( _ => this )
    }

    def onComplete( fn : ( ActorContext[ MessageType ], T ) => Unit ) : BehaviorPhase[ MessageType, T ] = {
        flatMap( t => Do[ MessageType, T ]( ctx => {
            fn( ctx, t )
            t
        } ) )
    }

    final def setup : Behavior[ MessageType ] = Behaviors.setup[ MessageType ]( implicit ctx => behavior( _ => Behaviors.stopped ) )

    final def setupWith( fn : T => Unit ) : Behavior[ MessageType ] = {
        Behaviors.setup[ MessageType ]( implicit ctx => behavior { t : T =>
            fn( t )
            Behaviors.stopped
        } )
    }

    final def setupLoop : Behavior[ MessageType ] = {
        def bhv() : Behavior[ MessageType ] = Behaviors.setup( implicit ctx => behavior( _ => bhv() ) )
        bhv()
    }

    def map[ U ]( fn : T => U ) : BehaviorPhase[ MessageType, U ] = {
        def originalBehavior( handler : T => Behavior[ MessageType ] )( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = behavior( handler )
        new BehaviorPhase[ MessageType, U ] {
            override def behavior( retBehav : U => Behavior[ MessageType ] )
                                 ( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = {
                originalBehavior( t => retBehav( fn( t ) ) )
            }
        }
    }

    def flatMap[ U ]( fn : T => BehaviorPhase[ MessageType, U ] ) : BehaviorPhase[ MessageType, U ] = {
        def originalBehavior( handler : T => Behavior[ MessageType ] )( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = behavior( handler )
        new BehaviorPhase[ MessageType, U ] {
            override def behavior( retBehav : U => Behavior[ MessageType ] )( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = {
                originalBehavior( t => fn( t ).behavior( retBehav ) )
            }
        }
    }
}

trait HandlerBehaviorPhase[ MessageType, +T ] extends BehaviorPhase[ MessageType, T ] {

    val partialHandler :  ActorContext[ MessageType ] => PartialFunction[ MessageType, BehaviorDecision[ T, Nothing ] ]

    override final def behavior( returnBehavior : T => Behavior[ MessageType ] )
                               ( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = {
        Behaviors.receiveMessage { msg : MessageType =>
            val partialHandlerFn : PartialFunction[ MessageType, BehaviorDecision[ T, Nothing ] ] = {
                partialHandler( ctx ) orElse ( {
                    case _ => Stay
                } )
            }

            partialHandlerFn( msg ) match {
                case Stay => Behaviors.same
                case StayWith( _ ) => Behaviors.same
                case Return( value ) => returnBehavior( value )
            }
        }
    }
}

trait StatefulHandlerBehaviorPhase[ MessageType, +T, StateType ] extends BehaviorPhase[ MessageType, T ] {

    type HandlerType[ B ] = ActorContext[ MessageType ] => PartialFunction[ (MessageType, StateType), BehaviorDecision[ B, StateType ] ]

    // Lifecycle definitions
    val initializer : ActorContext[ MessageType ] => StateType
    val partialHandler : ActorContext[ MessageType ] => PartialFunction[ (MessageType, StateType), BehaviorDecision[ T, StateType ] ]
    val cleanup : (ActorContext[ MessageType ], StateType) => Unit

    override final def behavior( returnBehavior : T => Behavior[ MessageType ] )
                               ( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = {

        val initState = initializer( ctx )

        def behaviorFn( state : StateType ) : Behavior[ MessageType ] = {
            Behaviors.receiveMessage { msg : MessageType =>
                partialHandler( ctx )( (msg, state) ) match {
                    case StayWith( newState ) => {
                        behaviorFn( newState )
                    }
                    case Return( value : T ) =>
                        cleanup( ctx, state )
                        returnBehavior( value )
                }
            }
        }

        behaviorFn( initState )
    }
}

case class Do[ MessageType, +T ]( action : ActorContext[ MessageType ] => T ) extends BehaviorPhase[ MessageType, T ] {
    override def behavior( returnBehavior : T => Behavior[ MessageType ] )
                         ( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = {
        val returnVal = action( ctx )
        returnBehavior( returnVal )
    }
}

case class Skip[ MessageType, +T ]( returnVal : T ) extends BehaviorPhase[ MessageType, T ] {
    override def behavior( returnBehavior : T => Behavior[ MessageType ] )
                         ( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = returnBehavior( returnVal )

    override def map[ U ]( fn : T => U ) : BehaviorPhase[ MessageType, U ] = Skip( fn( returnVal ) )

    override def flatMap[ U ]( fn : T => BehaviorPhase[ MessageType, U ] ) : BehaviorPhase[ MessageType, U ] = {
        fn( returnVal )
    }
}

object BehaviorPhase extends {

    def fromHandler[ MessageType, T ]( handler : ActorContext[ MessageType ] => PartialFunction[ MessageType, BehaviorDecision[ T, Nothing ] ] ) : BehaviorPhase[ MessageType, T ] = {
        new HandlerBehaviorPhase[ MessageType, T ] {
            override val partialHandler : ActorContext[ MessageType ] => PartialFunction[ MessageType, BehaviorDecision[ T, Nothing ] ] = {
                handler
            }
        }
    }
}
