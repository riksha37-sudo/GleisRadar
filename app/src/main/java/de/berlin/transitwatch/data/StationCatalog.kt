package de.berlin.transitwatch.data

import de.berlin.transitwatch.core.GeoPoint
import de.berlin.transitwatch.core.LineId
import de.berlin.transitwatch.core.LineSelector
import de.berlin.transitwatch.core.Station
import de.berlin.transitwatch.core.TransitMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject

internal val BerlinZone: ZoneId = ZoneId.of("Europe/Berlin")

data class StationCatalog(
    val stations: List<Station>,
    val sourceSha256: String,
    val importedAt: Instant,
    val validFrom: LocalDate,
    val validThrough: LocalDate,
) {
    fun usableAt(now: Instant): Boolean =
        now >= importedAt && now.atZone(BerlinZone).toLocalDate() in validFrom..validThrough

    /** The point is the city-centre example, independent of the device's location. */
    fun berlinMitteLines(now: Instant): Set<LineId> =
        if (usableAt(now)) LineSelector.select(GeoPoint(52.5200, 13.4050), stations) else emptySet()
}

internal fun readStationCatalog(json: String): StationCatalog {
    val root = JSONObject(json)
    val metadata = root.getJSONObject("metadata")
    require(metadata.getInt("schemaVersion") == 1)
    require(metadata.getString("region") == "vbb")
    require(metadata.getString("indexPolicy") == "service-window-union")
    require(metadata.getString("license") == "CC-BY-4.0")
    require(metadata.getString("sourceUrl") == "https://unternehmen.vbb.de/gtfs")
    val sourceHash = metadata.getString("sourceSha256")
    require(sourceHash.matches(Regex("[a-f0-9]{64}")))
    val window = metadata.getJSONObject("serviceWindow")
    val from = LocalDate.parse(window.getString("from"))
    val through = LocalDate.parse(window.getString("through"))
    require(from <= through)
    val records = root.getJSONArray("stations")
    require(records.length() in 1..5_000 && records.length() == metadata.getInt("stationCount"))
    val stations = List(records.length()) { i ->
        val record = records.getJSONObject(i)
        val id = record.getString("id").also { require(it.isNotBlank()) }
        val name = record.getString("name").also { require(it.isNotBlank()) }
        val lines = record.getJSONArray("lines")
        require(lines.length() in 1..100)
        Station(id, name, GeoPoint(record.getDouble("lat"), record.getDouble("lon")),
            List(lines.length()) { j ->
                val entry = lines.getJSONObject(j)
                val mode = TransitMode.valueOf(entry.getString("mode"))
                val region = entry.getString("region")
                require(region == "vbb" && mode in setOf(TransitMode.S_BAHN, TransitMode.U_BAHN))
                LineId(region, mode, entry.getString("name"))
            }.toSet())
    }
    require(stations.map { it.id }.toSet().size == stations.size)
    require(stations.flatMap { it.lines }.toSet().size == metadata.getInt("lineCount"))
    return StationCatalog(stations, sourceHash, Instant.parse(metadata.getString("importedAt")), from, through)
}
