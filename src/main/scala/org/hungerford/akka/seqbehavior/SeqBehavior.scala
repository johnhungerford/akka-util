package org.hungerford.akka.seqbehavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.hungerford.akka.seqbehavior.implementations.{DoFsm, FsmSeqBehavior, PartialFsmSeqBehaviorPhase, StatefulPartialFsmSeqBehaviorPhase}

sealed trait BehaviorDecision[ +T, StateType ]

case object Stay extends BehaviorDecision[ Nothing, Nothing ]
case class StayWith[ StateType ]( newState : StateType ) extends BehaviorDecision[ Nothing, StateType ]
case class Return[ T, StateType ]( value : T ) extends BehaviorDecision[ T, StateType ]

trait SeqBehavior[ MessageType, +T ] {

    def behavior( returnBehavior : T => Behavior[ MessageType ] )( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ]

    def loop( times : Int ) : SeqBehavior[ MessageType, T ] = {
        if ( times < 1 ) throw new IllegalArgumentException( "Cannot loop less than 1 time" )
        else if ( times == 1 ) this
        else flatMap( _ => loop( times - 1 ) )
    }

    def loop : SeqBehavior[ MessageType, Unit ] = flatMap( _ => loop )

    def doWhile( condition : T => Boolean ) : SeqBehavior[ MessageType, T ] = {
        this.flatMap( t => {
            if ( condition( t ) ) doWhile( condition )
            else Skip[ MessageType, T ]( t )
        } )
    }

    def fold[ B ]( init : B )( fn : ((B, T)) => B )( times : Int ) : SeqBehavior[ MessageType, B ] = {
        if ( times < 0 ) throw new IllegalArgumentException( "Cannot loopFold less than 0 times" )
        else if ( times == 0 ) Skip( init )
        else if ( times == 1 ) map( t => fn( (init, t) ) )
        else map( t => fn( (init, t) ) ).flatMap( b => fold( b )( fn )( times - 1 ) )
    }

    def foldWhile[ B ]( init : B )( foldFn : (B, T) => B )( condition : (T, B) => Boolean ) : SeqBehavior[ MessageType, B ] = {
        this.flatMap( t => {
            val b = foldFn( init, t )
            if ( condition( t, b ) ) foldWhile( b )( foldFn )( condition )
            else Skip[ MessageType, B ]( b )
        } )
    }

    def doFirst( fn : ActorContext[ MessageType ] => Unit ) : SeqBehavior[ MessageType, T ] = {
        SeqBehavior.Do[ MessageType, Unit ]( fn ).flatMap( _ => this )
    }

