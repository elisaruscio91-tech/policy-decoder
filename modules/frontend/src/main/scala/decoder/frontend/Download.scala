package decoder.frontend

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

// Salva un array di byte come file scaricabile dal browser (Blob + <a
// download> temporaneo). Usato per il PDF ricevuto da /api/analyze/pdf.
// Costruzione del Blob via js.Dynamic invece della facade tipizzata di
// scalajs-dom: bypassa eventuali disallineamenti tra la firma esatta del
// costruttore Blob nella versione di scalajs-dom in uso e quella reale del
// browser (qui non verificabile a compile-time, nessun sbt locale).
object Download:
  def salvaPdf(bytes: Array[Byte], nomeFile: String): Unit =
    val arr = new Uint8Array(bytes.length)
    var i = 0
    while i < bytes.length do
      arr(i) = (bytes(i) & 0xff).toShort
      i += 1

    val opzioni = js.Dynamic.literal(`type` = "application/pdf")
    val blob    = js.Dynamic.newInstance(js.Dynamic.global.Blob)(js.Array(arr), opzioni)
    val url     = dom.URL.createObjectURL(blob.asInstanceOf[dom.Blob])

    val a = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
    a.href = url
    a.setAttribute("download", nomeFile)
    dom.document.body.appendChild(a)
    a.click()
    dom.document.body.removeChild(a)
    dom.URL.revokeObjectURL(url)
