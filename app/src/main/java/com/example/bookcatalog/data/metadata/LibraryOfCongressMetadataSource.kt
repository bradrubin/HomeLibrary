package com.example.bookcatalog.data.metadata

import com.example.bookcatalog.data.remote.LibraryOfCongressApi
import com.example.bookcatalog.domain.BookFormat
import com.example.bookcatalog.domain.IsbnUtils
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

class LibraryOfCongressMetadataSource(
    private val api: LibraryOfCongressApi,
) : BookMetadataSource {
    override val name = "LibraryOfCongress"

    override suspend fun fetch(isbn13: String): BookMetadata? =
        api.searchRetrieve("bath.isbn=$isbn13").byteStream().use { stream ->
            ModsRecordParser.parse(stream, isbn13)
        }
}

/** Maps the MODS records of a Library of Congress SRU response to [BookMetadata]. */
internal object ModsRecordParser {
    private const val MODS_NAMESPACE = "http://www.loc.gov/mods/v3"
    private const val MAX_BISAC_SUBJECTS = 3
    private val pageCountPattern = Regex("""(\d+)\s*(?:pages|p\.)""")
    private val yearPattern = Regex("""\d{4}""")
    private val nameSuffixes = setOf("Jr.", "Sr.", "II", "III", "IV")
    private val abbreviations = nameSuffixes + setOf("Inc.", "Co.", "Ltd.", "Corp.", "Bros.")

    fun parse(stream: InputStream, isbn13: String): BookMetadata? {
        val document = try {
            documentBuilderFactory().newDocumentBuilder().parse(InputSource(stream))
        } catch (exception: SAXException) {
            throw IOException("Could not parse the Library of Congress response.", exception)
        } catch (exception: ParserConfigurationException) {
            throw IOException("Could not parse the Library of Congress response.", exception)
        }
        val records = document.getElementsByTagNameNS(MODS_NAMESPACE, "mods").elements()
        val record = records.firstOrNull { it.hasValidIsbn(isbn13) }
            ?: records.firstOrNull()
            ?: return null
        return record.toMetadata().copy(format = record.isbnQualifierFormat(isbn13)).takeIf(BookMetadata::hasDetails)
    }

    private fun documentBuilderFactory() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        // Not every platform parser supports this feature; Android's never loads external DTDs.
        try {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (_: ParserConfigurationException) {
        }
    }

    private fun Element.toMetadata(): BookMetadata {
        val titleInfo = children("titleInfo").firstOrNull { !it.hasAttribute("type") }
        val title = titleInfo?.let { info ->
            val nonSort = info.children("nonSort").firstOrNull()?.textContent.orEmpty()
            val mainTitle = info.children("title").firstOrNull()?.textContent.orEmpty()
            cleanCatalogText(nonSort.trim() + " " + mainTitle)
        }
        return BookMetadata(
            title = title,
            subtitle = titleInfo?.children("subTitle")?.firstOrNull()?.textContent?.let(::cleanCatalogText),
            authors = authors(),
            publisher = publisher(),
            publishedDate = children("originInfo")
                .flatMap { it.children("dateIssued") }
                .firstNotNullOfOrNull { yearPattern.find(it.textContent)?.value },
            pageCount = children("physicalDescription")
                .flatMap { it.children("extent") }
                .firstNotNullOfOrNull { pageCountPattern.find(it.textContent)?.groupValues?.get(1)?.toIntOrNull() },
            language = children("language")
                .flatMap { it.children("languageTerm") }
                .firstOrNull { it.getAttribute("type") == "code" }
                ?.textContent
                ?.trim(),
            subjects = subjects("lcsh"),
            bisacSubjects = subjects("bisacsh").take(MAX_BISAC_SUBJECTS),
            description = children("abstract").firstOrNull()?.textContent?.trim()?.takeIf(String::isNotBlank),
        )
    }

