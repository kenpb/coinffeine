package coinffeine.model.market

import coinffeine.common.test.UnitTest
import coinffeine.model.currency.Euro
import coinffeine.model.currency.Implicits._
import coinffeine.model.network.PeerId

class PositionQueueTest extends UnitTest {

  val emptyQueue = PositionQueue.empty(Bid, Euro)
  val posA1 = Position(Bid, 1.BTC, Price(100.EUR), PositionId(PeerId("A"), OrderId("A1")))
  val posA2 = Position(Bid, 0.3.BTC, Price(100.EUR), PositionId(PeerId("A"), OrderId("A2")))
  val posB = Position(Bid, 0.2.BTC, Price(100.EUR), PositionId(PeerId("B"), OrderId("B")))
  val queue = emptyQueue.enqueue(posA1).enqueue(posB).enqueue(posA2)

  "A position queue" should "enqueue new positions at the end" in new {
    queue.positions.map(_.id.orderId.value) should be (Seq("A1", "B", "A2"))
  }

  it should "reject already enqueued positions" in {
    val modifiedA1 = posA1.decreaseAmount(0.2.BTC)
    the [IllegalArgumentException] thrownBy {
      queue.enqueue(modifiedA1)
    } should have message s"requirement failed: Position ${posA1.id} already enqueued"
  }

  it should "remove a position by position id" in new {
    queue.removeByPositionId(posB.id) should be(emptyQueue.enqueue(posA1).enqueue(posA2))
    queue.removeByPositionId(PositionId(PeerId("unknown"), OrderId("unknown"))) should be (queue)
  }

  it should "remove positions by peer id" in new {
    queue.removeByPeerId(PeerId("B")) should be(emptyQueue.enqueue(posA1).enqueue(posA2))
    queue.removeByPeerId(PeerId("A")) should be(emptyQueue.enqueue(posB))
    queue.removeByPeerId(PeerId("unknown")) should be (queue)
  }

  it should "decrease amount from position" in {
    queue.decreaseAmount(posA1.id, 0.4.BTC) should be (
      emptyQueue.enqueue(posA1.decreaseAmount(0.4.BTC)).enqueue(posB).enqueue(posA2))
  }

  it should "remove a position by decreasing its whole amount" in {
    queue.decreaseAmount(posA1.id, 1.BTC) should be (emptyQueue.enqueue(posB).enqueue(posA2))
  }

  it should "add up the total amount" in new {
    emptyQueue.sum should be (0.BTC)
    queue.sum should be (1.5.BTC)
  }

  it should "be price-homogeneous" in new {
    an [IllegalArgumentException] shouldBe thrownBy {
      queue.enqueue(Position(Bid, 1.BTC, Price(120.EUR), PositionId(PeerId("A"), OrderId("A3"))))
    }
  }
}
