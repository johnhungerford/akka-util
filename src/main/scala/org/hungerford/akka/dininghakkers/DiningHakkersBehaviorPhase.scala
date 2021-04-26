package org.hungerford.akka.dininghakkers

import akka.actor.typed.{ActorRef, Behavior}
import org.hungerford.akka.behaviorphase.{BehaviorPhase, Do, Return, Skip, Stay}

import scala.concurrent.duration._

object ChopstickBP {
    import Chopstick._

    def apply() : Behavior[ ChopstickMessage ] = {
        chopstickBehavior.setup
    }

    // Chopsticks switch back and forth between waiting to be picked up
    // and waiting to be put down
    def chopstickBehavior : BehaviorPhase[ ChopstickMessage, Unit ] = ( for {
        hakker <- getPickedUp
        _ <- getPutDownBy( hakker )
    } yield () ).loop

    //When a Chopstick is available, it can be taken by a hakker
    private def getPickedUp : BehaviorPhase[ChopstickMessage, ActorRef[ ChopstickAnswer ] ] = {
        BehaviorPhase
          .fromHandler[ ChopstickMessage, ActorRef[ ChopstickAnswer ] ]( ctx => {
              case Take( hakker ) =>
                  hakker ! Taken( ctx.self )
                  Return( hakker )
              case _ => Stay
          } )
    }

    // When a Chopstick is taken by a hakker
    // It will refuse to be taken by other hakkers
    // But the owning hakker can put it back
    private def getPutDownBy(
        hakker: ActorRef[ChopstickAnswer]
    ) : BehaviorPhase[ChopstickMessage, Unit ] = {
        BehaviorPhase
          .fromHandler[ ChopstickMessage, Unit ]( ctx => {
              case Take( otherHakker ) =>
                  otherHakker ! Busy( ctx.self )
                  Stay
              case Put( `hakker` ) =>
                  Return()
              case _ =>
                  Stay
          } )
    }

}

/*
 * A hakker is an awesome dude or dudette who either thinks about hacking or has to eat ;-)
 */
object HakkerBP {
    import Chopstick._
    import Hakker._

    def apply(name: String, left: ActorRef[ ChopstickMessage ], right: ActorRef[ ChopstickMessage ] ): Behavior[ Command ] = {
        new HakkerBP( name, left, right ).hakkerBehavior.setup
    }
}

