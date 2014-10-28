package coinffeine.model.exchange

import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{KeyPair, PublicKey}
import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange.{DepositAmounts, Progress}
import coinffeine.model.network.PeerId

trait SampleExchange extends CoinffeineUnitTestNetwork.Component {

  val participants = Both(
    buyer = Exchange.PeerInfo(
      paymentProcessorAccount = "buyer",
      bitcoinKey = new PublicKey()
    ),
    seller = Exchange.PeerInfo(
      paymentProcessorAccount = "seller",
      bitcoinKey = new KeyPair()
    )
  )

  val requiredSignatures = participants.map(_.bitcoinKey)

  val peerIds = Both(buyer = PeerId.hashOf("buyer"), seller = PeerId.hashOf("seller"))

  val amounts = Exchange.Amounts(
    grossBitcoinExchanged = 10.006.BTC,
    grossFiatExchanged = 10.5.EUR,
    deposits = Both(
      buyer = DepositAmounts(input = 2.002.BTC, output = 2.BTC),
      seller = DepositAmounts(input = 11.006.BTC, output = 11.004.BTC)
    ),
    refunds = Both(buyer = 1.BTC, seller = 10.BTC),
    intermediateSteps = Seq.tabulate(10) { index =>
      val step = index + 1
      Exchange.IntermediateStepAmounts(
        depositSplit = Both(buyer = 1.BTC * step + 0.002.BTC, seller = 10.BTC - 1.BTC * step),
        fiatAmount = 1.EUR,
        fiatFee = 0.05.EUR,
        progress = Progress(Both(buyer = 1.BTC * step, seller = 1.BTC * step + 0.006.BTC))
      )
    },
    finalStep = Exchange.FinalStepAmounts(
      depositSplit = Both(buyer = 12.002.BTC, seller = 1.BTC),
      progress = Progress(Both(buyer = 10.BTC, seller = 10.006.BTC))
    )
  )

  val exchangeId = ExchangeId("id")
  val parameters = Exchange.Parameters(lockTime = 25, network)

  val buyerExchange =
    Exchange.notStarted(exchangeId, BuyerRole, peerIds.seller, amounts, parameters)
  val buyerHandshakingExchange =
    buyerExchange.startHandshaking(user = participants.buyer, counterpart = participants.seller)

  val sellerExchange =
    Exchange.notStarted(exchangeId, SellerRole, peerIds.buyer, amounts, parameters)
  val sellerHandshakingExchange =
    sellerExchange.startHandshaking(user = participants.seller, counterpart = participants.buyer)
}

object SampleExchange {
  val IntermediateSteps = 10
}
