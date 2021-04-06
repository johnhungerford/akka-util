package org.hungerford.akka.seqbehavior

import akka.actor.typed.scaladsl.ActorContext
import org.hungerford.akka.seqbehavior.implementations.{PartialFsmSeqBehaviorPhase, PartialSpawnedSeqBehaviorPhase, StatefulPartialFsmSeqBehaviorPhase}

trait SeqBehaviorType
case object FSM extends SeqBehaviorType
case object Spawned extends SeqBehaviorType

trait SeqBehaviorBuilder

class InitialSeqBehaviorBuilder extends SeqBehaviorBuilder {
    def stateful[ MessageType, ReturnType, StateType ] : InitialStatefulSeqBehaviorBuilder[ MessageType, ReturnType, StateType ] = new InitialStatefulSeqBehaviorBuilder[ MessageType, ReturnType, StateType ]( None )
    def stateless[ MessageType, ReturnType ] : InitialStatelessSeqBehaviorBuilder[ MessageType, ReturnType ] = new InitialStatelessSeqBehaviorBuilder[ MessageType, ReturnType ]
}

trait StatefulSeqBehaviorBuilder[ MessageType, ReturnType, StateType ] extends SeqBehaviorBuilder {
    def initialState( state : StateType ) : StatefulSeqBehaviorBuilder[ MessageType, ReturnType, StateType ]
    def initializer( handler : ActorContext[ MessageType ] => StateType ) : StatefulSeqBehaviorBuilder[ MessageType, ReturnType, StateType ]
    def cleanup( callback : (ActorContext[ MessageType ], StateType) => Unit ) : StatefulSeqBehaviorBuilder[ MessageType, ReturnType, StateType ]
    def onMessage( handler : ActorContext[ MessageType ] => PartialFunction[ (MessageType, StateType), BehaviorDecision[ ReturnType, StateType ] ] ) : StatefulSeqBehaviorBuilder[ MessageType, ReturnType, StateType ]
}

class InitialStatefulSeqBehaviorBuilder[ MessageType, ReturnType, StateType ](
    private[seqbehavior] val cleanupOpt : Option[ (ActorContext[ MessageType ], StateType) => Unit ],
) extends StatefulSeqBehaviorBuilder[ MessageType, ReturnType, StateType ] {
    override def initialState( state : StateType ) : StatefulSeqBehaviorBuilderWithInitializer[ MessageType, ReturnType, StateType ] = {
        new StatefulSeqBehaviorBuilderWithInitializer[ MessageType, ReturnType, StateType ]( cleanupOpt, _ => state )
    }
    override def initializer( handler : ActorContext[ MessageType ] => StateType ) : StatefulSeqBehaviorBuilderWithInitializer[ MessageType, ReturnType, StateType ] = {
        new StatefulSeqBehaviorBuilderWithInitializer[ MessageType, ReturnType, StateType ]( cleanupOpt, handler )
    }
    override def cleanup( callback : (ActorContext[ MessageType ], StateType) => Unit ) : InitialStatefulSeqBehaviorBuilder[ MessageType, ReturnType, StateType ] = {
        new InitialStatefulSeqBehaviorBuilder[ MessageType, ReturnType, StateType ]( Some( callback ) )
    }
    override def onMessage( handler : ActorContext[ MessageType ] => PartialFunction[ (MessageType, StateType), BehaviorDecision[ ReturnType, StateType ] ] ) 
    : StatefulSeqBehaviorBuilderWithOnMessage[ MessageType, ReturnType, StateType ] = {
        new StatefulSeqBehaviorBuilderWithOnMessage[ MessageType, ReturnType, StateType ]( cleanupOpt, handler )
    }
}

