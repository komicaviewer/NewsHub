package tw.kevinzhang.gamer_api.interactor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.gildor.coroutines.okhttp.await
import tw.kevinzhang.gamer_api.AuthRequiredException
import tw.kevinzhang.gamer_api.model.GNews
import tw.kevinzhang.gamer_api.parser.BoardParser
import tw.kevinzhang.gamer_api.parser.NewsParser
import tw.kevinzhang.gamer_api.request.RequestBuilderImpl
import java.util.logging.Logger

private val logger = Logger.getLogger("GetAllNews")

class GetAllNews(
    private val client: OkHttpClient,
) {
    /**
     * 取得指定 Board 底下的 News，這些 News 與 Thread 不同，News 只是 Thread 的簡單資訊，而 Thread 內包含了整個討論串的原 PO 及回文 (RePost)
     */
    suspend fun invoke(req: Request): List<GNews> = withContext(Dispatchers.IO) {
        val url = req.url.toString()
        logger.info("→ GET $url")
        val t0 = System.currentTimeMillis()
        val board = GetBoard().invoke(url)
        val res = client.newCall(req).await()
        val ms = System.currentTimeMillis() - t0
        val finalUrl = res.request.url.toString()
        if (finalUrl.contains("loginPage")) {
            logger.warning("← AUTH_REDIRECT $finalUrl (${ms}ms)")
            throw AuthRequiredException()
        }
        logger.info("← ${res.code} $url (${ms}ms)")
        BoardParser(NewsParser(), RequestBuilderImpl()).parse(res.body!!, req)
    }
}
