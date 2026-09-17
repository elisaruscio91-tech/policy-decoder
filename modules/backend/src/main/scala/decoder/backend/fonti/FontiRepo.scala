package decoder.backend.fonti

import org.postgresql.util.PGobject
import zio.*

import java.sql.Types
import java.util.UUID
import javax.sql.DataSource

final case class FonteChunkTrovato(documentoTitolo: String, testo: String, distanza: Double)

// Accesso diretto in JDBC (non Quill) a fonte_documento/fonte_chunk: pgvector
// introduce il tipo colonna `vector` e l'operatore di distanza `<->`, che
// Quill non supporta nativamente. Isolare qui questa eccezione tiene
// Repo.scala (e il resto della codebase) libero da questo dettaglio.
final class FontiRepo(ds: DataSource):

  private def vettoreALiteral(v: Vector[Float]): String = v.mkString("[", ",", "]")

  def inserisciDocumento(titolo: String, origine: Option[String]): Task[UUID] =
    ZIO.attemptBlocking {
      val id   = UUID.randomUUID()
      val conn = ds.getConnection
      try
        val ps = conn.prepareStatement("INSERT INTO fonte_documento (id, titolo, origine) VALUES (?, ?, ?)")
        ps.setObject(1, id)
        ps.setString(2, titolo)
        origine.fold(ps.setNull(3, Types.VARCHAR))(ps.setString(3, _))
        ps.executeUpdate()
        id
      finally conn.close()
    }

  def inserisciChunk(documentoId: UUID, ordine: Int, testo: String, embedding: Vector[Float]): Task[Unit] =
    ZIO.attemptBlocking {
      val conn = ds.getConnection
      try
        val ps = conn.prepareStatement(
          "INSERT INTO fonte_chunk (id, documento_id, ordine, testo, embedding) VALUES (?, ?, ?, ?, ?)",
        )
        ps.setObject(1, UUID.randomUUID())
        ps.setObject(2, documentoId)
        ps.setInt(3, ordine)
        ps.setString(4, testo)
        val vec = new PGobject()
        vec.setType("vector")
        vec.setValue(vettoreALiteral(embedding))
        ps.setObject(5, vec)
        ps.executeUpdate()
        ()
      finally conn.close()
    }

  // `distanzaMassima` filtra lato applicazione (non in SQL) i risultati
  // troppo lontani: con pochi documenti indicizzati LIMIT da solo
  // ritornerebbe comunque i "meno peggio" anche se nessuno è pertinente.
  def cercaSimili(embedding: Vector[Float], limite: Int, distanzaMassima: Double): Task[List[FonteChunkTrovato]] =
    ZIO.attemptBlocking {
      val conn = ds.getConnection
      try
        val vec = new PGobject()
        vec.setType("vector")
        vec.setValue(vettoreALiteral(embedding))
        val ps = conn.prepareStatement(
          """SELECT d.titolo AS titolo, c.testo AS testo, (c.embedding <-> ?) AS distanza
            |FROM fonte_chunk c JOIN fonte_documento d ON d.id = c.documento_id
            |ORDER BY c.embedding <-> ? LIMIT ?""".stripMargin,
        )
        ps.setObject(1, vec)
        ps.setObject(2, vec)
        ps.setInt(3, limite)
        val rs  = ps.executeQuery()
        val buf = scala.collection.mutable.ListBuffer.empty[FonteChunkTrovato]
        while rs.next() do
          val distanza = rs.getDouble("distanza")
          if distanza <= distanzaMassima then buf += FonteChunkTrovato(rs.getString("titolo"), rs.getString("testo"), distanza)
        buf.toList
      finally conn.close()
    }

object FontiRepo:
  val layer: URLayer[DataSource, FontiRepo] = ZLayer.fromFunction(FontiRepo(_))
