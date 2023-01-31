package com.segment.analytics.kotlin.destinations.nielsendcr

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.nielsen.app.sdk.AppSdk
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.log
import com.segment.analytics.kotlin.core.utilities.toContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern

class NielsenDCRDestination : DestinationPlugin() {
    companion object {
        private const val NIELSEN_DCR_FULL_KEY = "Nielsen DCR"
        private const val SF_CODE = "dcr"

//        Formatter to format properties
        private val CONTENT_FORMATTER = Collections.unmodifiableMap(mapOf(
            "session_id" to "sessionId",
            "asset_id" to "assetId",
            "pod_id" to "podId",
            "total_length" to "totalLength",
            "full_episode" to "fullEpisode",
            "content_asset_id" to "contentAssetId",
            "ad_asset_id" to "adAssetId",
            "load_type" to "loadType",
        ))

        private val AD_FORMATTER = Collections.unmodifiableMap(mapOf(
            "session_id" to "sessionId",
            "asset_id" to "assetId",
            "pod_id" to "podId",
            "pod_position" to "podPosition",
            "pod_length" to "podLength",
            "total_length" to "totalLength",
            "load_type" to "loadType"
        ))

//        Mapper
        private val CONTENT_MAP = Collections.unmodifiableMap(mapOf(
            "assetId" to "assetid",
            "contentAssetId" to "assetid",
            "title" to "title",
            "program" to "program"
        ))

        private val AD_MAP = Collections.unmodifiableMap(mapOf(
            "assetId" to "assetid",
            "type" to "type",
            "title" to "title",
        ))

//        reusable variables for `airDate` helper method
        private val FORMATTER = SimpleDateFormat("yyyyMMdd HH:mm:ss")
        private val SHORT_DATE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$")
        private val LONG_DATE =
            Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})[tT](\\d{2}):(\\d{2}):(\\d{2})[zZ]$")

