/**
 * Copyright (C) 2009-2011 Scalable Solutions AB <http://scalablesolutions.se>
 */

package akka.actor

import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers

import akka.testkit._

import FSM._
import akka.util.Duration
import akka.util.duration._
import akka.event._

object FSMActorSpec {

  val unlockedLatch = TestLatch()
  val lockedLatch = TestLatch()
  val unhandledLatch = TestLatch()
  val terminatedLatch = TestLatch()
  val transitionLatch = TestLatch()
  val initialStateLatch = TestLatch()
  val transitionCallBackLatch = TestLatch()

  sealed trait LockState
  case object Locked extends LockState
  case object Open extends LockState

  class Lock(code: String, timeout: Duration) extends Actor with FSM[LockState, CodeState] {

    startWith(Locked, CodeState("", code))
    
    when(Locked) {
      case Event(digit: Char, CodeState(soFar, code)) => {
        soFar + digit match {
          case incomplete if incomplete.length < code.length =>
            stay using CodeState(incomplete, code)
          case codeTry if (codeTry == code) => {
            doUnlock
            goto(Open) using CodeState("", code) forMax timeout
          }
          case wrong => {
            stay using CodeState("", code)
          }
        }
      }
      case Event("hello", _) => stay replying "world"
      case Event("bye", _) => stop(Shutdown)
    }

    when(Open) {
      case Event(StateTimeout, _) => {
        doLock
        goto(Locked)
      }
    }

    whenUnhandled {
      case Ev(msg) => {
        unhandledLatch.open
        EventHandler.info(this, "unhandled event "+msg+" in state "+stateName+" with data "+stateData)
        stay
      }
    }

    onTransition {
      case Locked -> Open => transitionLatch.open
    }

    // verify that old-style does still compile
    onTransition (transitionHandler _)

    def transitionHandler(from: LockState, to: LockState) = {
      // dummy
    }

    onTermination {
      case StopEvent(Shutdown, Locked, _) =>
        // stop is called from lockstate with shutdown as reason...
        terminatedLatch.open
    }

    // initialize the lock
    initialize

    private def doLock() {
      lockedLatch.open
    }

    private def doUnlock = {
      unlockedLatch.open
    }
  }

  case class CodeState(soFar: String, code: String)
}

class FSMActorSpec extends WordSpec with MustMatchers with TestKit {
  import FSMActorSpec._

  "An FSM Actor" must {

    "unlock the lock" in {
      
      // lock that locked after being open for 1 sec
      val lock = Actor.actorOf(new Lock("33221", 1 second)).start()

      val transitionTester = Actor.actorOf(new Actor { def receive = {
        case Transition(_, _, _) => transitionCallBackLatch.open
        case CurrentState(_, Locked) => initialStateLatch.open
      }}).start()

      lock ! SubscribeTransitionCallBack(transitionTester)
      initialStateLatch.await

      lock ! '3'
      lock ! '3'
      lock ! '2'
      lock ! '2'
      lock ! '1'

      unlockedLatch.await
      transitionLatch.await
      transitionCallBackLatch.await
      lockedLatch.await

      lock ! "not_handled"
      unhandledLatch.await

      val answerLatch = TestLatch()
      object Hello
      object Bye
      val tester = Actor.actorOf(new Actor {
        protected def receive = {
          case Hello => lock ! "hello"
          case "world" => answerLatch.open
          case Bye => lock ! "bye"
        }
      }).start()
      tester ! Hello
      answerLatch.await

      tester ! Bye
      terminatedLatch.await
    }

    "log termination" in {
      val fsm = Actor.actorOf(new Actor with FSM[Int, Null] {
          startWith(1, null)
          when(1) {
            case Ev("go") => goto (2)
          }
        }).start()
      val logger = Actor.actorOf(new Actor {
          def receive = {
            case x => testActor forward x
          }
        })
      EventHandler.addListener(logger)
      fsm ! "go"
      expectMsgPF() {
        case EventHandler.Error(_ : EventHandler.EventHandlerException, `fsm`, "Next state 2 does not exist") => true
      }
      EventHandler.removeListener(logger)
    }

    "run onTermination upon ActorRef.stop()" in {
      lazy val fsm = new Actor with FSM[Int, Null] {
          startWith(1, null)
          when(1) { NullFunction }
          onTermination {
            case x => testActor ! x
          }
        }
      val ref = Actor.actorOf(fsm).start()
      ref.stop()
      expectMsg(fsm.StopEvent(Shutdown, 1, null))
    }

  }
}
