package com.phigros.offline

import org.json.JSONObject

/** Offline-cached account profile read from the Phigros `.userdata` file. */
data class UserProfile(
    val nickname: String?,
    val shortId: String?
)

/**
 * Parses the local LeanCloud user cache (`.userdata`), a plain JSON object
 * persisted under the app's external files directory. This is the only
 * offline-local source of the in-game display name; the server-side save
 * does not carry the nickname in `playerprefs.xml`.
 */
object UserDataParser {

    fun parse(json: String?): UserProfile? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            val nickname = firstNonBlank(
                obj.optString("nickname"),
                obj.optJSONObject("authData")?.optJSONObject("taptap")?.optString("name")
            )
            val shortId = firstNonBlank(obj.optString("shortId"))
            if (nickname == null && shortId == null) null
            else UserProfile(nickname, shortId)
        } catch (e: Throwable) {
            null
        }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }
}
