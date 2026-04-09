package tw.kevinzhang.komica_api.interactor

import tw.kevinzhang.komica_api.model.KBoard
import tw.kevinzhang.komica_api.request.ThreadRequestBuilder
import tw.kevinzhang.komica_api.request.ThreadSummariesRequestBuilder
import tw.kevinzhang.komica_api.request._2cat._2catRequestBuilder
import tw.kevinzhang.komica_api.request.komica2.Komica2ThreadRequestBuilder
import tw.kevinzhang.komica_api.request.komica2.Komica2ThreadSummariesRequestBuilder
import tw.kevinzhang.komica_api.request.sora.SoraThreadRequestBuilder
import tw.kevinzhang.komica_api.request.sora.SoraThreadSummariesRequestBuilder

class GetRequestBuilder {
    fun forBoard(board: KBoard): ThreadSummariesRequestBuilder {
        return when (board) {
            is KBoard.Sora ->
                SoraThreadSummariesRequestBuilder().setBoard(board)
            is KBoard._2catSora ->
                SoraThreadSummariesRequestBuilder().setBoard(board)
            is KBoard._2cat ->
                _2catRequestBuilder().setBoard(board)
            is KBoard.Komica2 ->
                Komica2ThreadSummariesRequestBuilder().setBoard(board)
            else ->
                throw NotImplementedError("BoardRequestBuilder of $board not implemented yet")
        }
    }

    fun forThread(board: KBoard): ThreadRequestBuilder {
        return when (board) {
            is KBoard.Sora ->
                SoraThreadRequestBuilder().setBoard(board)
            is KBoard._2catSora ->
                SoraThreadRequestBuilder().setBoard(board)
            is KBoard._2cat ->
                _2catRequestBuilder().setBoard(board)
            is KBoard.Komica2 ->
                Komica2ThreadRequestBuilder().setBoard(board)
            else ->
                throw NotImplementedError("ThreadRequestBuilder of $board not implemented yet")
        }
    }
}