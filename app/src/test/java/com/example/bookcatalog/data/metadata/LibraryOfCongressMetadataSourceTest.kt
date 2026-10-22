package com.example.bookcatalog.data.metadata

import com.example.bookcatalog.data.remote.LibraryOfCongressApi
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryOfCongressMetadataSourceTest {
    @Test
    fun mapsModsRecordToMetadata() {
        val queries = mutableListOf<String>()
        val api = fakeApi(queries, response(SILENT_PATIENT_RECORD))

        val result = kotlinx.coroutines.runBlocking {
            LibraryOfCongressMetadataSource(api).fetch("9781250301697")
        }

        requireNotNull(result)
        assertEquals(listOf("bath.isbn=9781250301697"), queries)
        assertEquals("The silent patient", result.title)
        assertEquals(listOf("Alex Michaelides"), result.authors)
        assertEquals("Celadon Books", result.publisher)
        assertEquals("2019", result.publishedDate)
        assertEquals(325, result.pageCount)
        assertEquals("en", result.language)
        assertEquals(
            listOf("Family violence -- Fiction", "London (England) -- Fiction"),
            result.subjects,
        )
        assertEquals(
            listOf("FICTION / Thrillers / Psychological", "FICTION / Thrillers / Suspense"),
            result.bisacSubjects,
        )
        assertEquals("Alicia Berenson's life is seemingly perfect.", result.description)
    }

    @Test
    fun convertsInvertedNamesAndSkipsEditors() {
        val record = """
            <mods xmlns="http://www.loc.gov/mods/v3">
              <titleInfo><title>Collected stories /</title></titleInfo>
              <name type="personal" usage="primary">
                <namePart>Fitzgerald, F. Scott (Francis Scott),</namePart>
                <namePart type="date">1896-1940</namePart>
                <role><roleTerm type="text">author</roleTerm></role>
              </name>
              <name type="personal">
                <namePart>West, James L. W.,</namePart>
                <role><roleTerm type="text">editor</roleTerm></role>
              </name>
              <name type="personal">
                <namePart>King, Martin Luther, Jr.,</namePart>
                <role><roleTerm type="text">author</roleTerm></role>
              </name>
            </mods>
        """.trimIndent()

        val result = kotlinx.coroutines.runBlocking {
            LibraryOfCongressMetadataSource(fakeApi(response = response(record))).fetch("9780743273565")
        }

        requireNotNull(result)
        assertEquals("Collected stories", result.title)
        assertEquals(listOf("F. Scott Fitzgerald", "Martin Luther King, Jr."), result.authors)
    }

    @Test
    fun returnsNullWhenNoRecordsMatch() {
        val result = kotlinx.coroutines.runBlocking {
            LibraryOfCongressMetadataSource(fakeApi(response = response(""))).fetch("9780385533225")
        }

        assertNull(result)
    }

    private fun fakeApi(
        queries: MutableList<String> = mutableListOf(),
        response: String,
    ) = object : LibraryOfCongressApi {
        override suspend fun searchRetrieve(
            query: String,
            version: String,
            operation: String,
            maximumRecords: Int,
            recordSchema: String,
        ) = response.also { queries += query }.toResponseBody()
    }

    private fun response(records: String) = """
        <zs:searchRetrieveResponse xmlns:zs="http://www.loc.gov/zing/srw/">
          <zs:records><zs:record><zs:recordData>$records</zs:recordData></zs:record></zs:records>
        </zs:searchRetrieveResponse>
    """.trimIndent()

    private companion object {
        val SILENT_PATIENT_RECORD = """
            <mods xmlns="http://www.loc.gov/mods/v3" version="3.8">
              <titleInfo>
                <nonSort xml:space="preserve">The  </nonSort>
                <title>silent patient</title>
              </titleInfo>
              <name type="personal" usage="primary">
                <namePart>Michaelides, Alex,</namePart>
                <namePart type="date">1977-</namePart>
                <role><roleTerm type="text">author</roleTerm></role>
              </name>
              <originInfo>
                <dateIssued encoding="marc">2019</dateIssued>
              </originInfo>
              <originInfo eventType="publication">
                <place><placeTerm type="text">New York :</placeTerm></place>
                <agent>
                  <namePart>Celadon Books,</namePart>
                  <role><roleTerm>publisher</roleTerm></role>
                </agent>
                <dateIssued>2019.</dateIssued>
              </originInfo>
              <language><languageTerm authority="iso639-2b" type="code">eng</languageTerm></language>
              <physicalDescription><extent>325 pages ; 25 cm</extent></physicalDescription>
              <abstract type="Summary">Alicia Berenson's life is seemingly perfect.</abstract>
              <subject authority="lcsh"><topic>Family violence</topic><genre>Fiction</genre></subject>
              <subject authority="bisacsh"><topic>FICTION / Thrillers / Psychological</topic></subject>
              <subject authority="bisacsh"><topic>FICTION / Thrillers / Suspense.</topic></subject>
              <subject authority="lcsh"><geographic>London (England)</geographic><genre>Fiction</genre></subject>
              <identifier type="isbn">9781250301697</identifier>
              <identifier type="isbn" invalid="yes">9781250230782</identifier>
            </mods>
        """.trimIndent()
    }
}
