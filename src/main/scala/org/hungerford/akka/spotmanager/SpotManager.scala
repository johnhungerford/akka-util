package org.hungerford.akka.spotmanager

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import org.hungerford.akka.seqbehavior.implementations.{FsmSeqBehavior, StatefulPartialFsmSeqBehaviorPhase}
import org.hungerford.akka.seqbehavior.{Return, SeqBehavior, StayWith}

import scala.concurrent.duration.FiniteDuration

object SpotManager {

    trait SpotManagerMessage
    case object Poll extends SpotManagerMessage
    case object NeedFleet extends SpotManagerMessage
    case object DontNeedFleet extends SpotManagerMessage

    trait FleetServiceMessage extends SpotManagerMessage
    case object CheckFleets extends FleetServiceMessage
    case object FleetIsLive extends FleetServiceMessage
    case object FleetIsDead extends FleetServiceMessage
    case object StartFleet extends FleetServiceMessage
    case object FleetStarted extends FleetServiceMessage
    case object FleetNotStarted extends FleetServiceMessage
    case object KillFleet extends FleetServiceMessage
    case object FleetKilled extends FleetServiceMessage
    case object FleetNotKilled extends FleetServiceMessage

    trait SpotManagerReturn

    trait PollHandlerReturn extends SpotManagerReturn
    case object GetFleet extends PollHandlerReturn
    case object DontGetFleet extends PollHandlerReturn

    case class PollHandlerState( fleetService : ActorRef[ FleetServiceMessage ], fleetNeeded : Boolean )

    def mainLoop( pollDelay : FiniteDuration ) : StatefulPartialFsmSeqBehaviorPhase[SpotManagerMessage, SpotManagerReturn, PollHandlerState ] = {
        SeqBehavior
          .builder
          .stateful[ SpotManagerMessage, SpotManagerReturn, PollHandlerState ]
          .initializer { ctx =>
              val fleetService = ctx.spawn[ FleetServiceMessage ]( ???, ??? )
              ctx.self ! Poll
              PollHandlerState( fleetService, fleetNeeded = false )
          }
          .cleanup( ( ctx : ActorContext[ SpotManagerMessage ], state : PollHandlerState ) => ctx.stop( state.fleetService ) )
          .onMessage( ctx => {
              case (Poll, phs@PollHandlerState( fsa, _ ) ) =>
                  fsa ! CheckFleets
                  ctx.scheduleOnce( pollDelay, ctx.self, Poll )
                  StayWith( phs )
              case (NeedFleet, phs) =>
                  StayWith( phs )
              case (DontNeedFleet, phs) =>
                  StayWith( phs )
              case (FleetIsLive, PollHandlerState( fsa, true )) =>
                  ctx.stop( fsa )
                  Return( DontGetFleet )
              case (FleetIsDead, PollHandlerState( fsa, true )) =>
                  ctx.stop( fsa )
                  Return( GetFleet )
              case (FleetIsLive,  PollHandlerState( fsa, false )) =>
                  ctx.stop( fsa )
                  Return( DontGetFleet )
              case (FleetIsDead, PollHandlerState( fsa, false )) =>
                  ctx.stop( fsa )
                  Return( DontGetFleet )
          } )
          .buildFsm()
    }

    trait FleetStarterReturn extends SpotManagerReturn

    case class FleetStarterState( fleetService : ActorRef[ FleetServiceMessage ] )

    def fleetStarter : StatefulPartialFsmSeqBehaviorPhase[ SpotManagerMessage, Unit, FleetStarterState ] = {
        SeqBehavior
          .builder
          .stateful[ SpotManagerMessage, Unit, FleetStarterState ]
          .initializer { ctx =>
              val fleetService = ctx.spawn[ FleetServiceMessage ]( ???, ??? )
              fleetService ! StartFleet
              FleetStarterState( fleetService )
          }
          .cleanup( ( ctx : ActorContext[ SpotManagerMessage ], state : FleetStarterState ) => ctx.stop( state.fleetService ) )
          .onMessage( _ => {
              case (FleetStarted, fss@FleetStarterState( fsa ) ) =>
                  fsa ! CheckFleets
                  StayWith( fss )
              case (FleetNotStarted, fss@FleetStarterState( fsa ) ) =>
                  fsa ! StartFleet
                  StayWith( fss )
              case (FleetIsLive, _ ) =>
                  Return()
              case (FleetIsDead, fss@FleetStarterState( fsa ) ) =>
                  fsa ! StartFleet
                  StayWith( fss )
          } )
          .buildFsm()
    }

    case class FleetKillerState( fleetService : ActorRef[ FleetServiceMessage ] )

    def fleetKiller : StatefulPartialFsmSeqBehaviorPhase[SpotManagerMessage, Unit, FleetKillerState ] = {
        SeqBehavior
          .builder
          .stateful[ SpotManagerMessage, Unit, FleetKillerState ]
          .initializer { ctx =>
              val fleetService = ctx.spawn[ FleetServiceMessage ]( ???, ??? )
              fleetService ! KillFleet
              FleetKillerState( fleetService )
          }
          .cleanup( ( ctx : ActorContext[ SpotManagerMessage ], state : FleetKillerState ) => ctx.stop( state.fleetService ) )
          .onMessage( _ => {
              case (FleetKilled, fks@FleetKillerState( fsa )) =>
                  fsa ! CheckFleets
                  StayWith( fks )
              case (FleetNotKilled, fks@FleetKillerState( fsa )) =>
                  fsa ! KillFleet
                  StayWith( fks )
              case (FleetIsLive, fks@FleetKillerState( fsa )) =>
                  fsa ! KillFleet
                  StayWith( fks )
              case (FleetIsDead, _ ) =>
                  Return()
          } )
          .buildFsm()
    }
    
    def spotManagerBehavior( pollDelay : FiniteDuration ) : FsmSeqBehavior[ SpotManagerMessage, Unit ] = for {
        result <- mainLoop( pollDelay )
        _ <- result match {
            case GetFleet => fleetStarter
            case DontGetFleet => fleetKiller
        }
        _ <- spotManagerBehavior( pollDelay )
    } yield ()

}