    private fun Element.hasValidIsbn(isbn13: String): Boolean =
        children("identifier").any { identifier ->
            identifier.getAttribute("type") == "isbn" &&
                identifier.getAttribute("invalid") != "yes" &&
                IsbnUtils.toIsbn13(identifier.textContent.trim().substringBefore(' ')) == isbn13
        }

    /** Catalogers note an edition's binding after its ISBN, as in "9780385533225 (pbk.)". */
    private fun Element.isbnQualifierFormat(isbn13: String): String? =
        children("identifier")
            .filter { it.getAttribute("type") == "isbn" }
            .map { it.textContent.trim() }
            .firstOrNull { IsbnUtils.toIsbn13(it.substringBefore(' ')) == isbn13 }
            ?.substringAfter(' ', "")
            ?.let(BookFormat::fromSource)

    private fun Element.authors(): List<String> =
        children("name")
            .filter { it.getAttribute("type") == "personal" }
            .filter { name ->
                val roles = name.children("role")
                    .flatMap { it.children("roleTerm") }
                    .map { it.textContent.trim().lowercase() }
                if (roles.isEmpty()) name.getAttribute("usage") == "primary" else "author" in roles || "aut" in roles
            }
            .mapNotNull { name ->
                name.children("namePart")
                    .firstOrNull { !it.hasAttribute("type") }
                    ?.textContent
                    ?.let(::naturalOrderName)
            }
            .distinct()

    private fun Element.publisher(): String? {
        val originInfos = children("originInfo")
            .sortedByDescending { it.getAttribute("eventType") == "publication" }
        val agentPublisher = originInfos
            .flatMap { it.children("agent") }
            .firstOrNull { agent ->
                agent.children("role").flatMap { it.children("roleTerm") }
                    .any { it.textContent.trim().equals("publisher", ignoreCase = true) }
            }
            ?.children("namePart")
            ?.firstOrNull()
            ?.textContent
        return (agentPublisher ?: originInfos.flatMap { it.children("publisher") }.firstOrNull()?.textContent)
            ?.let(::cleanCatalogText)
    }

    private fun Element.subjects(authority: String): List<String> =
        children("subject")
            .filter { it.getAttribute("authority") == authority }
            .mapNotNull { subject ->
                subject.childElements()
                    .mapNotNull { cleanCatalogText(it.textContent) }
                    .takeIf(List<String>::isNotEmpty)
                    ?.joinToString(" -- ")
            }
            .distinct()

    /** Turns a cataloged "Fitzgerald, F. Scott (Francis Scott), 1896-1940" name into "F. Scott Fitzgerald". */
    private fun naturalOrderName(value: String): String? {
        val cleaned = cleanCatalogText(value.replace(Regex("""\s*\([^)]*\)"""), "")) ?: return null
        val parts = cleaned.split(", ")
        if (parts.size == 1) return cleaned
        val suffixes = parts.drop(2).filter { it in nameSuffixes }
        return (listOf("${parts[1]} ${parts[0]}") + suffixes).joinToString(", ")
    }

    /** Strips the trailing ISBD punctuation that catalog records leave on field values. */
    private fun cleanCatalogText(value: String): String? {
        var text = value.replace(Regex("""\s+"""), " ").trim()
        while (true) {
            val trimmed = text.removeSuffix(",").removeSuffix(";").removeSuffix(":").removeSuffix("/").trimEnd()
            if (trimmed == text) break
            text = trimmed
        }
        val lastWord = text.substringAfterLast(' ')
        if (text.endsWith('.') && lastWord.length > 2 && lastWord !in abbreviations) {
            text = text.dropLast(1)
        }
        return text.takeIf(String::isNotBlank)
    }

    private fun org.w3c.dom.NodeList.elements(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }

    private fun Element.childElements(): List<Element> = childNodes.elements()

    private fun Element.children(localName: String): List<Element> =
        childElements().filter { it.namespaceURI == MODS_NAMESPACE && it.localName == localName }
}
