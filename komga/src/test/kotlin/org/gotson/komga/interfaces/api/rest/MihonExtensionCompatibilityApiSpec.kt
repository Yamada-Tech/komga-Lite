package org.gotson.komga.interfaces.api.rest

import org.assertj.core.api.Assertions.assertThat
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.makeBook
import org.gotson.komga.domain.model.makeLibrary
import org.gotson.komga.domain.model.makeSeries
import org.gotson.komga.domain.persistence.KomgaUserRepository
import org.gotson.komga.domain.persistence.LibraryRepository
import org.gotson.komga.domain.persistence.SeriesRepository
import org.gotson.komga.domain.service.KomgaUserLifecycle
import org.gotson.komga.domain.service.LibraryLifecycle
import org.gotson.komga.domain.service.SeriesLifecycle
import org.hamcrest.core.IsNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Guards the API shape expected by the Mihon/Tachiyomi "Komga" extension
 * (https://github.com/keiyoushi/extensions-source, `src/all/komga`).
 *
 * The extension deserializes responses with kotlinx.serialization using **required** properties, so
 * dropping a field that upstream Komga exposes makes the whole series listing fail with
 * `MissingFieldException: Required value 'publisher' is missing`. This fork removed publisher /
 * language / ageRating from the series metadata (421d6344), hence the placeholder fields on
 * SeriesMetadataDto and the empty `GET /api/v1/publishers` endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc(printOnlyOnFailure = false)
class MihonExtensionCompatibilityApiSpec(
  @Autowired private val seriesRepository: SeriesRepository,
  @Autowired private val seriesLifecycle: SeriesLifecycle,
  @Autowired private val libraryRepository: LibraryRepository,
  @Autowired private val libraryLifecycle: LibraryLifecycle,
  @Autowired private val userRepository: KomgaUserRepository,
  @Autowired private val userLifecycle: KomgaUserLifecycle,
  @Autowired private val mockMvc: MockMvc,
) {
  private val library = makeLibrary(id = "1")

  @BeforeAll
  fun `setup library`() {
    libraryRepository.insert(library)
    userRepository.insert(KomgaUser("user@example.org", "", id = "1"))
  }

  @AfterAll
  fun teardown() {
    userRepository.findAll().forEach {
      userLifecycle.deleteUser(it)
    }
    libraryRepository.findAll().forEach {
      libraryLifecycle.deleteLibrary(it)
    }
  }

  @AfterEach
  fun `clear repository`() {
    seriesLifecycle.deleteMany(seriesRepository.findAll())
  }

  private fun createSeries() =
    makeSeries(name = "series", libraryId = library.id).let { series ->
      seriesLifecycle.createSeries(series).also { created ->
        seriesLifecycle.addBooks(created, listOf(makeBook("1", libraryId = library.id)))
      }
    }

  @Test
  @WithMockCustomUser
  fun `given series when listing series then metadata carries the upstream placeholder fields`() {
    createSeries()

    // Each of these is a required (non-nullable, no default) property on the extension's
    // SeriesMetadataDto, so a single missing one breaks deserialization of the whole page.
    mockMvc
      .get("/api/v1/series")
      .andExpect {
        status { isOk() }
        jsonPath("$.content[0].metadata.publisher") { value("") }
        jsonPath("$.content[0].metadata.publisherLock") { value(false) }
        jsonPath("$.content[0].metadata.ageRating") { value(IsNull.nullValue()) }
        jsonPath("$.content[0].metadata.ageRatingLock") { value(false) }
        jsonPath("$.content[0].metadata.language") { value("") }
        jsonPath("$.content[0].metadata.languageLock") { value(false) }
      }
  }

  @Test
  @WithMockCustomUser
  fun `given series when getting series by id then metadata carries the upstream placeholder fields`() {
    val createdSeries = createSeries()

    mockMvc
      .get("/api/v1/series/${createdSeries.id}")
      .andExpect {
        status { isOk() }
        jsonPath("$.metadata.publisher") { value("") }
        jsonPath("$.metadata.publisherLock") { value(false) }
        jsonPath("$.metadata.ageRating") { value(IsNull.nullValue()) }
        jsonPath("$.metadata.ageRatingLock") { value(false) }
        jsonPath("$.metadata.language") { value("") }
        jsonPath("$.metadata.languageLock") { value(false) }
      }
  }

  @Test
  @WithMockCustomUser
  fun `given publishers were removed when listing publishers then an empty list is returned`() {
    // The extension fetches this inside coroutineScope, so a 404 here also drops the
    // libraries/genres/tags/collections/authors filters.
    val result =
      mockMvc
        .get("/api/v1/publishers")
        .andExpect { status { isOk() } }
        .andReturn()

    assertThat(result.response.contentAsString).isEqualTo("[]")
  }

  @Test
  @WithMockCustomUser
  fun `given extension fetches its filter data when calling each endpoint then all are reachable`() {
    // Mirrors the extension's fetchFilterData() plus the login probe (logIn()).
    listOf(
      "/api/v1/libraries",
      "/api/v1/collections?unpaged=true",
      "/api/v1/genres",
      "/api/v1/tags",
      "/api/v1/publishers",
      "/api/v1/authors",
    ).forEach { url ->
      mockMvc.get(url).andExpect { status { isOk() } }
    }
  }

  @Test
  @WithMockCustomUser
  fun `given extension reads a page wrapper when listing collections unpaged then the wrapper fields are present`() {
    // The extension parses PageWrapperDto, whose wrapper fields are all required.
    mockMvc
      .get("/api/v1/collections") {
        param("unpaged", "true")
      }.andExpect {
        status { isOk() }
        jsonPath("$.content") { isEmpty() }
        jsonPath("$.empty") { value(true) }
        jsonPath("$.first") { value(true) }
        jsonPath("$.last") { value(true) }
        jsonPath("$.number") { value(0) }
        jsonPath("$.numberOfElements") { value(0) }
        // unpaged でも size は既定ページサイズを返すので、存在のみ検証する
        jsonPath("$.size") { exists() }
        jsonPath("$.totalElements") { value(0) }
        jsonPath("$.totalPages") { value(0) }
      }
  }

  @Test
  @WithMockCustomUser
  fun `given extension browses series when using its query parameters then the page is returned`() {
    createSeries()

    // Exactly what the extension's getPopularManga()/getLatestUpdates() send.
    mockMvc
      .get("/api/v1/series") {
        param("search", "")
        param("deleted", "false")
        param("page", "0")
        param("sort", "lastModifiedDate,desc")
      }.andExpect {
        status { isOk() }
        jsonPath("$.content.length()") { value(1) }
        jsonPath("$.content[0].metadata.title") { value("series") }
      }
  }
}
