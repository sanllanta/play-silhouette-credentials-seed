package persistence

import com.typesafe.config.{ Config, ConfigFactory }
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.JdbcBackend.Database
import slick.util.AsyncExecutor

object InMemoryDatabase {
  val database: DatabaseDef =
    Database.forURL(
      url = s"jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;INIT=RUNSCRIPT FROM './conf/users.sql'", driver = "org.h2.Driver",
      executor = AsyncExecutor("PostgresExecutor", numThreads = 20, queueSize = 5000)
    )
}

object LitigandoDB {
  private val config: Config = ConfigFactory.load()
  private val databaseUrl: String = config.getString("db.default.url")
  private val databaseDriver: String = config.getString("db.default.driver")
  private val databaseUser: String = config.getString("db.default.username")
  private val databasePassword: String = config.getString("db.default.password")
  private val db: DatabaseDef = Database.forURL(url = databaseUrl, driver = databaseDriver, user = databaseUser, password = databasePassword)
  def getDatabase(): DatabaseDef = db
}

case class DataBaseDefinitionImpl() {
  def getDataBase(): DatabaseDef = {
    LitigandoDB.getDatabase()
  }
}