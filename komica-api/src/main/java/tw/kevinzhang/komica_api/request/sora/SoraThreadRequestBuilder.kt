package tw.kevinzhang.komica_api.request.sora

import tw.kevinzhang.komica_api.addFilename
import tw.kevinzhang.komica_api.isFile
import tw.kevinzhang.komica_api.model.KBoard
import tw.kevinzhang.komica_api.request.ThreadRequestBuilder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class SoraThreadRequestBuilder: ThreadRequestBuilder {
    private lateinit var builder: HttpUrl.Builder

    override fun setUrl(url: HttpUrl): SoraThreadRequestBuilder {
        this.builder = if (!url.isFile("php")) {
            url.newBuilder().addFilename("pixmicat", "php")
        } else {
            url.newBuilder()
        }
        return this
    }

    fun setBoard(board: KBoard): SoraThreadRequestBuilder {
        setUrl(board.url.toHttpUrl())
        return this
    }

    fun setRes(res: String?): SoraThreadRequestBuilder {
        return if(res == null) removeQuery("res")
        else addQuery("res", res)
    }

    private fun addQuery(queryName: String, value: String): SoraThreadRequestBuilder {
        if (hasQuery(queryName))
            removeQuery(queryName)
        builder = builder.addQueryParameter(queryName, value)
        return this
    }

    private fun hasQuery(queryName: String): Boolean {
        return builder.build().queryParameter(queryName).isNullOrBlank().not()
    }

    private fun removeQuery(queryName: String): SoraThreadRequestBuilder {
        if(hasQuery(queryName))
            builder = builder.removeAllQueryParameters(queryName)
        return this
    }

    fun setFragment(reply: String?): SoraThreadRequestBuilder {
        return if(reply == null) removeFragment()
        else addFragment(reply)
    }

    private fun addFragment(value: String): SoraThreadRequestBuilder {
        if (hasFragment())
            removeFragment()
        builder = builder.fragment(value)
        return this
    }

    private fun hasFragment(): Boolean {
        return builder.build().fragment.isNullOrBlank().not()
    }

    private fun removeFragment(): SoraThreadRequestBuilder {
        if(hasFragment())
            builder = builder.fragment(null)
        return this
    }

    override fun build(): Request {
        return Request.Builder()
            .url(builder.build())
            .build()
    }
}