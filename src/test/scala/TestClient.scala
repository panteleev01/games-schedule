import cats.effect.IO
import cats.effect.unsafe.implicits.global
import config.HttpClientConfig
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.{a, convertToAnyMustWrapper}
import repository.NHLClient

class TestClient extends AnyFlatSpec {

  def testClient(f: Client[IO] => Unit): Unit = {
    BlazeClientBuilder[IO].resource
      .use { httpClient => IO(f(httpClient)) }
      .unsafeRunSync()
  }

  private val config = HttpClientConfig(
    "https://api-web.nhle.com/v1/schedule"
  )

  "nhlClient" should "successfully parse today's schedule" in {
    testClient { client =>
      val nhl = NHLClient.make(client, config)
      nhl.getTodaysGames.unsafeRunSync() mustBe a[Right[_, _]]
    }
  }

  "nhlClient" should "successfully parse tomorrow's schedule" in {
    testClient { client =>
      val nhl = NHLClient.make(client, config)
      nhl.getTomorrowsGames.unsafeRunSync() mustBe a[Right[_, _]]
    }
  }

}
