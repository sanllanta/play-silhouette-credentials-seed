package persistence

import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.JdbcBackend.Database
import slick.util.AsyncExecutor

trait DataBaseDefinition {
  def getDataBase(): DatabaseDef
}

object InMemoryDatabase {
  def getDatabase(): DatabaseDef =
    Database.forURL(
      url = s"jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;INIT=RUNSCRIPT FROM './conf/users.sql'", driver = "org.h2.Driver",
      executor = AsyncExecutor("PostgresExecutor", numThreads = 20, queueSize = 5000)
    )
}

case class DataBaseDefinitionImpl() extends DataBaseDefinition {
  override def getDataBase(): DatabaseDef = {
    InMemoryDatabase.getDatabase()
  }
}