    def onComplete( fn : ( ActorContext[ MessageType ], T ) => Unit ) : SeqBehavior[ MessageType, T ] = {
        flatMap( t => SeqBehavior.Do[ MessageType, T ]( ctx => {
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

    def map[ U ]( fn : T => U ) : SeqBehavior[ MessageType, U ]

    def flatMap[ U ]( fn : T => SeqBehavior[ MessageType, U ] ) : SeqBehavior[ MessageType, U ]

}

trait PartialSeqBehavior[ MessageType, +T ] extends SeqBehavior[ MessageType, T ] {

    val partialHandler :  ActorContext[ MessageType ] => PartialFunction[ MessageType, BehaviorDecision[ T, Nothing ] ]

    private[seqbehavior] def constructFromHandler[ B >: T ]( handler : ActorContext[ MessageType ] => PartialFunction[ MessageType, BehaviorDecision[ B, Nothing ] ] ) : PartialSeqBehavior[ MessageType, B ]

    private[seqbehavior] final def fromTwoHandlers[ B >: T ]( h1 : ActorContext[ MessageType ] => PartialFunction[ MessageType, BehaviorDecision[ B, Nothing ] ],
                                                        h2 : ActorContext[ MessageType ] => PartialFunction[ MessageType, BehaviorDecision[ B, Nothing ] ] ) : PartialSeqBehavior[ MessageType, B ] = {
        constructFromHandler( ctx => new PartialFunction[ MessageType, BehaviorDecision[ B, Nothing ] ] {
            private val h1f = h1( ctx )
            private val h2f = h2( ctx )
            override def isDefinedAt( x : MessageType ) : Boolean = h1f.isDefinedAt( x ) || h2f.isDefinedAt( x )
            override def apply( v1 : MessageType ) : BehaviorDecision[ B, Nothing ] = h1f.applyOrElse( v1, h2f )
        } )
    }

    final def :+[ B >: T ] ( that : PartialSeqBehavior[ MessageType, B ] ) : PartialSeqBehavior[ MessageType, B ] = {
        fromTwoHandlers( this.partialHandler, that.partialHandler )
    }

    final def +:[ B >: T ] ( that : PartialSeqBehavior[ MessageType, B ] ) : PartialSeqBehavior[ MessageType, B ] = {
        fromTwoHandlers( that.partialHandler, this.partialHandler )
    }

    override final def behavior( returnBehavior : T => Behavior[ MessageType ] )
                               ( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = {
        Behaviors.receiveMessage { msg : MessageType =>
            partialHandler( ctx )( msg ) match {
                case Stay => Behaviors.same
                case StayWith( _ ) => Behaviors.same
                case Return( value ) => returnBehavior( value )
            }
        }
    }
}

trait StatefulPartialSeqBehavior[ MessageType, +T, StateType ] extends SeqBehavior[ MessageType, T ] {

    type HandlerType[ B ] = ActorContext[ MessageType ] => PartialFunction[ (MessageType, StateType), BehaviorDecision[ B, StateType ] ]

    // Lifecycle definitions
    val initializer : ActorContext[ MessageType ] => StateType
    val partialHandler : ActorContext[ MessageType ] => PartialFunction[ (MessageType, StateType), BehaviorDecision[ T, StateType ] ]
    val cleanup : (ActorContext[ MessageType ], StateType) => Unit

    private[ seqbehavior ] def constructFromHandler[ B >: T ]( handler : HandlerType[ B ] ) : StatefulPartialSeqBehavior[ MessageType, B, StateType ]

    private[ seqbehavior ] final def fromTwoHandlers[ B >: T ]( h1 : HandlerType[ B ],
                                                                h2 : HandlerType[ B ] ) : StatefulPartialSeqBehavior[ MessageType, B, StateType ] = {
        constructFromHandler( ctx => new PartialFunction[ (MessageType, StateType), BehaviorDecision[ B, StateType ] ] {
            private val h1f = h1( ctx )
            private val h2f = h2( ctx )

            override def isDefinedAt( x : (MessageType, StateType) ) : Boolean = h1f.isDefinedAt( x ) || h2f.isDefinedAt( x )

            override def apply( v1 : (MessageType, StateType) ) : BehaviorDecision[ B, StateType ] = h1f.applyOrElse( v1, h2f )
        } )
    }


    final def :+[ B >: T ] ( that : StatefulPartialSeqBehavior[ MessageType, B, StateType ] ) : StatefulPartialSeqBehavior[ MessageType, B, StateType ] = {
        fromTwoHandlers( this.partialHandler, that.partialHandler )
    }

    final def +:[ B >: T ] ( that : StatefulPartialSeqBehavior[ MessageType, B, StateType ] ) : StatefulPartialSeqBehavior[ MessageType, B, StateType ] = {
        fromTwoHandlers( that.partialHandler, this.partialHandler )
    }

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

case class Skip[ MessageType, +T ]( returnVal : T ) extends SeqBehavior[ MessageType, T ] {
    override def behavior( returnBehavior : T => Behavior[ MessageType ] )
                         ( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = returnBehavior( returnVal )

    override def map[ U ]( fn : T => U ) : SeqBehavior[ MessageType, U ] = Skip( fn( returnVal ) )

    override def flatMap[ U ]( fn : T => SeqBehavior[ MessageType, U ] ) : SeqBehavior[ MessageType, U ] = {
        fn( returnVal )
    }
}

abstract class Do[ MessageType, +T ]( action : ActorContext[ MessageType ] => T ) extends SeqBehavior[ MessageType, T ] {
    override def behavior( returnBehavior : T => Behavior[ MessageType ] )
                         ( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = {
        val returnVal = action( ctx )
        returnBehavior( returnVal )
    }
}

object SeqBehavior extends {
    def builder : InitialSeqBehaviorBuilder = new InitialSeqBehaviorBuilder

    def Do[ MessageType, T ]( action : ActorContext[ MessageType ] => T ) : SeqBehavior[ MessageType, T ] = DoFsm( action )

    def fromHandler[ MessageType, T ]( handler : ActorContext[ MessageType ] => PartialFunction[ MessageType, BehaviorDecision[ T, Nothing ] ] ) : SeqBehavior[ MessageType, T ] = {
        new PartialFsmSeqBehaviorPhase[ MessageType, T ]( handler )
    }
}
