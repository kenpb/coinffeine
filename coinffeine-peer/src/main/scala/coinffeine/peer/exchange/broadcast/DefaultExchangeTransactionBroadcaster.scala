package coinffeine.peer.exchange.broadcast

import akka.actor._
import akka.persistence.{RecoveryCompleted, PersistentActor}

import coinffeine.common.akka.persistence.PersistentEvent
import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BitcoinPeerActor._
import coinffeine.peer.bitcoin.blockchain.BlockchainActor
import coinffeine.peer.exchange.broadcast.ExchangeTransactionBroadcaster._
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.LastBroadcastableOffer

private class DefaultExchangeTransactionBroadcaster(
     refund: ImmutableTransaction,
     collaborators: DefaultExchangeTransactionBroadcaster.Collaborators,
     constants: ProtocolConstants) extends PersistentActor with ActorLogging {
  import DefaultExchangeTransactionBroadcaster._

  override val persistenceId = "broadcast-with-refund-" + refund.get.getHashAsString
  private val policy = new BroadcastPolicy(refund, constants.refundSafetyBlockCount)

  override def preStart(): Unit = {
    watchRelevantBlocks()
    super.preStart()
  }

  private def watchRelevantBlocks(): Unit = {
    for (blockHeight <- policy.relevantBlocks) {
      collaborators.blockchain ! BlockchainActor.WatchBlockchainHeight(blockHeight)
    }
  }

  override val receiveRecover: Receive = {
    case event: OfferAdded => onOfferAdded(event)
    case PublicationRequested => onPublicationRequested()
    case event: FinishedWithResult => onFinished(event)
    case RecoveryCompleted =>
      collaborators.blockchain ! BlockchainActor.RetrieveBlockchainHeight
  }

  override val receiveCommand: Receive =  {
    case LastBroadcastableOffer(tx) =>
      persist(OfferAdded(tx))(onOfferAdded)

    case PublishBestTransaction =>
      persist(PublicationRequested) { _ =>
        onPublicationRequested()
        broadcastIfNeeded()
      }

    case BlockchainActor.BlockchainHeightReached(height) =>
      policy.updateHeight(height)
      broadcastIfNeeded()

    case msg @ TransactionPublished(tx, _) if tx == policy.bestTransaction =>
      finishWith(SuccessfulBroadcast(msg))

    case TransactionPublished(_, unexpectedTx) =>
      finishWith(FailedBroadcast(UnexpectedTxBroadcast(unexpectedTx)))

    case TransactionNotPublished(_, err) =>
      finishWith(FailedBroadcast(err))
  }

  private def broadcastIfNeeded(): Unit = {
    if (policy.shouldBroadcast) {
      collaborators.bitcoinPeer ! PublishTransaction(policy.bestTransaction)
    }
  }

  private def finishWith(result: BroadcastResult): Unit = {
    persist(FinishedWithResult(result))(onFinished)
  }

  private def onOfferAdded(event: OfferAdded): Unit = {
    policy.addOfferTransaction(event.offer)
  }

  private def onPublicationRequested(): Unit = {
    policy.requestPublication()
  }

  private def onFinished(event: FinishedWithResult): Unit = {
    collaborators.listener ! event.result
    self ! PoisonPill
  }
}

object DefaultExchangeTransactionBroadcaster {

  case class Collaborators(bitcoinPeer: ActorRef, blockchain: ActorRef, listener: ActorRef)

  def props(refund: ImmutableTransaction, collaborators: Collaborators, constants: ProtocolConstants) =
    Props(new DefaultExchangeTransactionBroadcaster(refund, collaborators, constants))

  private case class OfferAdded(offer: ImmutableTransaction) extends PersistentEvent
  private case object PublicationRequested extends PersistentEvent
  private case class FinishedWithResult(result: BroadcastResult) extends PersistentEvent
}
