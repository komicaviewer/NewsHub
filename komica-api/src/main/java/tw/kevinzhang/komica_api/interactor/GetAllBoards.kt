package tw.kevinzhang.komica_api.interactor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.kevinzhang.komica_api.model.boards

class GetAllBoards {
    suspend fun invoke() = withContext(Dispatchers.IO) {
        boards()
    }
}