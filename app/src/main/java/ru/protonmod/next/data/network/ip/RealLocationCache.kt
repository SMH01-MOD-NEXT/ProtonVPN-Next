package ru.protonmod.next.data.network.ip

/**
 * Rules for remembering the address this device is seen at with no tunnel up.
 *
 * The dashboard draws the world map at the user's own country, so it needs an
 * answer before the first request finishes — and on a filtered network that
 * request finishes late or not at all. Keeping the last answer means the map can
 * open where the user actually is, leaving a refresh only to correct it.
 *
 * This object holds what may be kept and when it is worth asking again. The
 * storage is [ru.protonmod.next.data.local.SettingsManager]; the asking is the
 * dashboard's.
 */
object RealLocationCache {

    /** A remembered answer: the address, and the country it belonged to. */
    data class Snapshot(val ip: String, val countryCode: String?)

    /** Addresses only: digits, the hex letters, and their separators. */
    private val ADDRESS = Regex("""^[0-9a-fA-F.:]{3,45}$""")

    private val COUNTRY_CODE = Regex("""^[A-Z]{2}$""")

    /** Codes that mean "somewhere", which is not something worth showing. */
    private val PLACEHOLDER_CODES = setOf("XX", "T1")

    /**
     * Turns a stored or freshly fetched pair into something safe to keep and show.
     *
     * A failed lookup used to be written down as the localized word for unknown,
     * so the shape of the address is what gets checked here rather than any one
     * language's placeholder.
     *
     * @return null when the value is missing or is not an address, in which case
     *   there is nothing worth remembering or drawing.
     */
    fun sanitise(ip: String?, countryCode: String?): Snapshot? {
        val address = IpEchoSources.normaliseAddress(ip.orEmpty().trim())
        if (!ADDRESS.matches(address)) return null
        // Hex digits alone spell words like "abc", so a separator is required.
        if (!address.contains('.') && !address.contains(':')) return null

        val code = countryCode?.trim()?.uppercase()
            ?.takeIf { COUNTRY_CODE.matches(it) && it !in PLACEHOLDER_CODES }
        return Snapshot(address, code)
    }

    /**
     * Whether the real address is worth asking for right now.
     *
     * While a tunnel is up the question can only be answered by sending a request
     * around it, which is the opposite of what the user turned the tunnel on for.
     * So it is asked then only when nothing is known yet: the map needs somewhere
     * to open, and the leak check needs something to compare against.
     */
    fun shouldRefresh(tunnelActive: Boolean, hasCachedAnswer: Boolean): Boolean =
        !tunnelActive || !hasCachedAnswer
}
