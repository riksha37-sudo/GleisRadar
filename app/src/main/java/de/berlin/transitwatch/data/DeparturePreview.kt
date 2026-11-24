package de.berlin.transitwatch.data

import de.berlin.transitwatch.core.TransitMode
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

data class DepartureNotice(
    val id: String,
    val title: String,
    val description: String,
    val lines: Set<String>,
    val modes: Set<TransitMode>,
    val validUntil: Instant?,
    val lastSeen: Instant,
    val observedInLastResponse: Boolean = true,
)

data class DeparturePreview(
    val checkedAt: Instant,
    val railDepartures: Int,
    val notices: List<DepartureNotice>,
)

internal class DeparturePreviewClient {
    fun read(): DeparturePreview {
        val connection = URL(
            "https://v6.vbb.transport.rest/stops/900100003/departures?duration=60&results=50&remarks=true&language=de"
        ).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Linienwache Berlin/0.2 (manual departure preview)")
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            val response = ByteArrayOutputStream()
            connection.inputStream.use { stream ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val count = stream.read(buffer)
                    if (count == -1) break
                    if (response.size() + count > MAX_RESPONSE_BYTES) throw IOException("Response exceeds limit")
                    response.write(buffer, 0, count)
                }
            }
            return readDeparturePreview(response.toByteArray().toString(Charsets.UTF_8), Instant.now())
        } finally {
            connection.disconnect()
        }
    }
}

private const val MAX_RESPONSE_BYTES = 512 * 1024
private const val MAX_NOTICES = 100

internal fun readDeparturePreview(json: String, checkedAt: Instant): DeparturePreview {
    val departures = JSONObject(json).getJSONArray("departures")
    require(departures.length() <= 100)
    val notices = linkedMapOf<String, DepartureNotice>()
    var railDepartures = 0
    for (i in 0 until departures.length()) {
        val departure = departures.getJSONObject(i)
        val line = departure.getJSONObject("line")
        val mode = when (line.getString("product")) {
            "suburban" -> TransitMode.S_BAHN
            "subway" -> TransitMode.U_BAHN
            else -> continue
        }
        railDepartures++
        val lineName = line.getString("name")
        val remarks = departure.optJSONArray("remarks") ?: continue
        for (j in 0 until remarks.length()) {
            val remark = remarks.getJSONObject(j)
            if (remark.optString("type") != "warning") continue
            val id = remark.text("id").also { require(it.isNotEmpty()) }
            val previous = notices[id]
            notices[id] = DepartureNotice(
                id,
                remark.text("summary").ifEmpty { "Hinweis zur Abfahrt" },
                remark.text("text").take(4_000),
                previous?.lines.orEmpty() + lineName,
                previous?.modes.orEmpty() + mode,
                remark.text("validUntil").asInstant(),
                checkedAt,
            )
        }
    }
    return DeparturePreview(checkedAt, railDepartures, notices.values.take(MAX_NOTICES))
}

/** A departure can leave the query window. Preserve context without guessing resolution. */
internal fun DeparturePreview.retainUnconfirmedFrom(previous: DeparturePreview?): DeparturePreview {
    val observed = notices.mapTo(mutableSetOf()) { it.id }
    val unconfirmed = previous?.notices.orEmpty()
        .filterNot { it.id in observed }
        .map { it.copy(observedInLastResponse = false) }
    return copy(notices = (notices + unconfirmed).take(MAX_NOTICES))
}

private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key).trim()

private fun String.asInstant(): Instant? = try {
    takeIf { it.isNotEmpty() }?.let(Instant::parse)
} catch (_: DateTimeParseException) {
    null
}
