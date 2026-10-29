package app.northvane.core.ai

import app.northvane.core.json.NorthvaneJson
import app.northvane.core.json.arr
import app.northvane.core.json.asObj
import app.northvane.core.json.obj
import app.northvane.core.json.str
import app.northvane.core.model.AlertCategory
import app.northvane.core.model.UnitSystem
import app.northvane.core.model.Units
import app.northvane.core.model.WeatherSnapshot
import app.northvane.core.net.HttpStatusException
import app.northvane.core.net.InsecureConfigurationException
import app.northvane.core.net.InvalidCredentialsException
import app.northvane.core.net.LanCleartextAllowed
import app.northvane.core.net.LocalServerClients
import app.northvane.core.net.ParseException
import app.northvane.core.net.UrlPolicy
import app.northvane.core.net.WeatherApiException
import app.northvane.core.net.fetch
import app.northvane.core.util.UnitFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Self-hosted or hosted LLM servers that speak the OpenAI chat-completions
 * format. [chatPath] and [modelsPath] are appended to the user's base URL.
 */
@Serializable
enum class AiProvider(val label: String, val example: String, val chatPath: String, val modelsPath: String?, val keyRequired: Boolean, val note: String) {
    OLLAMA("Ollama", "http://192.168.1.20:11434", "/v1/chat/completions", "/api/tags", false,
        "Ollama's OpenAI-compatible API; no key needed. Pick a small model such as llama3.2 or qwen2.5."),
    OPEN_WEBUI("Open WebUI", "https://openwebui.example.com", "/api/chat/completions", "/api/models", true,
        "Create an API key under Settings › Account in Open WebUI."),
    LIBRECHAT("LibreChat", "https://librechat.example.com", "/api/agents/v1/chat/completions", null, true,
        "Uses LibreChat's Agents API (beta): enable remote agents, create an API key, and enter an agent ID as the model."),
    OPENAI_COMPATIBLE("OpenAI-compatible", "https://api.openai.com/v1", "/chat/completions", "/models", false,
        "Any server with /chat/completions (OpenAI, LM Studio, vLLM, llama.cpp, LiteLLM…). Hosted services bill per use."),
}

data class AiConfig(val provider: AiProvider, val baseUrl: String, val model: String, val apiKey: String?)

/** Talks to the user's AI server. LAN servers may use plain HTTP, but never with an API key. */
class AiClient(clients: LocalServerClients) {
    // Local models can take a while on modest hardware.
    private val https: OkHttpClient = clients.https.newBuilder().readTimeout(120, TimeUnit.SECONDS).callTimeout(150, TimeUnit.SECONDS).build()
    private val cleartext: OkHttpClient = clients.cleartext.newBuilder().readTimeout(120, TimeUnit.SECONDS).callTimeout(150, TimeUnit.SECONDS).build()

    private fun endpoint(config: AiConfig, path: String): Pair<HttpUrl, Boolean> {
        val base = config.baseUrl.trim().trimEnd('/')
        val url = base + path
        val validated = when (val v = UrlPolicy.validateUserServerUrl(url, sendsCredentials = !config.apiKey.isNullOrBlank())) {
            is UrlPolicy.Validation.Invalid -> throw InsecureConfigurationException(v.reason)
            is UrlPolicy.Validation.Valid -> v
        }
        return validated.url to validated.isCleartext
    }

    private fun request(config: AiConfig, url: HttpUrl, cleartext: Boolean): Request.Builder =
        Request.Builder().url(url).header("Accept", "application/json").header("Cache-Control", "no-store").apply {
            config.apiKey?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
            if (cleartext) tag(LanCleartextAllowed::class.java, LanCleartextAllowed)
        }

    suspend fun complete(config: AiConfig, messages: List<Pair<String, String>>, maxTokens: Int = 300): String {
        require(config.model.isNotBlank()) { "Choose a model" }
        val (url, clear) = endpoint(config, config.provider.chatPath)
        val body = buildJsonObject {
            put("model", config.model)
            putJsonArray("messages") {
                messages.forEach { (role, content) -> addJsonObject { put("role", role); put("content", content) } }
            }
            put("temperature", 0.4)
            put("max_tokens", maxTokens)
            put("stream", false)
        }.toString()
        val response = (if (clear) cleartext else https).fetch(
            request(config, url, clear).post(body.toRequestBody("application/json".toMediaType())).build(),
            maxBytes = 512L * 1024,
        )
        when (response.code) {
            in 200..299 -> Unit
            401, 403 -> throw InvalidCredentialsException("Your AI server")
            404 -> throw WeatherApiException("Your AI server doesn't have that endpoint or model (HTTP 404)")
            else -> throw HttpStatusException(response.code, "Your AI server")
        }
        return parseCompletion(response.body)
    }

    suspend fun models(config: AiConfig): List<String> {
        val path = config.provider.modelsPath ?: return emptyList()
        val (url, clear) = endpoint(config, path)
        val response = (if (clear) cleartext else https).fetch(request(config, url, clear).build(), maxBytes = 1024L * 1024)
        if (response.code == 401 || response.code == 403) throw InvalidCredentialsException("Your AI server")
        if (response.code !in 200..299) throw HttpStatusException(response.code, "Your AI server")
        return parseModels(response.body)
    }

