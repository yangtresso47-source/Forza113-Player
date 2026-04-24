package com.kuqforza.data.remote.xtream

import com.google.common.truth.Truth.assertThat
import com.kuqforza.data.remote.dto.XtreamCategory
import com.kuqforza.data.remote.dto.XtreamSeriesItem
import com.kuqforza.data.remote.dto.XtreamStream
import com.kuqforza.data.remote.dto.XtreamVodMovieData
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test

class XtreamDtoParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `xtream category decodes is_adult from provider payload`() {
        val category = json.decodeFromString<XtreamCategory>(
            """
            {
              "category_id": "28",
              "category_name": "|+18| ADULTS LIVE",
              "is_adult": "1"
            }
            """.trimIndent()
        )

        assertThat(category.isAdult).isTrue()
    }

    @Test
    fun `xtream stream and vod payloads decode nullable is_adult`() {
        val stream = json.decodeFromString<XtreamStream>(
            """
            {
              "name": "Adult Channel",
              "stream_id": 99,
              "is_adult": 1
            }
            """.trimIndent()
        )
        val vod = json.decodeFromString<XtreamVodMovieData>(
            """
            {
              "stream_id": 123,
              "name": "Adult Movie",
              "is_adult": "true"
            }
            """.trimIndent()
        )

        assertThat(stream.isAdult).isTrue()
        assertThat(vod.isAdult).isTrue()
    }

    @Test
    fun `xtream series payload decodes false and missing is_adult safely`() {
        val explicitFalse = json.decodeFromString<XtreamSeriesItem>(
            """
            {
              "series_id": 77,
              "name": "Series",
              "is_adult": "0"
            }
            """.trimIndent()
        )
        val missing = json.decodeFromString<XtreamSeriesItem>(
            """
            {
              "series_id": 78,
              "name": "Series Two"
            }
            """.trimIndent()
        )

        assertThat(explicitFalse.isAdult).isFalse()
        assertThat(missing.isAdult).isNull()
    }
}