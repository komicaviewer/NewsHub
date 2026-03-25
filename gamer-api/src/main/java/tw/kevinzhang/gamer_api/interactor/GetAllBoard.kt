package tw.kevinzhang.gamer_api.interactor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.kevinzhang.gamer_api.model.GBoard

class GetAllBoard {
    suspend fun invoke() = withContext(Dispatchers.IO) {
        listOf(GBoard("電腦應用綜合討論", "https://forum.gamer.com.tw/B.php?bsn=60030"))
    }
}