import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForEach
import domain.Domain._
import doobie.Transactor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.{a, convertToAnyMustWrapper}
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.testcontainers.utility.DockerImageName
import repository.{GameSQL, GameStorage}

import java.sql.DriverManager
import java.time.Instant

class Tests extends AnyFlatSpec with TestContainerForEach {

  override val containerDef: PostgreSQLContainer.Def = PostgreSQLContainer.Def(
    dockerImageName = DockerImageName.parse("postgres:16.0"),
    databaseName = "games",
    username = "docker",
    password = "docker"
  )

  private def loadData(container: Containers): Unit = {
    val init = "sql/init.sql"
    val connection = DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
    val statement = connection.createStatement()

    val source = scala.io.Source.fromFile(init)
    val initScript =
      try source.mkString
      finally source.close()

    try statement.execute(initScript)
    finally connection.close()
  }

  def createGame(gameId: Long, homeTeam: Team, awayTeam: Team, mili: Long): Game =
    Game(GameId(gameId), homeTeam, awayTeam, GameDate(Instant.ofEpochMilli(mili)))

  private val t1 = Team(TeamId(1), TeamName("NJD"))
  private val t2 = Team(TeamId(2), TeamName("NYI"))
  private val t3 = Team(TeamId(3), TeamName("NYR"))

  private val i1 = Instant.ofEpochMilli(1000)
  private val i2 = Instant.ofEpochMilli(2000)
  private val i3 = Instant.ofEpochMilli(3000)
  private val g1 = Game(GameId(10), t1, t2, GameDate(i1))
  private val g2 = Game(GameId(11), t1, t3, GameDate(i2))
  private val g3 = Game(GameId(12), t3, t2, GameDate(i3))

  private val series = GameSeries(List(g1, g2, g3))

  override def afterContainersStart(containers: PostgreSQLContainer): Unit = {
    loadData(containers)
  }

  def test(testStorage: GameStorage => Unit): Unit = {
    withContainers { pgContainer =>
      Class.forName(pgContainer.driverClassName)

      val sql = GameSQL.make
      val transactor = Transactor.fromDriverManager[IO](
        pgContainer.driverClassName,
        pgContainer.jdbcUrl,
        pgContainer.username,
        pgContainer.password
      )

      val storage = GameStorage.make(sql, transactor)
      storage.saveGameDay(series).unsafeRunSync() mustBe a[Right[_, _]]
      testStorage(storage)
    }
  }

  "saveGameDay" should "do nothing if game is already added" in {
    test { storage =>
      val newSeries = GameSeries(List(g2))
      storage.saveGameDay(newSeries).unsafeRunSync() mustBe a[Right[_, _]]
      series.list.map { game =>
        storage.getGame(gameId = game.gameId).unsafeRunSync() shouldEqual Right(game)
      }
    }
  }

  "saveGameDay" should "successfully save new games" in {
    test { storage =>
      val newGame = createGame(15, t2, t2, 400)
      val newSeries = GameSeries(List(newGame))

      storage.markGame(UserId(10), newGame.gameId).unsafeRunSync() mustBe a[Left[_, _]]
      storage.saveGameDay(newSeries).unsafeRunSync() mustBe a[Right[_, _]]
      storage.markGame(UserId(10), newGame.gameId).unsafeRunSync() mustBe a[Right[_, _]]
    }
  }

  "getGame" should "return game by id" in {
    test { storage =>
      val newGame = createGame(100, t2, t2, 400)
      val newSeries = GameSeries(List(newGame))

      storage.getGame(g1.gameId).unsafeRunSync() shouldEqual Right(g1)
      storage.getGame(GameId(100)).unsafeRunSync() mustBe a[Left[_, _]]

      storage.saveGameDay(newSeries).unsafeRunSync() mustBe a[Right[_, _]]
      storage.getGame(newGame.gameId).unsafeRunSync() shouldEqual Right(newGame)
    }
  }

  "getGamesForUser" should "return list of stared games for user" in {
    test { storage =>
      val userId = UserId(10)
      storage.getMarkedGamesForUser(userId).unsafeRunSync() shouldEqual Right(List())

      storage.markGame(userId, g1.gameId).unsafeRunSync() shouldEqual Right(())
      storage.markGame(userId, g2.gameId).unsafeRunSync() shouldEqual Right(())

      storage.getMarkedGamesForUser(userId).unsafeRunSync().map(_.toSet) shouldEqual Right(Set(g1, g2))
    }
  }

  "getGamesForUser" should "return only correctly inserted games" in {
    test { storage =>
      val userId = UserId(10)

      storage.getMarkedGamesForUser(userId).unsafeRunSync() shouldEqual Right(List())

      storage.markGame(userId, GameId(100)).unsafeRunSync() mustBe a[Left[_, _]]

      storage.getMarkedGamesForUser(userId).unsafeRunSync() shouldEqual Right(List())
    }
  }

  "saveGame" should "return GameAlreadyExists if game already exists" in {
    test { storage =>
      val userId = UserId(10)

      storage.markGame(userId, g1.gameId).unsafeRunSync() shouldEqual Right(())
      storage.markGame(userId, GameId(1000)).unsafeRunSync() mustBe a[Left[_, _]]
    }
  }

  "unmarkGame" should "remove game from stared if it exists" in {
    test { storage =>
      val userId = UserId(10)
      storage.markGame(userId, g1.gameId).unsafeRunSync() shouldEqual Right(())

      storage.getMarkedGamesForUser(userId).unsafeRunSync() shouldEqual Right(List(g1))
      storage.unmarkGame(userId, g1.gameId).unsafeRunSync() shouldEqual Right(())
      storage.getMarkedGamesForUser(userId).unsafeRunSync() shouldEqual Right(List())
    }
  }

  "unmarkGame" should "do nothing and return error if game is not stared" in {
    test { storage =>
      val userId = UserId(10)
      storage.getMarkedGamesForUser(userId).unsafeRunSync() shouldEqual Right(List())
      storage.unmarkGame(userId, GameId(100)).unsafeRunSync() mustBe a[Left[_, _]]
    }
  }

}
