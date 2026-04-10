package tw.kevinzhang.extensions_builtin

import tw.kevinzhang.komica_api.model.KImageInfo
import tw.kevinzhang.komica_api.model.KLink
import tw.kevinzhang.komica_api.model.KParagraph
import tw.kevinzhang.komica_api.model.KQuote
import tw.kevinzhang.komica_api.model.KReplyTo
import tw.kevinzhang.komica_api.model.KText
import tw.kevinzhang.komica_api.model.KVideoInfo
import tw.kevinzhang.extension_api.model.Paragraph as ExtParagraph

fun KParagraph.toExtParagraph(): ExtParagraph = when (this) {
    is KQuote   -> ExtParagraph.Quote(content)
    is KReplyTo -> ExtParagraph.ReplyTo(targetId = targetId, preview = preview)
    is KText    -> ExtParagraph.Text(content)
    is KImageInfo -> ExtParagraph.ImageInfo(thumb, raw)
    is KVideoInfo -> ExtParagraph.VideoInfo(url)
    is KLink    -> ExtParagraph.Link(content)
    else        -> throw IllegalArgumentException("Unknown KParagraph: $this")
}
