import akka.actor.typed.ActorSystem
import org.hungerford.akka.dininghakkers.{DiningHakkersBasic, DiningHakkersFsm, DiningHakkersSB}
import org.hungerford.akka.dininghakkers.DiningHakkers.{DiningHakkersMessage, Exit, NewHakkers, Start, Stop}
import org.hungerford.fp.impure.{FpIO, FpImpure}

import scala.util.matching.Regex

object Main {
    def main( args : Array[ String ] ) : Unit = {

        def program : FpImpure[ Unit ] = for {
            _ <- FpIO.fpPrintLine( "\nWhich implementation do you choose?\n" )
            initCmd <- FpIO.fpReadLine
              .map( _.toString().trim.toLowerCase )
              .doWhile( cmd => !BasicCommand.matches( cmd ) && !FsmCommand.matches( cmd ) && !SeqBehaviorCommand.matches( cmd ) )
            actorSystem <- initCmd match {
                case SeqBehaviorCommand() => FpImpure( ActorSystem( DiningHakkersSB(), "DiningHakkers" ) )
                case FsmCommand() => FpImpure( ActorSystem( DiningHakkersFsm(), "DiningHakkers" ) )
                case BasicCommand() => FpImpure( ActorSystem( DiningHakkersBasic(), "DiningHakkers" ) )
            }
            _ <- FpIO.fpPrintLine( "\nEnter your command\n" )
            _ <- ( for {
                command <- FpIO.fpReadLine.map( _.toString().trim.toLowerCase )
                repeat <- command match {
                    case StartCommand() => FpImpure( actorSystem ! Start ).map( _ => true )
                    case StartWithCommand( newHakkerNames ) => for {
                        _ <- FpImpure( actorSystem ! NewHakkers( newHakkerNames.split( """\s+""" ).map( _.trim ) ) )
                        _ <- FpImpure( actorSystem ! Start )
                    } yield true
                    case StopCommand() => FpImpure( actorSystem ! Stop ).map( _ => true )
                    case ExitCommand() => FpImpure( actorSystem ! Exit ).map( _ => false )
                    case _ => FpIO.fpPrintLine( s"Unknown command: ${command}" ).map( _ => true )
                }
            } yield repeat ).doWhile( res => res )
            _ <- FpImpure( actorSystem.terminate() )
        } yield ()

        program.run()
    }

    val BasicCommand : Regex = """basic|bas|b""".r
    val FsmCommand : Regex = """fsm""".r
    val SeqBehaviorCommand : Regex = """sb|seqbehavior|seqbehav|seqbehave""".r

    val StartWithCommand : Regex = """start:\s*([a-z0-9\s]*)""".r
    val StartCommand : Regex = "start".r
    val StopCommand : Regex = "stop".r
    val ExitCommand : Regex = "exit".r
}