    companion object {
        private val THINK = Regex("(?s)<think>.*?</think>")

        /** The reply text, with any <think>…</think> reasoning (e.g. DeepSeek-R1, Qwen3) removed. */
        fun parseCompletion(json: String): String {
            val root = runCatching { NorthvaneJson.parseToJsonElement(json).asObj() }.getOrNull()
                ?: throw ParseException("Unexpected response from your AI server")
            root.obj("error")?.str("message")?.let { throw WeatherApiException("AI server: ${it.take(200)}") }
            val content = root.arr("choices")?.firstOrNull()?.asObj()?.obj("message")?.str("content")
                ?: root.obj("message")?.str("content") // Ollama native /api/chat shape
                ?: throw ParseException("Your AI server returned no text")
            return content.replace(THINK, "").replace(Regex("[*#`]+"), "").trim().take(1200)
        }

        /** OpenAI/Open WebUI `{"data":[{"id":…}]}` or Ollama `{"models":[{"name":…}]}`. */
        fun parseModels(json: String): List<String> {
            val root = runCatching { NorthvaneJson.parseToJsonElement(json).asObj() }.getOrNull() ?: return emptyList()
            val list = (root.arr("data") ?: root.arr("models")) as? JsonArray ?: return emptyList()
            return list.mapNotNull { it.asObj()?.let { o -> o.str("id") ?: o.str("name") ?: o.str("model") } }.distinct().sorted()
        }
    }
}

/**
 * Builds a compact, factual prompt from the forecast so a small local model
 * can write a short, friendly briefing. Only weather numbers and the place
 * name are included.
 */
object WeatherBrief {
    const val SYSTEM = "You are a friendly, concise weather briefer for a phone weather app. " +
        "Write 2 to 4 short sentences in plain text (no lists, no markdown, no emoji). " +
        "Cover how today feels, when rain or snow arrives or ends, notable wind, the temperature trend into tomorrow, " +
        "and any active warning or watch. Use only the data given; never invent numbers."

    fun prompt(s: WeatherSnapshot, place: String, units: UnitSystem, now: Instant): String {
        val zone = runCatching { ZoneId.of(s.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
        val f = UnitFormatter(units)
        val hour = DateTimeFormatter.ofPattern("EEE h a", Locale.US).withZone(zone)
        return buildString {
            appendLine("Place: $place. Local time: ${hour.format(now)}. Units: ${if (units == UnitSystem.IMPERIAL) "°F, mph, inches" else "°C, km/h, mm"}.")
            s.current?.let { c ->
                appendLine(
                    "Now: ${f.temperatureWithUnit(c.temperatureC)}, feels like ${f.temperatureWithUnit(c.feelsLikeC ?: c.temperatureC)}, " +
                        "${c.description ?: c.condition.label}, wind ${f.wind(c.windSpeedKph, c.windDirectionDeg)}" +
                        (c.windGustKph?.let { " gusting ${f.windSpeed(it)}" } ?: "") + (c.humidityPercent?.let { ", humidity ${f.percent(it)}" } ?: "") + ".",
                )
            }
            val next = s.hourly.filter { it.time.isAfter(now.minus(Duration.ofMinutes(59))) }.take(18)
            if (next.isNotEmpty()) {
                val wet = next.filter { (it.precipitationChance ?: 0) >= 40 }
                appendLine(
                    "Next 18 hours: temperatures ${f.temperatureWithUnit(next.mapNotNull { it.temperatureC }.minOrNull())} to " +
                        "${f.temperatureWithUnit(next.mapNotNull { it.temperatureC }.maxOrNull())}; " +
                        (if (wet.isEmpty()) "precipitation unlikely" else "precipitation likely ${hour.format(wet.first().time)} to ${hour.format(wet.last().time)} (${wet.first().shortForecast ?: "rain"}, up to ${wet.maxOf { it.precipitationChance ?: 0 }}%)") +
                        (next.mapNotNull { it.windGustKph }.maxOrNull()?.let { "; gusts up to ${f.windSpeed(it)}" } ?: "") + ".",
                )
            }
            s.daily.take(3).forEach { d ->
                append("${d.name}: high ${f.temperatureWithUnit(d.highC)}, low ${f.temperatureWithUnit(d.lowC)}, ${d.shortForecast ?: d.condition.label}")
                d.precipitationChance?.takeIf { it >= 20 }?.let { append(", ${it}% chance of precipitation") }
                d.snowMm?.takeIf { it >= 10 }?.let { append(", snow about ${f.snow(it)}") }
                d.gustMaxKph?.takeIf { it >= 40 }?.let { append(", gusts to ${f.windSpeed(it)}") }
                appendLine(".")
            }
            s.alerts.filter { it.category == AlertCategory.WARNING || it.category == AlertCategory.WATCH || it.category == AlertCategory.ADVISORY }
                .take(3).forEach { a -> appendLine("Active ${a.event}" + ((a.ends ?: a.expires)?.let { " until ${hour.format(it)}" } ?: "") + ".") }
            s.airQuality?.current?.let { appendLine("Air quality index ${it.aqi} (${it.category.label}).") }
            s.uv?.todayMax?.let { if (it >= 3) appendLine("UV index peaks near ${it.toInt()} today.") }
        }.trim()
    }

    fun messages(s: WeatherSnapshot, place: String, units: UnitSystem, now: Instant) =
        listOf("system" to SYSTEM, "user" to prompt(s, place, units, now))
}
