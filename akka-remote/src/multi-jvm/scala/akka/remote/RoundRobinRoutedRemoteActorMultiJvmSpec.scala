package akka.remote

import akka.actor.{ Actor, Props }
import akka.remote._
import akka.routing._
import akka.testkit.DefaultTimeout
import akka.dispatch.Await

object RoundRobinRoutedRemoteActorMultiJvmSpec extends AbstractRemoteActorMultiJvmSpec {
  override def NrOfNodes = 4

  class SomeActor extends Actor with Serializable {
    def receive = {
      case "hit" ⇒ sender ! context.system.nodename
      case "end" ⇒ context.stop(self)
    }
  }

  import com.typesafe.config.ConfigFactory
  override def commonConfig = ConfigFactory.parseString("""
    akka {
      loglevel = "WARNING"
      actor {
        provider = "akka.remote.RemoteActorRefProvider"
        deployment {
          /service-hello.router = "round-robin"
          /service-hello.nr-of-instances = %d
          /service-hello.target.nodes = [%s]
        }
      }
    }""" format (3, specString(3)))
}

class RoundRobinRoutedRemoteActorMultiJvmNode1 extends AkkaRemoteSpec(RoundRobinRoutedRemoteActorMultiJvmSpec.nodeConfigs(0)) {
  import RoundRobinRoutedRemoteActorMultiJvmSpec._
  val nodes = NrOfNodes
  "___" must {
    "___" in {
      barrier("start")
      barrier("broadcast-end")
      barrier("end")
      barrier("done")
    }
  }
}

class RoundRobinRoutedRemoteActorMultiJvmNode2 extends AkkaRemoteSpec(RoundRobinRoutedRemoteActorMultiJvmSpec.nodeConfigs(1)) {
  import RoundRobinRoutedRemoteActorMultiJvmSpec._
  val nodes = NrOfNodes
  "___" must {
    "___" in {
      barrier("start")
      barrier("broadcast-end")
      barrier("end")
      barrier("done")
    }
  }
}

class RoundRobinRoutedRemoteActorMultiJvmNode3 extends AkkaRemoteSpec(RoundRobinRoutedRemoteActorMultiJvmSpec.nodeConfigs(2)) {
  import RoundRobinRoutedRemoteActorMultiJvmSpec._
  val nodes = NrOfNodes
  "___" must {
    "___" in {
      barrier("start")
      barrier("broadcast-end")
      barrier("end")
      barrier("done")
    }
  }
}

class RoundRobinRoutedRemoteActorMultiJvmNode4 extends AkkaRemoteSpec(RoundRobinRoutedRemoteActorMultiJvmSpec.nodeConfigs(3)) with DefaultTimeout {
  import RoundRobinRoutedRemoteActorMultiJvmSpec._
  val nodes = NrOfNodes
  "A new remote actor configured with a RoundRobin router" must {
    "be locally instantiated on a remote node and be able to communicate through its RemoteActorRef" in {

      barrier("start")
      val actor = system.actorOf(Props[SomeActor].withRouter(RoundRobinRouter()), "service-hello")
      actor.isInstanceOf[RoutedActorRef] must be(true)

      val connectionCount = NrOfNodes - 1
      val iterationCount = 10

      var replies = Map(
        "node1" -> 0,
        "node2" -> 0,
        "node3" -> 0)

      for (i ← 0 until iterationCount) {
        for (k ← 0 until connectionCount) {
          val nodeName = Await.result(actor ? "hit", timeout.duration).toString
          replies = replies + (nodeName -> (replies(nodeName) + 1))
        }
      }

      barrier("broadcast-end")
      actor ! Broadcast("end")

      barrier("end")
      replies.values foreach { _ must be(10) }

      barrier("done")
    }
  }
}

