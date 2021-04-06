package org.hungerford.akka.seqbehavior.implementations

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.hungerford.akka.seqbehavior.{BehaviorDecision, Do, PartialSeqBehavior, Return, SeqBehavior, StatefulPartialSeqBehavior, Stay}

trait FsmSeqBehavior[ MessageType, +T ] extends SeqBehavior[ MessageType, T ] {

    def behavior( returnBehavior : T => Behavior[ MessageType ] )( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ]

    def map[ U ]( fn : T => U ) : FsmSeqBehavior[ MessageType, U ] = {
        def originalBehavior( handler : T => Behavior[ MessageType ] )( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = behavior( handler )
        new FsmSeqBehavior[ MessageType, U ] {
            override def behavior( retBehav : U => Behavior[ MessageType ] )
                                 ( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = {
                originalBehavior( t => retBehav( fn( t ) ) )
            }
        }
    }

    def flatMap[ U ]( fn : T => SeqBehavior[ MessageType, U ] ) : FsmSeqBehavior[ MessageType, U ] = {
        def originalBehavior( handler : T => Behavior[ MessageType ] )( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = behavior( handler )
        new FsmSeqBehavior[ MessageType, U ] {
            override def behavior( retBehav : U => Behavior[ MessageType ] )( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = {
                originalBehavior( t => fn( t ).behavior( retBehav ) )
            }
        }
    }
}

class PartialFsmSeqBehaviorPhase[ MessageType, +T ]( val partialHandler :  ActorContext[ MessageType ] => PartialFunction[ MessageType, BehaviorDecision[ T, Nothing ] ] )
  extends PartialSeqBehavior[ MessageType, T ] with FsmSeqBehavior[ MessageType, T ] {

    override private[ seqbehavior ] def constructFromHandler[ B >: T ]( handler : ActorContext[ MessageType ] => PartialFunction[ MessageType, BehaviorDecision[ B, Nothing ] ] ) : PartialFsmSeqBehaviorPhase[ MessageType, B ] = {
        new PartialFsmSeqBehaviorPhase( handler )
    }
}

class StatefulPartialFsmSeqBehaviorPhase[ MessageType, +T, StateType ](
    override val initializer : ActorContext[ MessageType ] => StateType,
    override val partialHandler :  ActorContext[ MessageType ] => PartialFunction[ (MessageType, StateType), BehaviorDecision[ T, StateType ] ],
    override val cleanup : (ActorContext[ MessageType ], StateType) => Unit
)
  extends StatefulPartialSeqBehavior[ MessageType, T, StateType ] with FsmSeqBehavior[ MessageType, T ] {
    override private[ seqbehavior ] def constructFromHandler[ B >: T ]( handler : ActorContext[ MessageType ] => PartialFunction[ (MessageType, StateType), BehaviorDecision[ B,
      StateType ] ] ) : StatefulPartialFsmSeqBehaviorPhase[ MessageType, B, StateType ] = {
        new StatefulPartialFsmSeqBehaviorPhase( initializer, handler, cleanup )
    }
}

case class DoFsm[ MessageType, +T ]( action : ActorContext[ MessageType ] => T ) extends Do[ MessageType, T ]( action ) with FsmSeqBehavior[ MessageType, T ]