class StatefulSeqBehaviorBuilderWithInitializer[ MessageType, ReturnType, StateType ]( 
    private[seqbehavior] val cleanupOpt : Option[ (ActorContext[ MessageType ], StateType) => Unit ],
    private[seqbehavior] val initializer : ActorContext[ MessageType ] => StateType,
) extends StatefulSeqBehaviorBuilder[ MessageType, ReturnType, StateType ] {
    override def initialState( state : StateType ) : StatefulSeqBehaviorBuilderWithInitializer[ MessageType, ReturnType, StateType ] = {
        new StatefulSeqBehaviorBuilderWithInitializer[ MessageType, ReturnType, StateType ]( cleanupOpt, _ => state )
    }
    override def initializer( handler : ActorContext[ MessageType ] => StateType ) : StatefulSeqBehaviorBuilderWithInitializer[ MessageType, ReturnType, StateType ] = {
        new StatefulSeqBehaviorBuilderWithInitializer[ MessageType, ReturnType, StateType ]( cleanupOpt, handler )
    }
    override def cleanup( callback : (ActorContext[ MessageType ], StateType) => Unit ) : StatefulSeqBehaviorBuilderWithInitializer[ MessageType, ReturnType, StateType ] = {
        new StatefulSeqBehaviorBuilderWithInitializer[ MessageType, ReturnType, StateType ]( cleanupOpt, initializer )
    }
    override def onMessage( handler : ActorContext[ MessageType ] => PartialFunction[ (MessageType, StateType), BehaviorDecision[ ReturnType, StateType ] ] )
    : StatefulSeqBehaviorBuilderReadyToBuild[ MessageType, ReturnType, StateType ] = {
        new StatefulSeqBehaviorBuilderReadyToBuild[ MessageType, ReturnType, StateType ]( initializer, cleanupOpt, handler )
    }
}

class StatefulSeqBehaviorBuilderWithOnMessage[ MessageType, ReturnType, StateType ](
    private[seqbehavior] val cleanupOpt : Option[ (ActorContext[ MessageType ], StateType) => Unit ],
    private[seqbehavior] val onMessage : ActorContext[ MessageType ] => PartialFunction[ (MessageType, StateType), BehaviorDecision[ ReturnType, StateType ] ],
) extends StatefulSeqBehaviorBuilder[ MessageType, ReturnType, StateType ] {
    override def initialState( state : StateType ) : StatefulSeqBehaviorBuilderReadyToBuild[ MessageType, ReturnType, StateType ] = {
        new StatefulSeqBehaviorBuilderReadyToBuild[ MessageType, ReturnType, StateType ]( _ => state, cleanupOpt, onMessage )
    }
    override def initializer( handler : ActorContext[ MessageType ] => StateType ) : StatefulSeqBehaviorBuilderReadyToBuild[ MessageType, ReturnType, StateType ] = {
        new StatefulSeqBehaviorBuilderReadyToBuild[ MessageType, ReturnType, StateType ]( handler, cleanupOpt, onMessage )
    }
    override def cleanup( callback : (ActorContext[ MessageType ], StateType) => Unit ) : StatefulSeqBehaviorBuilderWithOnMessage[ MessageType, ReturnType, StateType ] = {
        new StatefulSeqBehaviorBuilderWithOnMessage[ MessageType, ReturnType, StateType ]( Some( callback ), onMessage )
    }
    override def onMessage( handler : ActorContext[ MessageType ] => PartialFunction[ (MessageType, StateType), BehaviorDecision[ ReturnType, StateType ] ] )
    : StatefulSeqBehaviorBuilderWithOnMessage[ MessageType, ReturnType, StateType ] = {
        new StatefulSeqBehaviorBuilderWithOnMessage[ MessageType, ReturnType, StateType ]( cleanupOpt, handler )
    }
}

