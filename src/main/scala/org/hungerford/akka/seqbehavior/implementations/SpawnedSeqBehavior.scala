package org.hungerford.akka.seqbehavior.implementations

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import org.hungerford.akka.seqbehavior.{BehaviorDecision, PartialSeqBehavior, SeqBehavior}

trait SpawnedSeqBehavior[ MessageType, +T ] extends SeqBehavior[ MessageType, T ] {
    def behavior( returnBehavior : T => Behavior[ MessageType ] )( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ]

    def map[ U ]( fn : T => U ) : SpawnedSeqBehavior[ MessageType, U ] = {
        ???
//        def originalBehavior( handler : T => Behavior[ MessageType ] )( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = behavior( handler )
//
//        new SpawnedSeqBehavior[ MessageType, U ] {
//            override def behavior( retBehav : U => Behavior[ MessageType ] )
//                                 ( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = {
//                originalBehavior( t => retBehav( fn( t ) ) )
//            }
//        }
    }

    def flatMap[ U ]( fn : T => SeqBehavior[ MessageType, U ] ) : FsmSeqBehavior[ MessageType, U ] = {
        ???
//        def originalBehavior( handler : T => Behavior[ MessageType ] )( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = behavior( handler )
//
//        new FsmSeqBehavior[ MessageType, U ] {
//            override def behavior( retBehav : U => Behavior[ MessageType ] )( implicit ctx : ActorContext[ MessageType ] ) : Behavior[ MessageType ] = {
//                originalBehavior( t => fn( t ).behavior( retBehav ) )
//            }
//        }
    }
}

class PartialSpawnedSeqBehaviorPhase[ MessageType, T ]( override val partialHandler :  ActorContext[ MessageType ] => PartialFunction[ MessageType, BehaviorDecision[ T, Nothing ] ] )
  extends PartialSeqBehavior[ MessageType, T ] with SpawnedSeqBehavior[ MessageType, T ] {

    override private[ seqbehavior ] def constructFromHandler[ B >: T ]( handler : ActorContext[ MessageType ] => PartialFunction[ MessageType, BehaviorDecision[ B, Nothing ] ] ) : PartialSpawnedSeqBehaviorPhase[ MessageType, B ] = {
        new PartialSpawnedSeqBehaviorPhase( handler )
    }
}
