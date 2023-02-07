package com.segment.analytics.kotlin.destinations.nielsendcr

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.nielsen.app.sdk.AppSdk
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.LenientJson
import com.segment.analytics.kotlin.destinations.matchers.matchJSONObject
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NielsenDCRDestinationTest {
    @MockK
    lateinit var mockApplication: Application
    @MockK
    lateinit var mockedContext: Context
    @MockK(relaxUnitFun = true)
    lateinit var mockedAnalytics: Analytics
    @MockK(relaxUnitFun = true)
    lateinit var mockedAppSdk: AppSdk
    @MockK(relaxUnitFun = true)
    lateinit var mockedPackageManager: PackageManager

    lateinit var mockedNielsenDcrDestination: NielsenDCRDestination
    private val sampleNielsenNCRSettings: Settings = LenientJson.decodeFromString(
        """
            {
              "integrations": {
                "Nielsen DCR": {
                   "adAssetIdPropertyName": "",
                   "appId": "APPID1234567890",
                   "assetIdPropertyName": "",
                   "clientIdPropertyName": "",
                   "contentAssetIdPropertyName": "",
                   "contentLengthPropertyName": "",
                   "customSectionProperty": "",
                   "instanceName": "",
                   "nolDevDebug": false,
                   "sendCurrentTimeLivestream": false,
                   "sfCode": true,
                   "subbrandPropertyName": ""
                }
              }
            }
        """.trimIndent()
    )

    init {
        MockKAnnotations.init(this)
    }

    @Before
    fun setUp() {
        mockedNielsenDcrDestination = NielsenDCRDestination()
        every { mockedAnalytics.configuration.application } returns mockApplication
        every { mockApplication.applicationContext } returns mockedContext
        every { mockApplication.packageName } returns "unknown"
        mockedAnalytics.configuration.application = mockedContext
        mockedNielsenDcrDestination.analytics = mockedAnalytics

        // An Nielsen DCR example settings
        val nielsenNCRSettings: Settings = sampleNielsenNCRSettings
        val packageInfo = PackageInfo()
        packageInfo.packageName = "unknown"
        packageInfo.versionName = "test"
        every { mockApplication.packageManager } returns mockedPackageManager
        every { mockedPackageManager.getPackageInfo("unknown", 0) } returns packageInfo

        mockedNielsenDcrDestination.update(nielsenNCRSettings, Plugin.UpdateType.Initial)
        mockedNielsenDcrDestination.appSdk = mockedAppSdk
    }

    @Test
    fun `screen handled correctly`() {
        val sampleEvent = ScreenEvent(
            name = "Screen 1",
            category = "Category 1",
            properties = buildJsonObject {
                put("variation", "New Screen")
                put("assetId", 123456)
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            context = emptyJsonObject
            integrations = emptyJsonObject
        }
        mockedNielsenDcrDestination.screen(sampleEvent)
        val screenExpectedMetadata = JSONObject()
        screenExpectedMetadata.put("assetid", "123456")
        screenExpectedMetadata.put("section", "Screen 1")
        screenExpectedMetadata.put("type", "static")
        screenExpectedMetadata.put("segB", "")
        screenExpectedMetadata.put("segC", "")
        verify { mockedAppSdk.loadMetadata(matchJSONObject(screenExpectedMetadata)) }
    }

    @Test
    fun `screen handled with custom settings correctly`() {
        mockedNielsenDcrDestination.nielsenDCRSettings?.customSectionProperty = "customSection"
        mockedNielsenDcrDestination.nielsenDCRSettings?.contentAssetIdPropertyName = "customContentAssetId"
        val sampleEvent = ScreenEvent(
            name = "Screen 1",
            category = "Category 1",
            properties = buildJsonObject {
                put("variation", "New Screen")
                put("customSection", "customSection")
                put("customContentAssetId", 123456)
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            context = emptyJsonObject
            integrations = buildJsonObject {
                put("nielsen-dcr",  buildJsonObject {
                    put("segB", "segmentB")
                    put("segC", "segmentC")
                    put("crossId1", "Cross Id 1 value")
                })
            }
        }
        mockedNielsenDcrDestination.screen(sampleEvent)
        val screenExpectedMetadata = JSONObject()
        screenExpectedMetadata.put("assetid", "123456")
        screenExpectedMetadata.put("section", "customSection")
        screenExpectedMetadata.put("type", "static")
        screenExpectedMetadata.put("segB", "segmentB")
        screenExpectedMetadata.put("segC", "segmentC")
        screenExpectedMetadata.put("crossId1", "Cross Id 1 value")
        verify { mockedAppSdk.loadMetadata(matchJSONObject(screenExpectedMetadata)) }
    }

    @Test
    fun `settings are updated correctly`() {

        /* assertions Nielsen DCR config */
        Assertions.assertNotNull(mockedNielsenDcrDestination.nielsenDCRSettings)
        with(mockedNielsenDcrDestination.nielsenDCRSettings!!) {
            assertEquals(appId, "APPID1234567890")
            assertEquals(nolDevDebug, false)
            assertEquals(sendCurrentTimeLivestream, false)
            assertEquals(sfCode, true)
        }
    }

    @Test
    fun `track for Video Playback Started handled correctly`() {
        val sampleEvent = TrackEvent(
            event = EventVideoEnum.PlaybackStarted.eventName,
            properties = buildJsonObject {
                put("assetId", 123456)
                put("adType", "pre-roll")
                put("totalLength", 120)
                put("videoPlayer", "Video Player 1")
                put("sound", 80)
                put("bitrate", 40)
                put("fullScreen", true)
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = emptyJsonObject
            context = emptyJsonObject
        }

       mockedNielsenDcrDestination.track(sampleEvent)
        val expectedChannelInfo = JSONObject()
        expectedChannelInfo.put("channelName", "defaultChannelName")
        expectedChannelInfo.put("mediaURL", "")

        val expectedMetaData = JSONObject()
        expectedMetaData.put("hasAds", "0")
        expectedMetaData.put("assetid", "123456")
        expectedMetaData.put("length", "120")
        expectedMetaData.put("isfullepisode", "n")
        expectedMetaData.put("adloadtype", "1")
        expectedMetaData.put("type", "content")
        expectedMetaData.put("pipmode", "false")
        verify { mockedAppSdk.loadMetadata(matchJSONObject(expectedMetaData)) }
        verify { mockedAppSdk.play(matchJSONObject(expectedChannelInfo)) }
    }

    @Test
    fun `track for Video Playback Started with live stream handled correctly`() {
        val sampleEvent = TrackEvent(
            event = "Video Playback Started",
            properties = buildJsonObject {
                put("assetId", 123456)
                put("adType", "pre-roll")
                put("totalLength", 120)
                put("videoPlayer", "Video Player 1")
                put("sound", 80)
                put("bitrate", 40)
                put("fullScreen", true)
                put("livestream", true)
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = emptyJsonObject
            context = emptyJsonObject
        }

        mockedNielsenDcrDestination.track(sampleEvent)
        val expectedChannelInfo = JSONObject()
        expectedChannelInfo.put("channelName", "defaultChannelName")
        expectedChannelInfo.put("mediaURL", "")

        val expectedMetaData = JSONObject()
        expectedMetaData.put("hasAds", "0")
        expectedMetaData.put("assetid", "123456")
        expectedMetaData.put("length", "120")
        expectedMetaData.put("isfullepisode", "n")
        expectedMetaData.put("adloadtype", "1")
        expectedMetaData.put("type", "content")
        expectedMetaData.put("pipmode", "false")

        verify { mockedAppSdk.loadMetadata(matchJSONObject(expectedMetaData)) }
        verify { mockedAppSdk.play(matchJSONObject(expectedChannelInfo)) }
    }

    @Test
    fun `track for Video Playback Paused handled correctly`() {
        val sampleEvent = TrackEvent(
            event = "Video Playback Paused",
            properties = buildJsonObject {
                put("assetId", 123456)
                put("adType", "mid-roll")
                put("totalLength", 120)
                put("videoPlayer", "video player 1")
                put("position", 10)
                put("sound", 80)
                put("bitrate", 40)
                put("fullScreen", true)
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = buildJsonObject {
                put("nielsen-dcr",  buildJsonObject {
                    put("channelName", "Channel Test Name")
                })
            }
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)
        verify { mockedAppSdk.stop() }
    }

    @Test
    fun `track for Video Playback Interrupted handled correctly`() {
        val sampleEvent = TrackEvent(
            event = "Video Playback Interrupted",
            properties = buildJsonObject {
                put("assetId", 123456)
                put("adType", "mid-roll")
                put("totalLength", 120)
                put("videoPlayer", "video player 1")
                put("position", 10)
                put("sound", 80)
                put("bitrate", 40)
                put("fullScreen", true)
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = buildJsonObject {
                put("nielsen-dcr",  buildJsonObject {
                    put("channelName", "Channel Test Name")
                })
            }
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)
        verify { mockedAppSdk.stop() }
    }

    @Test
    fun `track for Video Playback Exited handled correctly`() {
        val sampleEvent = TrackEvent(
            event = "Video Playback Exited",
            properties = buildJsonObject {
                put("assetId", 123456)
                put("adType", "mid-roll")
                put("totalLength", 120)
                put("videoPlayer", "video player 1")
                put("position", 10)
                put("sound", 80)
                put("bitrate", 40)
                put("fullScreen", true)
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = buildJsonObject {
                put("nielsen-dcr",  buildJsonObject {
                    put("channelName", "Channel Test Name")
                })
            }
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)
        verify { mockedAppSdk.stop() }
    }

    @Test
    fun `track for Video Playback Resumed handled correctly`() {
        val sampleEvent = TrackEvent(
            event = "Video Playback Resumed",
            properties = buildJsonObject {
                put("assetId", 123456)
                put("adType", "mid-roll")
                put("totalLength", 120)
                put("videoPlayer", "video player 1")
                put("position", 10)
                put("sound", 80)
                put("bitrate", 40)
                put("fullScreen", true)
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = buildJsonObject {
                put("nielsen-dcr",  buildJsonObject {
                    put("channelName", "Channel Test Name")
                })
            }
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)
        val expectedChannelInfo = JSONObject()
        expectedChannelInfo.put("channelName", "Channel Test Name")
        expectedChannelInfo.put("mediaURL", "")

        verify { mockedAppSdk.play(matchJSONObject(expectedChannelInfo)) }
    }

    @Test
    fun `track for Video Playback Completed handled correctly`() {
        val sampleEvent = TrackEvent(
            event = "Video Playback Completed",
            properties = buildJsonObject {
                put("assetId", 123456)
                put("adType", "mid-roll")
                put("totalLength", 120)
                put("videoPlayer", "video player 1")
                put("position", 10)
                put("sound", 80)
                put("bitrate", 40)
                put("fullScreen", true)
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = buildJsonObject {
                put("nielsen-dcr",  buildJsonObject {
                    put("channelName", "Channel Test Name")
                })
            }
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)
        verify { mockedAppSdk.end() }
    }

    @Test
    fun `track for Video Content Started handled correctly`() {
        val sampleEvent = TrackEvent(
            event = "Video Content Started",
            properties = buildJsonObject {
                put("assetId", 123456)
                put("title", "Title")
                put("season", 1)
                put("episode", 10)
                put("genre", "Fiction")
                put("program", "Program 1")
                put("channel", "Channel 1")
                put("publisher", "Publisher 1")
                put("clientId", "testClientId")
                put("subbrand", "testBrand1")
                put("fullEpisode", true)
                put("podId", "segment A")
                put("position", 50)
                put("totalLength", 1000)
                put("loadType", "dynamic")
                put("airdate", "2023-01-30T15:00:00.000Z")
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = buildJsonObject {
                put("nielsen-dcr",  buildJsonObject {
                    put("segB", "segmentB")
                    put("crossId2", "Cross Id 2")
                })
            }
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)

        val expectedMetaData = JSONObject()
        expectedMetaData.put("hasAds", "0")
        expectedMetaData.put("subbrand", "testBrand1")
        expectedMetaData.put("assetid", "123456")
        expectedMetaData.put("clientid", "testClientId")
        expectedMetaData.put("airdate", "20230130 15:00:00")
        expectedMetaData.put("length", "1000")
        expectedMetaData.put("isfullepisode", "y")
        expectedMetaData.put("program", "Program 1")
        expectedMetaData.put("adloadtype", "2")
        expectedMetaData.put("title", "Title")
        expectedMetaData.put("type", "content")
        expectedMetaData.put("pipmode", "false")
        expectedMetaData.put("segB", "segmentB")
        expectedMetaData.put("crossId2", "Cross Id 2")
        verify { mockedAppSdk.loadMetadata(matchJSONObject(expectedMetaData)) }
    }

    @Test
    fun `track for Video Content Started with custom settings handled correctly`() {
        mockedNielsenDcrDestination.nielsenDCRSettings?.contentAssetIdPropertyName = "customContentAssetId"
        mockedNielsenDcrDestination.nielsenDCRSettings?.clientIdPropertyName = "customClientId"
        mockedNielsenDcrDestination.nielsenDCRSettings?.subbrandPropertyName = "customSubbrand"
        mockedNielsenDcrDestination.nielsenDCRSettings?.contentLengthPropertyName = "customLength"

        val sampleEvent = TrackEvent(
            event = "Video Content Started",
            properties = buildJsonObject {
                put("assetId", 1)
                put("customContentAssetId", 1)
                put("title", "Title")
                put("season", 1)
                put("episode", 10)
                put("genre", "Fiction")
                put("program", "Program 1")
                put("channel", "Channel 1")
                put("publisher", "Publisher 1")
                put("clientId", "testClientId")
                put("customClientId", "testCustomClientId")
                put("subbrand", "testBrand1")
                put("customSubbrand", "testCustomBrand1")
                put("fullEpisode", true)
                put("podId", "segment A")
                put("position", 50)
                put("customLength", 1000)
                put("loadType", "dynamic")
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations =  buildJsonObject {
                put("nielsen-dcr",  buildJsonObject {
                    put("segB", "segmentB")
                    put("crossId2", "Cross Id 2")
                })
            }
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)
        val expectedMetaData = JSONObject()
        expectedMetaData.put("hasAds", "0")
        expectedMetaData.put("subbrand", "testBrand1")
        expectedMetaData.put("assetid", "1")
        expectedMetaData.put("clientid", "testClientId")
        expectedMetaData.put("length", "1000")
        expectedMetaData.put("isfullepisode", "y")
        expectedMetaData.put("program", "Program 1")
        expectedMetaData.put("adloadtype", "2")
        expectedMetaData.put("title", "Title")
        expectedMetaData.put("type", "content")
        expectedMetaData.put("pipmode", "false")
        expectedMetaData.put("clientid", "testCustomClientId")
        expectedMetaData.put("subbrand", "testCustomBrand1")
        expectedMetaData.put("segB", "segmentB")
        expectedMetaData.put("crossId2", "Cross Id 2")
        verify { mockedAppSdk.loadMetadata(matchJSONObject(expectedMetaData)) }
    }

    @Test
    fun `track for Video Content Started with ads handled correctly`() {
        val sampleEvent = TrackEvent(
            event = "Video Content Started",
            properties = buildJsonObject {
                put("assetId", 123456)
                put("title", "Title")
                put("season", 1)
                put("episode", 10)
                put("genre", "Fiction")
                put("program", "Program 1")
                put("channel", "Channel 1")
                put("publisher", "Publisher 1")
                put("fullEpisode", true)
                put("podId", "segment A")
                put("position", 50)
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = buildJsonObject {
                put("nielsen-dcr",  buildJsonObject {
                    put("segB", "segmentB")
                    put("hasAds", true)
                })
            }
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)

        val expectedMetaData = JSONObject()
        expectedMetaData.put("hasAds", "1")
        expectedMetaData.put("assetid", "123456")
        expectedMetaData.put("isfullepisode", "y")
        expectedMetaData.put("program", "Program 1")
        expectedMetaData.put("adloadtype", "1")
        expectedMetaData.put("title", "Title")
        expectedMetaData.put("type", "content")
        expectedMetaData.put("pipmode", "false")
        expectedMetaData.put("segB", "segmentB")
        verify { mockedAppSdk.loadMetadata(matchJSONObject(expectedMetaData)) }
    }

    @Test
    fun `track for Video Content Started with airDate correctly`() {
        val sampleEvent = TrackEvent(
            event = "Video Content Started",
            properties = buildJsonObject {
                put("assetId", 123456)
                put("title", "Title")
                put("season", 1)
                put("episode", 10)
                put("genre", "Fiction")
                put("program", "Program 1")
                put("channel", "Channel 1")
                put("publisher", "Publisher 1")
                put("clientId", "testClientId1")
                put("subbrand", "testSubbrand")
                put("fullEpisode", true)
                put("podId", "segment A")
                put("position", 50)
                put("totalLength", 1500)
                put("airdate", "2023-01-30T15:00:00.000Z")
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = buildJsonObject {
                put("nielsen-dcr",  buildJsonObject {
                    put("segB", "segmentB")
                    put("crossId2", "crossTestId")
                })
            }
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)

        val expectedMetaData = JSONObject()
        expectedMetaData.put("hasAds", "0")
        expectedMetaData.put("assetid", "123456")
        expectedMetaData.put("airdate", "20230130 15:00:00")
        expectedMetaData.put("isfullepisode", "y")
        expectedMetaData.put("program", "Program 1")
        expectedMetaData.put("adloadtype", "1")
        expectedMetaData.put("title", "Title")
        expectedMetaData.put("type", "content")
        expectedMetaData.put("pipmode", "false")
        expectedMetaData.put("segB", "segmentB")
        expectedMetaData.put("clientid", "testClientId1")
        expectedMetaData.put("subbrand", "testSubbrand")
        expectedMetaData.put("crossId2", "crossTestId")
        expectedMetaData.put("length", "1500")
        verify { mockedAppSdk.loadMetadata(matchJSONObject(expectedMetaData)) }
    }

    @Test
    fun `track for Video Content Started with incorrect airDate correctly`() {
        val sampleEvent = TrackEvent(
            event = "Video Content Started",
            properties = buildJsonObject {
                put("assetId", 123456)
                put("title", "Title")
                put("season", 1)
                put("episode", 10)
                put("genre", "Fiction")
                put("program", "Program 1")
                put("channel", "Channel 1")
                put("publisher", "Publisher 1")
                put("clientId", "testClientId1")
                put("subbrand", "testSubbrand")
                put("fullEpisode", true)
                put("podId", "segment A")
                put("position", 50)
                put("totalLength", 1500)
                put("airdate", "Incorrect AirDate")
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = buildJsonObject {
                put("nielsen-dcr",  buildJsonObject {
                    put("segB", "segmentB")
                    put("crossId2", "crossTestId")
                })
            }
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)

        val expectedMetaData = JSONObject()
        expectedMetaData.put("hasAds", "0")
        expectedMetaData.put("assetid", "123456")
        expectedMetaData.put("airdate", "Incorrect AirDate")
        expectedMetaData.put("isfullepisode", "y")
        expectedMetaData.put("program", "Program 1")
        expectedMetaData.put("adloadtype", "1")
        expectedMetaData.put("title", "Title")
        expectedMetaData.put("type", "content")
        expectedMetaData.put("pipmode", "false")
        expectedMetaData.put("segB", "segmentB")
        expectedMetaData.put("clientid", "testClientId1")
        expectedMetaData.put("subbrand", "testSubbrand")
        expectedMetaData.put("crossId2", "crossTestId")
        expectedMetaData.put("length", "1500")
        verify { mockedAppSdk.loadMetadata(matchJSONObject(expectedMetaData)) }
    }

    @Test
    fun `track for Video Content Started with load_type correctly`() {
        val sampleEvent = TrackEvent(
            event = "Video Content Started",
            properties = buildJsonObject {
                put("assetId", 123456)
                put("title", "Title")
                put("season", 1)
                put("episode", 10)
                put("genre", "Fiction")
                put("program", "Program 1")
                put("channel", "Channel 1")
                put("publisher", "Publisher 1")
                put("clientId", "testClientId1")
                put("subbrand", "testSubbrand")
                put("fullEpisode", true)
                put("podId", "segment A")
                put("position", 50)
                put("totalLength", 1500)
                put("load_type", "dynamic")
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = buildJsonObject {
                put("nielsen-dcr",  buildJsonObject {
                    put("segB", "segmentB")
                    put("crossId2", "crossTestId")
                })
            }
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)

        val expectedMetaData = JSONObject()
        expectedMetaData.put("hasAds", "0")
        expectedMetaData.put("assetid", "123456")
        expectedMetaData.put("adloadtype", "2")
        expectedMetaData.put("isfullepisode", "y")
        expectedMetaData.put("program", "Program 1")
        expectedMetaData.put("title", "Title")
        expectedMetaData.put("type", "content")
        expectedMetaData.put("pipmode", "false")
        expectedMetaData.put("segB", "segmentB")
        expectedMetaData.put("clientid", "testClientId1")
        expectedMetaData.put("subbrand", "testSubbrand")
        expectedMetaData.put("crossId2", "crossTestId")
        expectedMetaData.put("length", "1500")
        verify { mockedAppSdk.loadMetadata(matchJSONObject(expectedMetaData)) }
    }

    @Test
    fun `track for Video Ad Started handled correctly`() {
        val sampleEvent = TrackEvent(
            event = "Video Ad Started",
            properties = buildJsonObject {
                put("assetId", 123456)
                put("podId", "adSegmentA")
                put("totalLength", 1000)
                put("position", 0)
                put("title", "Ad Test Title")
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = emptyJsonObject
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)
        val expectedMetaData = JSONObject()
        expectedMetaData.put("assetid", "123456")
        expectedMetaData.put("type", "ad")
        expectedMetaData.put("title", "Ad Test Title")
        verify { mockedAppSdk.loadMetadata(matchJSONObject(expectedMetaData)) }
    }

    @Test
    fun `track for Video Ad Started with settings handled correctly`() {
        mockedNielsenDcrDestination.nielsenDCRSettings?.adAssetIdPropertyName = "customAdAssetId"

        val sampleEvent = TrackEvent(
            event = "Video Ad Started",
            properties = buildJsonObject {
                put("assetId", 123456)
                put("customAdAssetId", 1234)
                put("podId", "adSegmentA")
                put("type", "pre-roll")
                put("totalLength", 1000)
                put("position", 0)
                put("title", "Ad Test Title")
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = emptyJsonObject
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)
        val expectedMetaData = JSONObject()
        expectedMetaData.put("assetid", "1234")
        expectedMetaData.put("type", "preroll")
        expectedMetaData.put("title", "Ad Test Title")
        verify { mockedAppSdk.loadMetadata(matchJSONObject(expectedMetaData)) }
    }

    @Test
    fun `track for Video Ad Started with type mid-roll handled correctly`() {
        val sampleEvent = TrackEvent(
            event = "Video Ad Started",
            properties = buildJsonObject {
                put("assetId", 123456)
                put("customAdAssetId", 1234)
                put("podId", "adSegmentA")
                put("type", "mid-roll")
                put("totalLength", 1000)
                put("position", 0)
                put("title", "Ad Test Title")
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = emptyJsonObject
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)
        val expectedMetaData = JSONObject()
        expectedMetaData.put("assetid", "123456")
        expectedMetaData.put("type", "midroll")
        expectedMetaData.put("title", "Ad Test Title")
        verify { mockedAppSdk.loadMetadata(matchJSONObject(expectedMetaData)) }
    }

    @Test
    fun `track for Video Ad Started with type pre-roll handled correctly`() {
        val sampleEvent = TrackEvent(
            event = "Video Ad Started",
            properties = buildJsonObject {
                put("assetId", 123456)
                put("type", "pre-roll")
                put("title", "Ad Test Title")
                put("content", buildJsonObject {
                    put("podId", "adSegmentA")
                    put("totalLength", 1000)
                    put("load_type", "dynamic")
                    put("position", 0)
                    put("contentAssetId", "contentAssesId1")
                    put("clientId", "testClientId")
                    put("subbrand", "testSubBrand")
                    put("title", "Ad Test Title")
                })
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = buildJsonObject {
                put("nielsen-dcr",  buildJsonObject {
                    put("segB", "segmentB")
                    put("hasAds", true)
                })
            }
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)

        val contentExpectedMetaData = JSONObject()
        contentExpectedMetaData.put("assetid", "contentAssesId1")
        contentExpectedMetaData.put("type", "content")
        contentExpectedMetaData.put("title", "Ad Test Title")
        contentExpectedMetaData.put("pipmode", "false")
        contentExpectedMetaData.put("segB", "segmentB")
        contentExpectedMetaData.put("clientid", "testClientId")
        contentExpectedMetaData.put("subbrand", "testSubBrand")
        contentExpectedMetaData.put("length", "1000")
        contentExpectedMetaData.put("adloadtype", "2")
        contentExpectedMetaData.put("hasAds", "1")
        contentExpectedMetaData.put("isfullepisode", "n")

        val adExpectedMetadata = JSONObject()
        adExpectedMetadata.put("assetid", "123456")
        adExpectedMetadata.put("type", "preroll")
        adExpectedMetadata.put("title", "Ad Test Title")

        val contentCaptureSlot = slot<JSONObject>()
        val adCaptureSlot = slot<JSONObject>()

        verifyOrder {
            mockedAppSdk.loadMetadata(capture(contentCaptureSlot))
            mockedAppSdk.loadMetadata(capture(adCaptureSlot))
        }
        JSONAssert.assertEquals(contentExpectedMetaData, contentCaptureSlot.captured, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(adExpectedMetadata, adCaptureSlot.captured, JSONCompareMode.LENIENT)
    }

    @Test
    fun `track for Video Ad Started with type pre-roll and custom settings handled correctly`() {
        mockedNielsenDcrDestination.nielsenDCRSettings?.contentAssetIdPropertyName = "customContentAssetId"
        mockedNielsenDcrDestination.nielsenDCRSettings?.adAssetIdPropertyName = "customAdAssetId"
        mockedNielsenDcrDestination.nielsenDCRSettings?.clientIdPropertyName = "customClientId"
        mockedNielsenDcrDestination.nielsenDCRSettings?.subbrandPropertyName = "customSubbrand"
        mockedNielsenDcrDestination.nielsenDCRSettings?.contentLengthPropertyName = "customLength"
        val sampleEvent = TrackEvent(
            event = "Video Ad Started",
            properties = buildJsonObject {
                put("customAdAssetId", "123456")
                put("type", "pre-roll")
                put("title", "Ad Test Title")
                put("content", buildJsonObject {
                    put("podId", "adSegmentA")
                    put("customLength", 1200)
                    put("load_type", "linear")
                    put("position", 10)
                    put("customContentAssetId", 1234)
                    put("clientId", "testClientId")
                    put("customClientId", "testCustomClientId")
                    put("subbrand", "testSubBrand")
                    put("customSubbrand", "testCustomSubBrand")
                    put("title", "Ad Test Title")
                    put("hasAds", "1")
                    put("segB", "segmentB")
                })
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = buildJsonObject {
                put("nielsen-dcr",  buildJsonObject {
                    put("segB", "segmentB")
                    put("hasAds", true)
                })
            }
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)
        val contentExpectedMetaData = JSONObject()
        contentExpectedMetaData.put("assetid", "1234")
        contentExpectedMetaData.put("type", "content")
        contentExpectedMetaData.put("title", "Ad Test Title")
        contentExpectedMetaData.put("pipmode", "false")
        contentExpectedMetaData.put("segB", "segmentB")
        contentExpectedMetaData.put("clientid", "testCustomClientId")
        contentExpectedMetaData.put("subbrand", "testCustomSubBrand")
        contentExpectedMetaData.put("length", "1200")
        contentExpectedMetaData.put("adloadtype", "1")
        contentExpectedMetaData.put("hasAds", "1")
        contentExpectedMetaData.put("isfullepisode", "n")

        val adExpectedMetadata = JSONObject()
        adExpectedMetadata.put("assetid", "123456")
        adExpectedMetadata.put("type", "preroll")
        adExpectedMetadata.put("title", "Ad Test Title")

        val contentCaptureSlot = slot<JSONObject>()
        val adCaptureSlot = slot<JSONObject>()

        verifyOrder {
            mockedAppSdk.loadMetadata(capture(contentCaptureSlot))
            mockedAppSdk.loadMetadata(capture(adCaptureSlot))
        }
        JSONAssert.assertEquals(contentExpectedMetaData, contentCaptureSlot.captured, JSONCompareMode.LENIENT)
        JSONAssert.assertEquals(adExpectedMetadata, adCaptureSlot.captured, JSONCompareMode.LENIENT)
    }

    @Test
    fun `track for Video Ad Completed handled correctly`() {
        val sampleEvent = TrackEvent(
            event = "Video Ad Completed",
            properties = buildJsonObject {
                put("assetId", "123456")
                put("type", "mid-roll")
                put("title", "Ad Test Title")
                put("totalLength", 1000)
                put("position", 10)
                put("podId", "adSegmentb")
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            integrations = emptyJsonObject
            context = emptyJsonObject
        }
        mockedNielsenDcrDestination.track(sampleEvent)
        verify{mockedAppSdk.stop()}
    }
}