//        add millisecond date pattern for longer dates; accept upper or lowercase T and Z
        private val MS_DATE =
            Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})[tT](\\d{2}):(\\d{2}):(\\d{2}).(\\d{3})[zZ]$")
    }

    internal var nielsenDCRSettings: NielsenDCRSettings? = null
    internal lateinit var appSdk: AppSdk

    private var playHeadTimer: Timer? = null
    private var monitorHeadPos: TimerTask? = null
    private var playHeadPosition: Long = 0

    override val key: String = NIELSEN_DCR_FULL_KEY

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        super.update(settings, type)
        this.nielsenDCRSettings =
            settings.destinationSettings(key, NielsenDCRSettings.serializer())
        if (type == Plugin.UpdateType.Initial) {
            if (nielsenDCRSettings != null) {
                setupNielsenAppSdk()
            }
        }
    }

    override fun screen(payload: ScreenEvent): BaseEvent {
        val propertiesMap = payload.properties.asStringMap()
        val name: String = fetchSectionProperty(propertiesMap, payload.name)
        val contentAssetId: String = fetchContentAssetId(propertiesMap)
        val metadata = JSONObject()
        val nielsenOptions: Map<String?, Any> =
            ((payload.integrations.toContent()["nielsen-dcr"])
                ?: emptyMap<String?, Any>()) as Map<String?, Any>
        try {
            metadata.put("section", name)
            metadata.put("type", "static")
            metadata.put("assetid", contentAssetId)

            // segB and segC are required values, so will send a default value
            if (nielsenOptions.containsKey("segB")) {
                val segB = nielsenOptions["segB"].toString()
                metadata.put("segB", segB)
            } else {
                metadata.put("segB", "")
            }
            if (nielsenOptions.containsKey("segC")) {
                val segC = nielsenOptions["segC"].toString()
                metadata.put("segC", segC)
            } else {
                metadata.put("segC", "")
            }
            if (nielsenOptions.containsKey("crossId1")) {
                val crossId1 = nielsenOptions["crossId1"].toString()
                metadata.put("crossId1", crossId1)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            analytics.log("Error tracking Video Content: ${e.message}")
        }
        appSdk.loadMetadata(metadata)
        analytics.log("appSdk.loadMetadata($metadata)")
        return payload
    }

    override fun track(payload: TrackEvent): BaseEvent {
         if(!EventVideoEnum.isVideoEvent(payload.event)) {
             analytics.log("Event is not Video")
             return payload
        }
        val eventEnum = EventVideoEnum[payload.event]
        val nielsenProperties: Map<String, String> = payload.properties.asStringMap()
        val nielsenOptions: Map<String, String> = ((payload.integrations["nielsen-dcr"]
            ?: JsonObject(emptyMap())) as JsonObject).asStringMap()
        when (eventEnum) {
            EventVideoEnum.PlaybackStarted,
            EventVideoEnum.PlaybackPaused,
            EventVideoEnum.PlaybackInterrupted,
            EventVideoEnum.PlaybackSeekStarted,
            EventVideoEnum.PlaybackSeekCompleted,
            EventVideoEnum.PlaybackBufferStarted,
            EventVideoEnum.PlaybackBufferCompleted,
            EventVideoEnum.PlaybackResumed,
            EventVideoEnum.PlaybackExited,
            EventVideoEnum.PlaybackCompleted -> try {
                trackVideoPlayback(payload, nielsenProperties, nielsenOptions)

            } catch (e: Exception) {
                analytics.log("Error tracking Video Playback: ${e.message}")
            }
            EventVideoEnum.ContentStarted,
            EventVideoEnum.ContentPlaying,
            EventVideoEnum.ContentCompleted,
            -> try {
                trackVideoContent(payload, nielsenProperties, nielsenOptions)
            } catch (e: Exception) {
                analytics.log("Error tracking Video Content: ${e.message}")
            }
            EventVideoEnum.AdStarted,
            EventVideoEnum.AdPlaying,
            EventVideoEnum.AdCompleted,
            -> try {
                trackVideoAd(payload, nielsenProperties, nielsenOptions)
            } catch (e: Exception) {
                analytics.log("Error tracking Video Ad: ${e.message}")
            }
            else -> {
                analytics.log("Video Event not found")
            }
        }
        return payload
    }

    /**
     * creating Nielsen App SDK.
     */
    private fun setupNielsenAppSdk() {
        val appContext = analytics.configuration.application as Context
        val appName: String
        val appVersion: String
        try {
            val packageManager: PackageManager = appContext.packageManager
            val packageInfo: PackageInfo =
                packageManager.getPackageInfo(appContext.packageName, 0)
            appName = packageInfo.packageName
            appVersion = packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            analytics.log("Could not retrieve Package information. ${e.message}")
            return
        }
        // Create AppSdk configuration object (JSONObject)
        val appSdkConfig: JSONObject = JSONObject()
            .put("appid", nielsenDCRSettings!!.appId)
            .put("appname", appName)
            .put("appversion", appVersion)
            .put("sfcode", SF_CODE)
        if (nielsenDCRSettings!!.nolDevDebug) {
            appSdkConfig.put("nol_devDebug", "DEBUG")
        }
        appSdk = AppSdk(analytics.configuration.application as Context, appSdkConfig, null)
        analytics.log("new AppSdk(${appSdkConfig.toString(2)})")
    }

    private fun fetchSectionProperty(
        properties: Map<String, String>,
        defaultValue: String
    ): String {
        val customKey: String = nielsenDCRSettings!!.customSectionProperty
        val customSectionNameFromProps: String? = properties[customKey]
        return if (customKey.isNotEmpty() && !customSectionNameFromProps.isNullOrEmpty()) {
            customSectionNameFromProps
        } else if (defaultValue.isNotEmpty()) {
            defaultValue
        } else {
            "Unknown"
        }
    }

    private fun fetchContentAssetId(properties: Map<String, String>): String {
        return if (nielsenDCRSettings!!.contentAssetIdPropertyName.isNotEmpty()) {
            properties[nielsenDCRSettings!!.contentAssetIdPropertyName]!!
        } else if ((properties["assetId"]?.toString() ?: "").isNotEmpty()) {
            properties["assetId"]!!
        } else {
            (properties["contentAssetId"]?: "").toString()
        }
    }

    /**
     * For Segment-spec video event properties, this helper method maps keys in snake_case to
     * camelCase. The actual content and ad property mapping logic in this SDK only handles camelCase
     * property keys, even though Segment's video spec requires all keys in snake_case format.
     *
     * <p>Segment's video spec: https://segment.com/docs/spec/video/
     *
     * @param properties Segment event payload map converted properties
     * @param formatter Either CONTENT_FORMATTER or AD_FORMATTER
     * @return properties Segment event payload properties with keys formatter per Segment video spec
     */
    private fun toCamelCase(
        properties: Map<String, String>,
        formatter: Map<String, String>
    ): Map<String, String> {
        val mappedProperties = mutableMapOf<String, String>()
        mappedProperties.putAll(properties)
        for (keySet in formatter) {
            if(mappedProperties[keySet.key] !=null) {
                mappedProperties[keySet.value] = mappedProperties[keySet.key]!!
                mappedProperties.remove(keySet.key)
            }
        }
        return mappedProperties
    }

    /**
     * Map special keys, preserve only the special keys and convert to string -> string map
     *
     * @param properties Segment event payload map converted properties
     * @param mapper Either CONTENT_MAP or AD_MAP
     * @return JSONObject properties with mapped keys
     */
    private fun mapSpecialKeys(
        properties: Map<String, String>,
        mapper: Map<String, String>
    ): JSONObject {
        val metadata = JSONObject()
        // Map special keys and preserve only the special keys.
        for (keySet in properties) {
            if (!mapper[keySet.key].isNullOrEmpty()) {
                metadata.put(mapper[keySet.key]!!, keySet.value)
            }
        }
        return metadata
    }

    /**
     * Create and start PlayHead Timer, maintaining head position along.
     */
    private fun startPlayHeadTimer(properties: Map<String, String>, nielsen: AppSdk) {
        if (playHeadTimer != null) {
            return
        }
        playHeadPosition = getPlayHeadPosition(properties)
        playHeadTimer = Timer()
        monitorHeadPos = object : TimerTask() {
            override fun run() {
                setPlayHeadPosition()
            }

            fun setPlayHeadPosition() {
                nielsen.setPlayheadPosition(playHeadPosition)
                // ++ postfixed means we report the original position and then increment by 1 which is desired behavior for Nielsen
                playHeadPosition++
            }
        }
        analytics.log("playHeadTimer scheduled")
        playHeadTimer!!.schedule(monitorHeadPos, 0, TimeUnit.SECONDS.toMillis(1))
    }

    /**
     * Stopping and nullifying PlayHead Timer.
     */
    private fun stopPlayHeadTimer() {
        if (playHeadTimer != null) {
            playHeadTimer!!.cancel()
            monitorHeadPos!!.cancel()
            playHeadTimer = null
            analytics.log("playHeadTimer stopped")
        }
    }

    /**
     * fetch play head position from payload properties.
     */
    private fun getPlayHeadPosition(properties: Map<String, String>): Long {
        val playHeadPosition: Int = (properties["position"]?.toInt() ?: 0)
        val isLiveStream: Boolean = properties["livestream"]?.toBoolean() ?: false
        if (!isLiveStream) {
            return playHeadPosition.toLong()
        }
        val calendar = Calendar.getInstance()
        val millis = calendar.timeInMillis
        return if (nielsenDCRSettings!!.sendCurrentTimeLivestream) {
            TimeUnit.MILLISECONDS.toSeconds(millis)
        } else {
            TimeUnit.MILLISECONDS.toSeconds(millis) + playHeadPosition
        }
    }

    /**
     * Creating Content Metadata in JSONObject type. JSON value must be string value.
     */
    @Throws(JSONException::class)
    private fun buildContentMetadata(
        contentProperties: Map<String, String>, options: Map<String, *>
    ): JSONObject {
        val contentMetadata: JSONObject = mapSpecialKeys(contentProperties, CONTENT_MAP)
        // map payload options to Nielsen content metadata fields
        contentMetadata.put(
            "pipmode", options["pipmode"] ?: "false")
        if (options.containsKey("crossId1")) {
            val crossId1 = options["crossId1"].toString()
            contentMetadata.put("crossId1", crossId1)
        }
        if (options.containsKey("crossId2")) {
            val crossId2 = options["crossId2"].toString()
            contentMetadata.put("crossId2", crossId2)
        }
        if (options.containsKey("segB")) {
            val segB = options["segB"].toString()
            contentMetadata.put("segB", segB)
        }
        if (options.containsKey("segC")) {
            val segC = options["segC"].toString()
            contentMetadata.put("segC", segC)
        }
        contentMetadata.put(
            "hasAds", if ((options["hasAds"] ?: "false") == "true") "1" else "0")

        // map settings to Nielsen content metadata fields
        val contentAssetId = fetchContentAssetId(contentProperties)
        contentMetadata.put("assetid", contentAssetId)
        val clientIdPropertyName =
            nielsenDCRSettings!!.clientIdPropertyName.ifEmpty { "clientId" }
        val clientId: String? = contentProperties[clientIdPropertyName]
        if (!clientId.isNullOrEmpty()) {
            contentMetadata.put("clientid", clientId)
        }
        val subbrandPropertyName =
            nielsenDCRSettings!!.subbrandPropertyName.ifEmpty { "subbrand" }
        val subbrand: String? = contentProperties[subbrandPropertyName]
        if (!subbrand.isNullOrEmpty()) {
            contentMetadata.put("subbrand", subbrand)
        }
        val lengthPropertyName =
            nielsenDCRSettings!!.contentLengthPropertyName.ifEmpty { "totalLength" }
        if (contentProperties.containsKey(lengthPropertyName)) {
            val length: String = contentProperties[lengthPropertyName]!!
            contentMetadata.put("length", length)
        }

        // map properties with non-String values to Nielsen content metadata fields
        if (contentProperties.containsKey("airdate")) {
            var airdate: String? = contentProperties["airdate"]!!
            if (airdate != null && airdate.isNotEmpty()) {
                airdate = formatAirDate(contentProperties["airdate"]!!)
            }
            contentMetadata.put("airdate", airdate)
        }
        var adLoadType = ""
        if (options.containsKey("adLoadType")) {
            adLoadType = options["adLoadType"].toString()
        }
        if (adLoadType.isEmpty() || adLoadType == "null") {
            if (contentProperties.containsKey("loadType")) {
                adLoadType = contentProperties["loadType"]!!
            }
        }
        contentMetadata.put("adloadtype", if (adLoadType == "dynamic") "2" else "1")
        val fullEpisodeStatus: Boolean = contentProperties["fullEpisode"]?.toBoolean() ?: false
        contentMetadata.put("isfullepisode", if (fullEpisodeStatus) "y" else "n")
        contentMetadata.put("type", "content")
        return contentMetadata
    }

    /**
     * Creating Ad Metadata in JSONObject type. JSON value must be string value.
     */
    @Throws(JSONException::class)
    private fun buildAdMetadata(properties: Map<String, String>): JSONObject {
        val adMetadata = mapSpecialKeys(properties, AD_MAP)
        val adAssetIdPropertyName = nielsenDCRSettings!!.adAssetIdPropertyName.ifEmpty { "assetId" }
        val assetId: String = properties[adAssetIdPropertyName]?:""
        adMetadata.put("assetid", assetId)
        var adType: String? = properties["type"]
        adType = if (adType != null && adType.isNotEmpty()) {
            adType.replace("-", "")
        } else {
            "ad"
        }
        adMetadata.put("type", adType)
        val title: String = properties["title"]?:""
        adMetadata.put("title", title)
        return adMetadata
    }

    /**
     * Formatting string AirDate
     */
    private fun formatAirDate(airDate: String): String {
        var finalDate = airDate
        // assuming 'airDate' was passed as ISO date string per Segment spec
        try {
            val s: Matcher = SHORT_DATE.matcher(finalDate)
            val l: Matcher = LONG_DATE.matcher(finalDate)
            val m: Matcher = MS_DATE.matcher(finalDate)
            finalDate = if (s.find()) {
                StringBuilder()
                    .append(s.group(1))
                    .append(s.group(2))
                    .append(s.group(3))
                    .append(" ")
                    .append("00")
                    .append(":")
                    .append("00")
                    .append(":")
                    .append("00")
                    .toString()
            } else if (l.find()) {
                StringBuilder()
                    .append(l.group(1))
                    .append(l.group(2))
                    .append(l.group(3))
                    .append(" ")
                    .append(l.group(4))
                    .append(":")
                    .append(l.group(5))
                    .append(":")
                    .append(l.group(6))
                    .toString()
            } else if (m.find()) {
                StringBuilder()
                    .append(m.group(1))
                    .append(m.group(2))
                    .append(m.group(3))
                    .append(" ")
                    .append(m.group(4))
                    .append(":")
                    .append(m.group(5))
                    .append(":")
                    .append(m.group(6))
                    .toString()
            } else {
                throw Error("Error parsing airDate from ISO date format.")
            }
        } catch (e: Throwable) {
            analytics.log("Error parsing airDate from ISO date format.")
            // if above fail, treat as Date object
            try {
                finalDate = FORMATTER.format(FORMATTER.parse(airDate))
            } catch (ex: ParseException) {
                analytics.log("Error parsing Date object. Will not reformat date string.")
            }
        }
        return finalDate
    }

    /**
     * track video events which are related to Playback.
     */
    @Throws(JSONException::class)
    private fun trackVideoPlayback(
        payload: TrackEvent, properties: Map<String, String>, nielsenOptions: Map<String, Any>
    ) {
        val eventEnum = EventVideoEnum[payload.event]
        val contentProperties: Map<String, String> = toCamelCase(properties, CONTENT_FORMATTER)
        val contentMetadata: JSONObject = buildContentMetadata(contentProperties, nielsenOptions)
        val channelInfo = JSONObject()
        channelInfo.put("channelName", (nielsenOptions["channelName"]?.toString())?:"defaultChannelName")
        channelInfo.put("mediaURL", (nielsenOptions["mediaUrl"]?.toString())?:"")
        when (eventEnum) {
            EventVideoEnum.PlaybackStarted -> {
                appSdk.loadMetadata(contentMetadata)
                analytics.log("appSdk.loadMetadata($contentMetadata)")
                startPlayHeadTimer(properties, appSdk)
                appSdk.play(channelInfo)
                analytics.log("appSdk.play($channelInfo)")
            }
            EventVideoEnum.PlaybackResumed,
            EventVideoEnum.PlaybackSeekCompleted,
            EventVideoEnum.PlaybackBufferCompleted -> {
                startPlayHeadTimer(properties, appSdk)
                appSdk.play(channelInfo)
                analytics.log("appSdk.play($channelInfo)")
            }
            EventVideoEnum.PlaybackPaused,
            EventVideoEnum.PlaybackSeekStarted,
            EventVideoEnum.PlaybackBufferStarted,
            EventVideoEnum.PlaybackInterrupted,
            EventVideoEnum.PlaybackExited -> {
                stopPlayHeadTimer()
                appSdk.stop()
                analytics.log("appSdk.stop()")
            }
            EventVideoEnum.PlaybackCompleted-> {
                stopPlayHeadTimer()
                appSdk.end()
                analytics.log("appSdk.end()")
            }
            else-> {}
        }
    }

    /**
     * track video events which are related to Content.
     */
    private fun trackVideoContent(
        payload: TrackEvent, properties: Map<String, String>, nielsenOptions: Map<String, Any>) {
        val eventEnum = EventVideoEnum[payload.event]
        val contentProperties: Map<String, String> = toCamelCase(properties, CONTENT_FORMATTER)
        val contentMetadata: JSONObject = buildContentMetadata(contentProperties, nielsenOptions)
        when (eventEnum) {
            EventVideoEnum.ContentStarted -> {
                startPlayHeadTimer(contentProperties, appSdk)
                appSdk.loadMetadata(contentMetadata)
                analytics.log("appSdk.loadMetadata($contentMetadata)")
            }
            EventVideoEnum.ContentPlaying ->
                startPlayHeadTimer(contentProperties, appSdk)
            EventVideoEnum.ContentCompleted  -> {
                appSdk.stop()
                stopPlayHeadTimer()
            }
            else -> {}
        }
    }

    /**
     * track video events which are related to Ad.
     */
    private fun trackVideoAd(
        payload: TrackEvent, properties: Map<String, String>, nielsenOptions: Map<String, Any>
    ) {
        val eventEnum = EventVideoEnum[payload.event]
        val adProperties: Map<String, String> = toCamelCase(properties, AD_FORMATTER)
        when (eventEnum) {
            EventVideoEnum.AdStarted -> {
                // In case of ad `type` preroll, call `loadMetadata` with metadata values for content,
                // followed by `loadMetadata` with ad (preroll) metadata
                if (properties["type"].equals("pre-roll")) {
                    val contentMap = ((payload.properties["content"]
                        ?: JsonObject(emptyMap())) as JsonObject).asStringMap()
                    if (contentMap.isNotEmpty()) {
                        val contentProperties = toCamelCase(contentMap, CONTENT_FORMATTER)
                        val adContentAsset = buildContentMetadata(contentProperties, nielsenOptions)
                        appSdk.loadMetadata(adContentAsset)
                        analytics.log("appSdk.loadMetadata($adContentAsset)")
                    }
                }
                val adAsset: JSONObject = buildAdMetadata(adProperties)
                appSdk.loadMetadata(adAsset)
                analytics.log("appSdk.loadMetadata($adAsset)")
                startPlayHeadTimer(adProperties, appSdk)
            }
            EventVideoEnum.AdPlaying -> startPlayHeadTimer(adProperties, appSdk)
            EventVideoEnum.AdCompleted -> {
                stopPlayHeadTimer()
                appSdk.stop()
                analytics.log("appSdk.stop")
            }
            else -> {
            }
        }
    }
}

