package org.gotson.komga.interfaces.api.rest.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDate
import java.time.LocalDateTime

data class SeriesDto(
  val id: String,
  val libraryId: String,
  val name: String,
  val url: String,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  val created: LocalDateTime,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  val lastModified: LocalDateTime,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  val fileLastModified: LocalDateTime,
  val booksCount: Int,
  val booksReadCount: Int,
  val booksUnreadCount: Int,
  val booksInProgressCount: Int,
  val metadata: SeriesMetadataDto,
  val booksMetadata: BookMetadataAggregationDto,
  val deleted: Boolean,
  val oneshot: Boolean,
)

fun SeriesDto.restrictUrl(restrict: Boolean) = if (restrict) copy(url = "") else this

data class SeriesMetadataDto(
  val status: String,
  val statusLock: Boolean,
  val title: String,
  val titleLock: Boolean,
  val titleSort: String,
  val titleSortLock: Boolean,
  val summary: String,
  val summaryLock: Boolean,
  val readingDirection: String,
  val readingDirectionLock: Boolean,
  // --- Mihon/Tachiyomi 拡張（Keiyoushi repository）向けの後方互換スタブ ---
  // 拡張側は kotlinx.serialization でこれら upstream フィールドを「必須」として直列化するため、
  // 応答から消すと MissingFieldException でシリーズ一覧が丸ごと表示不能になる
  // （Required value 'publisher' is missing 等）。DB 列は 421d6344 で削除済みなので
  // 空のプレースホルダとして返す。削除する場合は Mihon 拡張側の dto を確認すること。
  val publisher: String = "",
  val publisherLock: Boolean = false,
  val ageRating: Int? = null,
  val ageRatingLock: Boolean = false,
  val language: String = "",
  val languageLock: Boolean = false,
  val genres: Set<String>,
  val genresLock: Boolean,
  val tags: Set<String>,
  val tagsLock: Boolean,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  val created: LocalDateTime,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  val lastModified: LocalDateTime,
)

data class BookMetadataAggregationDto(
  val authors: List<AuthorDto> = emptyList(),
  val notableEntities: List<NotableEntityDto>,
  val tags: Set<String> = emptySet(),
  @JsonFormat(pattern = "yyyy-MM-dd")
  val releaseDate: LocalDate?,
  val summary: String,
  val summaryNumber: String,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  val created: LocalDateTime,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  val lastModified: LocalDateTime,
)
