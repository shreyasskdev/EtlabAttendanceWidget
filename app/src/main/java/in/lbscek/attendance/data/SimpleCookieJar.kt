package `in`.lbscek.attendance.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Minimal in-memory cookie jar. We don't need cookies to persist across app
 * launches -- each attendance refresh does a fresh login, so we only need
 * the session cookie to survive for the lifetime of one OkHttpClient call
 * chain (login -> attendance page).
 */
class SimpleCookieJar : CookieJar {
    private val store = mutableMapOf<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isNotEmpty()) {
            store[url.host] = cookies
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return store[url.host] ?: emptyList()
    }
}
