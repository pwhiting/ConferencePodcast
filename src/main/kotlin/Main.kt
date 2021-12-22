

import com.rometools.rome.feed.synd.*
import com.rometools.rome.io.SyndFeedOutput
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.FileWriter
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import java.net.URI


// aws s3 cp eng s3://pete.dugg.in/c --acl public-read --content-type 'application/rss+xml'

fun main() {
    // This is the URL to scrape for content. It changes from time to time.
    // You need to be able to scrape the year/month string from it. If not, hard code dateStr too
    val url = URI("https://www.churchofjesuschrist.org/study/general-conference/2021/10")

    // This is the list of languages which will go into this feed.
    val languages = listOf<String>("eng","fra")
    //val languages = listOf<String>("eng")

    // This shouldn't need to change - but it is where we publish the feed
    val myFeedURL = "http://pete.dugg.in/"

    // This changes from time to time, as the church updates the website. It is the class attribute associated
    // with talks.
    val classStr = "item-3cCP7"

    // This can be calculated currently - but if not calculatable, replace with hardcoded value.
    val dateStr= url.path.substringAfter("general-conference/")

    // Title the feed using the title of the last language
    val feed = SyndFeedImpl().apply {
        feedType = "rss_2.0"
        title = Jsoup.connect("$url?lang="+languages.last())?.get()?.title() ?: "General Conference"
        link = myFeedURL + languages.last()
        description = title
        publishedDate = makeDate("$dateStr/01", 0,0,0)
    }

    // Iterate through each language and grab all the hrefs to mp3s

    languages.withIndex().forEach { (index,lang) ->
        Jsoup.connect("$url?lang=$lang").get()
            .select("""a[class="$classStr"]""")
            .map { it.attr("href") }
            .parallelStream()
            .map { getEntry(url.scheme+"://"+url.authority+"/$it", index) }
            .filter { it != null }
            .forEach { feed.entries.add(it) }
    }
    FileWriter(languages.last()).use { writer -> SyndFeedOutput().output(feed, writer) }
}

fun makeDate(date :String,hour: Int,min :Int,sec :Int) :Date {
    return Date.from(LocalDate.parse(date.replace('/','-'))
        .atTime(hour,min,sec).atZone(ZoneId.systemDefault()).toInstant())
}
fun getEntry(mp3url: String,seconds :Int) : SyndEntryImpl? {
    val doc: Document
    try {
        doc = Jsoup.connect(mp3url).get()
        val dateStr = """(\d\d\d\d/\d\d)""".toRegex().find(mp3url)?.groups?.last()?.value ?: "2000/01"
        val minutes ="""/\d\d/(\d\d)""".toRegex().find(mp3url)?.groups?.last()?.value?.toInt() ?: 0

        val enc = SyndEnclosureImpl().apply {
            type = "audio/mpeg"
            url = doc.selectFirst("a:contains(mp3)")!!.attr("href").toString()
        }
        if ( "auditing-department".toRegex().containsMatchIn(enc.url))  { return null }
        if ( "sustaining-of-general".toRegex().containsMatchIn(enc.url))  { return null }

        val entry = SyndEntryImpl().apply {
            //  title = doc.title().removeSuffix(" -")
            author = doc?.selectFirst("""p[class="author-name"]""")?.text() ?: ""
            title = doc?.title()
            publishedDate = makeDate("$dateStr/01",0,60-minutes,seconds)
            link = enc.url
            enclosures = listOf<SyndEnclosure>(enc)
        }
        return entry }
    catch (e: Exception){ return null }
}
