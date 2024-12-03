package tw.kevinzhang.gamer_api.parser

import com.google.gson.Gson
import okhttp3.Request
import okhttp3.ResponseBody
import tw.kevinzhang.gamer_api.model.GComment
import tw.kevinzhang.gamer_api.request.RequestBuilder

class CommentListParser(
    private val requestBuilder: RequestBuilder,
): Parser<List<GComment>> {
    override fun parse(body: ResponseBody, req: Request): List<GComment> {
        val jsonString = body.string()
        val gson = Gson()
        val map = gson.fromJson(jsonString, Map::class.java)
        return map.mapNotNull { (_, value) ->
            try {
                gson.fromJson(gson.toJson(value), CommentRes::class.java).toGComment()
            } catch (e: Exception) {
                null
            }
        }
    }

    private data class CommentRes(
        val bsn: String,
        val sn: String,
        val userid: String,
        val comment: String,
        val gp: String,
        val bp: String,
        val wtime: String,
        val mtime: String,
        val delreason: String,
        val state: String,
        val floor: Int,
        val type: String,
        val content: String,
        val snB: String,
        val time: String,
        val nick: String,
    )

    private fun CommentRes.toGComment(): GComment {
        return GComment(
            bsn = bsn,
            sn = sn,
            userId = userid,
            comment = comment,
            gp = gp,
            bp = bp,
            wtime = wtime,
            mtime = mtime,
            delreason = delreason,
            state = state,
            floor = floor,
            type = type,
            content = content,
            snB = snB,
            time = time,
            nick = nick,
        )
    }
}