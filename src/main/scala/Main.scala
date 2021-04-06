import akka.actor.typed.ActorSystem
import org.hungerford.akka.dininghakkers.DiningHakkers.{Exit, NewHakkers, Start, Stop}
import org.hungerford.akka.dininghakkers.{DiningHakkersBP, DiningHakkersFsm, DiningHakkersNaive, DiningHakkersSubActors}

import scala.util.matching.Regex

object Main {
    def main( args : Array[ String ] ) : Unit = {

        println( "\nWhich implementation do you choose?\n" )
        var initCmd : String = ""
        do {
            initCmd = scala.io.StdIn.readLine().trim.toLowerCase
        } while ( !NaiveCommand.matches( initCmd )
                  && !FsmCommand.matches( initCmd )
                  && !BehaviorPhaseCommand.matches( initCmd )
                  && !SubActorsCommand.matches( initCmd ) )
        val actorSystem = initCmd match {
            case BehaviorPhaseCommand() => ActorSystem( DiningHakkersBP(), "DiningHakkers" )
            case FsmCommand() => ActorSystem( DiningHakkersFsm(), "DiningHakkers" )
            case NaiveCommand() => ActorSystem( DiningHakkersNaive(), "DiningHakkers" )
            case SubActorsCommand() => ActorSystem( DiningHakkersSubActors(), "DiningHakkers" )
        }

        println( "\nEnter your command\n" )
        var repeat : Boolean = true
        do {
            var cmd = scala.io.StdIn.readLine().trim.toLowerCase
            repeat = cmd match {
                case StartCommand() => actorSystem ! Start; true
                case StartWithCommand( newHakkerNames ) =>
                    actorSystem ! NewHakkers( newHakkerNames.split( """\s+""" ).map( _.trim ) )
                    actorSystem ! Start
                    true
                case StopCommand() => actorSystem ! Stop; true
                case ExitCommand() => actorSystem ! Exit; false
                case _ => println( s"Unknown command: ${cmd}" ); true
            }
        } while ( repeat )
        actorSystem.terminate()

    }

    val NaiveCommand : Regex = """naive|na""".r
    val FsmCommand : Regex = """fsm|finitestate|finitestatemachine""".r
    val BehaviorPhaseCommand : Regex = """bp|behaviorphase|beph""".r
    val SubActorsCommand : Regex = """sub|subactors|subact""".r

    val StartWithCommand : Regex = """start:\s*([a-z0-9\s]*)""".r
    val StartCommand : Regex = "start".r
    val StopCommand : Regex = "stop".r
    val ExitCommand : Regex = "exit".r
}
