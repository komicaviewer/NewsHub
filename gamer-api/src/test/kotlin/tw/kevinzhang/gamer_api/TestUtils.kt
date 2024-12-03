package tw.kevinzhang.gamer_api

import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

fun loadFile(path: String): String? {
    var ele: String? = null
    try {
        val f = File(path)
        val read = InputStreamReader(FileInputStream(f), "UTF-8")
        val builder = StringBuilder()
        var ch: Int
        while (read.read().also { ch = it } != -1) {
            builder.append(ch.toChar())
        }
        ele = builder.toString()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return ele
}