package com.nimba.amortizationschedule.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Local-filesystem retention of the original uploaded CSV, for audit (fiche métier
 * §10). One file per case and version under a configured root. Local disk is
 * sufficient for this on-premise single-instance deployment; revisit only if a
 * multi-instance topology appears later.
 */
@ConfigurationProperties("storage")
data class StorageProperties(
    val amortizationScheduleDir: String = "storage/credit-cases",
)

@Component
class ScheduleFileStorage(
    private val properties: StorageProperties,
) {
    fun store(
        creditCaseId: UUID,
        version: Int,
        bytes: ByteArray,
    ): String {
        val directory = Path.of(properties.amortizationScheduleDir, creditCaseId.toString(), "amortization-schedules")
        Files.createDirectories(directory)
        val target = directory.resolve("$version.csv")
        Files.write(target, bytes)
        return target.toString()
    }

    /** Removes every retained file of a case (called when the case is deleted). */
    fun deleteAll(creditCaseId: UUID) {
        val root = Path.of(properties.amortizationScheduleDir, creditCaseId.toString())
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