class HakkerBP( name: String,
              left: ActorRef[ Chopstick.ChopstickMessage ],
              right: ActorRef[ Chopstick.ChopstickMessage ] ) {

    import Chopstick._
    import Hakker._

    // Complex behavior of a hakker, composed from component behaviors
    lazy val hakkerBehavior : BehaviorPhase[ Command, Unit ] = for {
        _ <- waitForDinner
        _ <- {
            val shouldThinkFirst = rndBool()
            if ( shouldThinkFirst ) thinkFor( 4.seconds, plusOrMinus = 3.seconds )
            else Skip[ Command, Unit ]()
        }
        _ <- ( for {
            res <- getChopsticks
            _ <- {
                if ( res ) eatFor( 7.seconds, plusOrMinus = 6.seconds )
                else Skip[ Command, Unit ]()
            }
            _ <- thinkFor( 5.seconds, plusOrMinus = 4.seconds )
        } yield res )
          .foldWhile( 0 )( ( i, res ) => if ( res ) 0 else i + 1 )( ( _, i : Int ) => i < 3 )
          .onComplete( ( ctx, numTimes ) => ctx.log.info( s"$name curses its fate after failing to pick up chopsticks $numTimes times in a row!!!" ) )
          .loop
    } yield ()

    private lazy val waitForDinner : BehaviorPhase[ Command, Unit ] = BehaviorPhase.fromHandler[ Command, Unit ]( _ => {
        case Eat =>
            Return()
    } )

    private def thinkFor( duration : FiniteDuration, plusOrMinus : FiniteDuration ) : BehaviorPhase[ Command, Unit ] = {
        for {
            _ <- Do[ Command, Unit ]( ctx => {
                val dur = rndDuration( duration, plusOrMinus )
                ctx.log.info( "{} starts to think for {}", name, dur.toString )
                ctx.scheduleOnce( dur, ctx.self, Eat )
            } )
            _ <- BehaviorPhase.fromHandler[ Command, Unit ]( ctx => {
                case Eat => Return()
                case _ => Stay
            } )
        } yield ()
    }

    private lazy val getChopsticks = for {
        _ <- Do[ Command, Unit ]( ctx => {
            val adapter = ctx.messageAdapter(HandleChopstickAnswer)
            ctx.log.info( "{} reaches for his chopsticks", name )
            left ! Take( adapter )
            right ! Take( adapter )
        } )
        firstResult <- waitForFirstChopstick
        secondResult <- firstResult match {
            case Left( cs ) => waitForSecondChopstick( cs, firstSucceeded = false )
            case Right( cs ) => waitForSecondChopstick( cs, firstSucceeded = true )
        }
    } yield secondResult


    private lazy val waitForFirstChopstick = {
        BehaviorPhase.fromHandler[ Command, Either[ ActorRef[ ChopstickMessage ], ActorRef[ ChopstickMessage ] ] ]( ctx => {
              case HandleChopstickAnswer( Taken( cs ) ) if cs == left || cs == right =>
                  ctx.log.info( "{} picked up {}", name, cs.path.name )
                  Return( Right( cs ) )
              case HandleChopstickAnswer( Busy( cs ) ) if cs == left || cs == right =>
                  ctx.log.info( "{} was unable to pick up {}", name, cs.path.name )
                  Return( Left( cs ) )
              case _ => Stay
          } )
    }

    private def waitForSecondChopstick(
      first : ActorRef[ ChopstickMessage ],
      firstSucceeded : Boolean
    ) : BehaviorPhase[Command, Boolean ] = {
        BehaviorPhase.fromHandler[ Command, Boolean ]( ctx => {
              case HandleChopstickAnswer( Taken( cs ) ) if ( cs == left || cs == right ) && cs != first =>
                  if ( firstSucceeded ) {
                      ctx.log.info( "{} also picked up {}", name, cs.path.name )
                      Return( true )
                  }
                  else {
                      val adapter = ctx.messageAdapter(HandleChopstickAnswer)
                      cs ! Put( adapter )
                      ctx.log.info( "{} picked up {} but put it back down because he was unable to pick up {}", name, cs.path.name, first.path.name )
                      Return( false )
                  }

              case HandleChopstickAnswer( Busy( cs ) ) if ( cs == left || cs == right ) && cs != first =>
                  if ( firstSucceeded ) {
                      first ! Put( ctx.messageAdapter( HandleChopstickAnswer ) )
                      ctx.log.info( "{} was unable to pick up {} so he put down {}", name, cs.path.name, first.path.name )
                  } else ctx.log.info( "{} was also unable to pick up {}", name, cs.path.name )
                  Return( false )

              case _ => Stay
          } )
    }

    private def eatFor( duration: FiniteDuration, plusOrMinus : FiniteDuration ) : BehaviorPhase[ Command, Unit ] = {
        for {
            _ <- Do[ Command, Unit ]( ctx => {
                val dur = rndDuration( duration, plusOrMinus )
                ctx.log.info( "{} starts to eat for {}", name, dur.toString )
                ctx.scheduleOnce( dur, ctx.self, Think )
            } )
            _ <- BehaviorPhase.fromHandler[ Command, Unit ]( ctx => {
                case Think =>
                    val adapter = ctx.messageAdapter(HandleChopstickAnswer)
                    ctx.log.info("{} stops eating and puts down his chopsticks", name)
                    left ! Put(adapter)
                    right ! Put(adapter)
                    Return()

                case _ => Stay
            } )
        } yield () 
    }

}

object DiningHakkersBP extends DiningHakkers {
    override def chopstickBehavior : Behavior[ Chopstick.ChopstickMessage ] = ChopstickFsm()

    override def hakkerBehavior( name : String, left : ActorRef[ Chopstick.ChopstickMessage ],
                                 right : ActorRef[ Chopstick.ChopstickMessage ] ) : Behavior[ Hakker.Command ] = {
        HakkerBP( name, left, right )
    }
}
