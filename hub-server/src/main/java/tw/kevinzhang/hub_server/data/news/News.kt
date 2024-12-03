package tw.kevinzhang.hub_server.data.news

import tw.kevinzhang.hub_server.data.Paragraph

interface News {
    val threadUrl: String
    val boardUrl: String
    val content: List<Paragraph>
}