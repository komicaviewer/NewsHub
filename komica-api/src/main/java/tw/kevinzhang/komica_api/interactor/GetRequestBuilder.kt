package tw.kevinzhang.komica_api.interactor

import tw.kevinzhang.komica_api.model.KBoard
import tw.kevinzhang.komica_api.request.ThreadRequestBuilder
import tw.kevinzhang.komica_api.request.ThreadSummariesRequestBuilder
import tw.kevinzhang.komica_api.request._2cat._2catRequestBuilder
import tw.kevinzhang.komica_api.request.sora.SoraThreadRequestBuilder
import tw.kevinzhang.komica_api.request.sora.SoraThreadSummariesRequestBuilder

class GetRequestBuilder {
    fun forBoard(board: KBoard): ThreadSummariesRequestBuilder {
        return when (board) {
            is KBoard.Sora, KBoard.人外, KBoard.格鬥遊戲, KBoard.Idolmaster, KBoard.`3D-STG`, KBoard.魔物獵人, KBoard.`TYPE-MOON` ->
                SoraThreadSummariesRequestBuilder().setBoard(board)
            is KBoard._2catKomica ->
                SoraThreadSummariesRequestBuilder().setBoard(board)
            is KBoard._2cat ->
                _2catRequestBuilder().setBoard(board)
            else ->
                throw NotImplementedError("BoardRequestBuilder of $board not implemented yet")
        }
    }

    fun forThread(board: KBoard): ThreadRequestBuilder {
        return when (board) {
            is KBoard.Sora, KBoard.人外, KBoard.格鬥遊戲, KBoard.Idolmaster, KBoard.`3D-STG`, KBoard.魔物獵人, KBoard.`TYPE-MOON` ->
                SoraThreadRequestBuilder().setBoard(board)
            is KBoard._2catKomica ->
                SoraThreadRequestBuilder().setBoard(board)
            is KBoard._2cat ->
                _2catRequestBuilder().setBoard(board)
            else ->
                throw NotImplementedError("ThreadRequestBuilder of $board not implemented yet")
        }
    }
}