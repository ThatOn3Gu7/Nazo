package quiz.thaton3app.nazo.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Startup connectivity check. Combines Android's network state (is a network
 * present AND does it claim internet capability) with a real, short HTTP
 * reachability probe so we don't get fooled by captive portals / Wi-Fi with no
 * upstream. The probe runs on IO and fails fast (~1.5s timeout).
 */
object Connectivity {
    suspend fun isOnline(context: Context): Boolean = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(network)
        val hasInternetCap = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        if (network == null || !hasInternetCap) return@withContext false
        // VALIDATED means the network already passed a captive-portal check.
        validated || probe()
    }

    private fun probe(): Boolean = try {
        val conn = URL("https://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpURLConnection
        conn.connectTimeout = 1500
        conn.readTimeout = 1500
        conn.requestMethod = "GET"
        conn.connect()
        val code = conn.responseCode
        conn.disconnect()
        code == 204 || code == 200
    } catch (_: Exception) {
        false
    }
}