internal enum class EventVideoEnum(
    /**
     * Retrieves the Neilsen DCR video event name. This is different from `enum.name()`
     *
     * @return Event name.
     */
    val eventName: String
) {
    PlaybackStarted("Video Playback Started"),
    PlaybackPaused("Video Playback Paused"),
    PlaybackResumed("Video Playback Resumed"),
    PlaybackExited("Video Playback Exited"),
    PlaybackInterrupted("Video Playback Interrupted"),
    PlaybackCompleted("Video Playback Completed"),
    ContentStarted("Video Content Started"),
    ContentPlaying("Video Content Playing"),
    ContentCompleted("Video Content Completed"),
    PlaybackBufferStarted("Video Playback Buffer Started"),
    PlaybackBufferCompleted("Video Playback Buffer Completed"),
    PlaybackSeekStarted("Video Playback Seek Started"),
    PlaybackSeekCompleted("Video Playback Seek Completed"),
    AdStarted("Video Ad Started"),
    AdPlaying("Video Ad Playing"),
    AdCompleted("Video Ad Completed");

    companion object {
        private var names: MutableMap<String, EventVideoEnum>? = null

        init {
            names = HashMap()
            for (e in values()) {
                (names as HashMap<String, EventVideoEnum>)[e.eventName] = e
            }
        }

        operator fun get(name: String): EventVideoEnum? {
            if (names!!.containsKey(name)) {
                return names!![name]
            }
            throw IllegalArgumentException("$name is not a valid video event")
        }
        /**
         * Identifies if the event is a video event.
         *
         * @param eventName Event name
         * @return `true` if it's a video event, `false` otherwise.
         */
        fun isVideoEvent(eventName: String): Boolean {
            return names!!.containsKey(eventName)
        }
    }
}
/**
 * NielsenDCR Settings data class.
 */
@Serializable
internal data class NielsenDCRSettings(
    var appId: String,
    var adAssetIdPropertyName: String,
    var assetIdPropertyName: String,
    var clientIdPropertyName: String,
    var contentAssetIdPropertyName: String,
    var contentLengthPropertyName: String,
    var customSectionProperty: String,
    var instanceName: String,
    var nolDevDebug: Boolean,
    var sendCurrentTimeLivestream: Boolean,
    var sfCode: Boolean,
    var subbrandPropertyName: String,
)

private fun JsonObject.asStringMap(): Map<String, String> = this.mapValues { (_, value) ->
    value.toContent().toString()
}