package tw.kevinzhang.komica_api.request._2cat

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import tw.kevinzhang.komica_api.isZeroOrNull
import tw.kevinzhang.komica_api.model.KBoard
import tw.kevinzhang.komica_api.request.BoardRequestBuilder
import tw.kevinzhang.komica_api.request.ThreadRequestBuilder
import tw.kevinzhang.komica_api.toKBoard

class _2catRequestBuilder : BoardRequestBuilder, ThreadRequestBuilder {
    private lateinit var builder: HttpUrl.Builder

    override fun setUrl(url: HttpUrl): _2catRequestBuilder {
        this.builder = url.newBuilder()
        return this
    }

    fun setBoard(board: KBoard): _2catRequestBuilder {
        setUrl(board.url.toHttpUrl())
        return this
    }

    fun setRes(res: String?): _2catRequestBuilder {
        return if (res == null) removeQuery("res")
        else addQuery("res", res)
    }

    private fun addQuery(queryName: String, value: String): _2catRequestBuilder {
        if (hasQuery(queryName))
            removeQuery(queryName)
        builder = builder.addQueryParameter(queryName, value)
        return this
    }

    private fun hasQuery(queryName: String): Boolean {
        return builder.build().queryParameter(queryName).isNullOrBlank().not()
    }

    private fun removeQuery(queryName: String): _2catRequestBuilder {
        if (hasQuery(queryName))
            builder = builder.removeAllQueryParameters(queryName)
        return this
    }

    fun setFragment(reply: String?): _2catRequestBuilder {
        return if (reply == null) removeFragment()
        else addFragment(reply)
    }

    private fun addFragment(value: String): _2catRequestBuilder {
        if (hasFragment())
            removeFragment()
        builder = builder.fragment(value)
        return this
    }

    private fun hasFragment(): Boolean {
        return builder.build().fragment.isNullOrBlank().not()
    }

    private fun removeFragment(): _2catRequestBuilder {
        if (hasFragment())
            builder = builder.fragment(null)
        return this
    }

    override fun setPage(page: Int?): _2catRequestBuilder {
        builder = builder
            .apply {
                if (page.isZeroOrNull()) {
                    removeQuery("page")
                } else {
                    val _httpUrl = builder.build()
                    val extra =
                        _httpUrl.pathSegments - _httpUrl.toKBoard().url.toHttpUrl().pathSegments
                    if (extra.isEmpty()) {
                        addQuery("page", "$page")
                    } else {
                        setQueryParameter("page", "$page")
                    }
                }
            }
        return this
    }

    override fun build(): Request {
        return Request.Builder()
            .url(builder.build())
            .build()
    }
}