class StatefulSeqBehaviorBuilderReadyToBuild[ MessageType, ReturnType, StateType ](
    private[seqbehavior] val initializer : ActorContext[ MessageType ] => StateType,
    private[seqbehavior] val cleanupOpt : Option[ (ActorContext[ MessageType ], StateType) => Unit ],
    private[seqbehavior] val onMessage : ActorContext[ MessageType ] => PartialFunction[ (MessageType, StateType), BehaviorDecision[ ReturnType, StateType ] ],
) extends StatefulSeqBehaviorBuilder[ MessageType, ReturnType, StateType ] {
    def initialState( state : StateType) : StatefulSeqBehaviorBuilderReadyToBuild[ MessageType, ReturnType, StateType ] = {
        new StatefulSeqBehaviorBuilderReadyToBuild[ MessageType, ReturnType, StateType ]( _ => state, cleanupOpt, onMessage )
    }
    def initializer( handler : ActorContext[ MessageType ] => StateType ) : StatefulSeqBehaviorBuilderReadyToBuild[ MessageType, ReturnType, StateType ] = {
        new StatefulSeqBehaviorBuilderReadyToBuild[ MessageType, ReturnType, StateType ]( handler, cleanupOpt, onMessage )
    }
    def cleanup( callback : (ActorContext[ MessageType ], StateType) => Unit ) : StatefulSeqBehaviorBuilderReadyToBuild[ MessageType, ReturnType, StateType ] = {
        new StatefulSeqBehaviorBuilderReadyToBuild[ MessageType, ReturnType, StateType ]( initializer, Some( callback ), onMessage )
    }
    def onMessage( handler : ActorContext[ MessageType ] => PartialFunction[ (MessageType, StateType), BehaviorDecision[ ReturnType, StateType ] ] ) : StatefulSeqBehaviorBuilderReadyToBuild[ MessageType, ReturnType, StateType ] = {
        new StatefulSeqBehaviorBuilderReadyToBuild[ MessageType, ReturnType, StateType ]( initializer, cleanupOpt, handler )
    }

    def buildFsm() : StatefulPartialFsmSeqBehaviorPhase[ MessageType, ReturnType, StateType ] = {
        new StatefulPartialFsmSeqBehaviorPhase[ MessageType, ReturnType, StateType ](
            initializer,
            onMessage,
            cleanupOpt.getOrElse( (a, b) => () )
        )
    }
//    def buildSpawned() : StatefulPartialSpawnedSeqBehaviorPhase[ MessageType, ReturnType, StateType ]
}

trait StatelessSeqBehaviorBuilder[ MessageType, ReturnType ] extends SeqBehaviorBuilder

class InitialStatelessSeqBehaviorBuilder[ MessageType, ReturnType ] extends StatelessSeqBehaviorBuilder[ MessageType, ReturnType ] {
    def onMessage( handler : ActorContext[ MessageType ] => PartialFunction[ MessageType, BehaviorDecision[ ReturnType, Nothing ] ] ) : FinalStatelessSeqBehavior[ MessageType, ReturnType ] = {
        new FinalStatelessSeqBehavior[ MessageType, ReturnType ]( handler )
    }
}

class FinalStatelessSeqBehavior[ MessageType, ReturnType ]( private[seqbehavior] val onMessageValue : ActorContext[ MessageType ] => PartialFunction[ MessageType , BehaviorDecision[ ReturnType, Nothing ] ] )
  extends StatelessSeqBehaviorBuilder[ MessageType, ReturnType ] {
    def onMessage[ NewReturnType ]( handler : ActorContext[ MessageType ] => PartialFunction[ MessageType, BehaviorDecision[ NewReturnType, Nothing ] ] ) : FinalStatelessSeqBehavior[ MessageType, NewReturnType ] = {
        new FinalStatelessSeqBehavior[ MessageType, NewReturnType ]( handler )
    }

    def buildFsm() : PartialFsmSeqBehaviorPhase[ MessageType, ReturnType ] = {
        new PartialFsmSeqBehaviorPhase[ MessageType, ReturnType ]( onMessageValue )
    }
    //    def buildSpawned() : StatefulPartialSpawnedSeqBehaviorPhase[ MessageType, ReturnType, StateType ]
}
