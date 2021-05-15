package com.dehnes.webwatcher

import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.mail.Email
import org.apache.commons.mail.SimpleEmail
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.nio.charset.Charset
import kotlin.random.Random
import kotlin.system.exitProcess


val stateFile = "webwatch.txt"

val userAgents = listOf(
    "Mozilla/5.0 (Linux; Android 8.0.0; SM-G960F Build/R16NW) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.84 Mobile Safari/537.36",
    "Mozilla/5.0 (Linux; Android 7.0; SM-G892A Build/NRD90M; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/60.0.3112.107 Mobile Safari/537.36",
    "Mozilla/5.0 (Linux; Android 6.0; HTC One X10 Build/MRA58K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/61.0.3163.98 Mobile Safari/537.36",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 12_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/69.0.3497.105 Mobile/15E148 Safari/605.1",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 12_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) FxiOS/13.2b11866 Mobile/16A366 Safari/605.1.15",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 11_0 like Mac OS X) AppleWebKit/604.1.38 (KHTML, like Gecko) Version/11.0 Mobile/15A372 Safari/604.1"
)

fun main(args: Array<String>) {

    if (args.size != 3) {
        println("<url> <baseDelayInMin> <emailAddr,....>")
        exitProcess(1)
    }

    val url = args[0]
    val delayMin = args[1].toLong()
    val emailAddress = args[2].split(",")

    val client = OkHttpClient()

    while (true) {

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgents[Random.nextInt(0, userAgents.size)])
            .build()

        try {
            var body = ""
            client.newCall(request).execute().use { response -> body = response.body!!.string() }

            val document = Jsoup.parse(body)
            val produktHrefs = document.select("a[href]")
                .map { el -> el.attr("href") }
                .filter { href -> href.contains("/produkt") }

            val newState = JSONArray(produktHrefs)
            val existingState = readState()

            val delta = delta(existingState, newState)

            if (delta != null) {
                println("Change detected - sending email")
                val deltaStr = delta.toString(2)

                emailAddress.forEach { addr ->
                    sendMail(
                        addr,
                        addr,
                        "Change detected $url",
                        """
                            delta: $deltaStr
                        """.trimIndent()
                    )
                }
            } else {
                println("No change detected this time")
            }

            writeState(newState)

        } catch (e: Exception) {
            e.printStackTrace()
        }

        val waitInMin = delayMin + Random.nextLong(0, 5)
        println("Waiting $waitInMin minutes")
        Thread.sleep(waitInMin * 60 * 1000)
    }
}

fun JSONArray.toStringList() = this.toList() as List<String>

fun delta(left: JSONArray, right: JSONArray): JSONObject? {
    val l = left.toStringList().sorted()
    val r = right.toStringList().sorted()

    val missing = l.filter { s -> r.none { it == s } }
    val extra = r.filter { s -> l.none { it == s } }

    return if (missing.isEmpty() && extra.isEmpty()) {
        null
    } else {
        JSONObject()
            .put("missing", missing)
            .put("new", extra)
    }
}

fun sendMail(from: String, to: String, subject: String, body: String) {
    try {
        val email: Email = SimpleEmail()
        email.hostName = "smtp.altibox.no"
        email.setSmtpPort(25)
        email.setFrom(from)
        email.subject = subject
        email.setMsg(body)
        email.addTo(to)
        email.send()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun readState() = try {
    JSONArray(File(stateFile).readText(Charset.defaultCharset()))
} catch (e: Exception) {
    JSONArray()
}

fun writeState(a: JSONArray) = try {
    File(stateFile).writeText(a.toString(), Charset.defaultCharset())
} catch (e: Exception) {
    e.printStackTrace()
}

