package tw.kevinzhang.komica_api.model

sealed class KBoard(val name: String, val url: String) {
    sealed class Sora(name: String, url: String) : KBoard(name, url) {
        object 綜合 : Sora("綜合", "https://gaia.komica.org/00b")
        object 男性角色 : Sora("男性角色", "https://sora.komica.org/38")
        object 短片2 : Sora("短片2", "https://sora.komica.org/69")
        object 寫真 : Sora("寫真", "https://sora.komica.org/16")
        object 女性角色 : Sora("女性角色", "https://sora.komica.org/19")
        object 歡樂惡搞 : Sora("歡樂惡搞", "https://tomo.komica.org/12")
        object Vtuber : Sora("Vtuber", "https://grea.komica.org/74")
        object GIF : Sora("GIF", "https://grea.komica.org/23")
        object 蘿蔔 : Sora("蘿蔔", "https://grea.komica.org/15")
        object 模型 : Sora("模型", "https://grea.komica.org/09")
        object 鋼普拉 : Sora("鋼普拉", "https://grea.komica.org/61")
        object 飲食 : Sora("飲食", "https://grea.komica.org/58")
        object 特攝 : Sora("特攝", "https://grea.komica.org/13")
        object 小說 : Sora("小說", "https://grea.komica.org/35")
        object 遊戲速報 : Sora("遊戲速報", "https://grea.komica.org/27")
        object `奇幻-科幻` : Sora("奇幻/科幻", "https://grea.komica.org/60")
        object 軍武 : Sora("軍武", "https://grea.komica.org/17")
        object 遊戲王 : Sora("遊戲王", "https://grea.komica.org/73")
        object `電腦-消費電子` : Sora("電腦/消費電子", "https://grea.komica.org/37")
        object 中性角色 : Sora("中性角色", "https://grea.komica.org/57")
        object 影視 : Sora("影視", "https://grea.komica.org/04")
        object 塗鴉王國 : Sora("塗鴉王國", "https://grea.komica.org/30")
        object 新聞 : Sora("新聞", "https://grea.komica.org/25")
        object 布袋戲 : Sora("布袋戲", "https://grea.komica.org/46")
        object 紙牌 : Sora("紙牌", "https://grea.komica.org/10")
        object 網路遊戲 : Sora("網路遊戲", "https://grea.komica.org/52")
        object 四格 : Sora("四格", "https://tomo.komica.org/42a")
    }
    sealed class _2catKomica(name: String, url: String) : KBoard(name, url) {
        object 新番實況 : _2catKomica("新番實況", "https://sora.komica.org/78/index.htm")
        object 新番捏他 : _2catKomica("新番捏他", "https://sora.komica.org/79")
        object 漫畫 : _2catKomica("漫畫", "https://sora.komica.org/81/")
        object 動畫 : _2catKomica("動畫", "https://sora.komica.org/80/")
        object 車 : _2catKomica("車", "https://komica.yucie.net/car/")
        object 萌 : _2catKomica("萌", "https://2cat.komica.org/~kirur/img2")
    }
    sealed class _2cat(name: String, url: String) : KBoard(name, url) {
        object 碧藍幻想 : _2cat("碧藍幻想", "https://2cat.org/granblue")
        object 手機遊戲 : _2cat("手機遊戲", "https://2cat.org/handheld")
        object `Azur-Lane` : _2cat("Azur Lane", "https://2cat.org/azurlane")
        object 網頁遊戲 : _2cat("網頁遊戲", "https://2cat.org/webgame2")
    }
    object PAD : KBoard("PAD", "https://phone.mymoe.moe/bazudora")
    object 三次實況 : KBoard("三次實況", "https://yuutan.mymoe.moe/pawahuru")
    object 綜合2 : KBoard("綜合2", "https://alleyneblade.mymoe.moe/queensblade")
    object 少女前線 : KBoard("少女前線", "https://secilia.zawarudo.org/gf")
    object AGA : KBoard("AGA", "https://secilia.zawarudo.org/alicegearaegis")
    object 人外 : KBoard("人外", "https://komica.dbfoxtw.me/jingai")
    object 格鬥遊戲 : KBoard("格鬥遊戲", "https://komica.yucie.net/fight")
    object Idolmaster : KBoard("Idolmaster", "http://anzuchang.com")
    object `3D-STG` : KBoard("3D STG", "https://kagaminerin.org/fps")
    object 魔物獵人 : KBoard("魔物獵人", "http://strange-komica.com/MonsterHunter")
    object `TYPE-MOON` : KBoard("TYPE-MOON", "http://gzone-anime.info/UnitedSites/TypeMoon")
    object `Figure-GK` : KBoard("Figure/GK", "https://vi.anacel.com/fg/vichan/fg")
    object 艦隊收藏 : KBoard("艦隊收藏", "http://acgspace.wsfun.com/kancolle")
}

fun boards() =
    listOf(
        KBoard.Sora.綜合,
        KBoard.Sora.男性角色,
        KBoard.Sora.短片2,
        KBoard.Sora.寫真,
        KBoard.Sora.女性角色,
        KBoard.Sora.歡樂惡搞,
        KBoard.Sora.Vtuber,
        KBoard.Sora.GIF,
        KBoard.Sora.蘿蔔,
        KBoard.Sora.模型,
        KBoard.Sora.鋼普拉,
        KBoard.Sora.蘿蔔,
        KBoard.Sora.飲食,
        KBoard.Sora.特攝,
        KBoard.Sora.小說,
        KBoard.Sora.遊戲速報,
        KBoard.Sora.`奇幻-科幻`,
        KBoard.Sora.軍武,
        KBoard.Sora.遊戲王,
        KBoard.Sora.`電腦-消費電子`,
        KBoard.Sora.中性角色,
        KBoard.Sora.影視,
        KBoard.Sora.塗鴉王國,
        KBoard.Sora.新聞,
        KBoard.Sora.布袋戲,
        KBoard.Sora.紙牌,
        KBoard.Sora.網路遊戲,
        KBoard.Sora.四格,
        KBoard._2catKomica.新番實況,
        KBoard._2catKomica.新番捏他,
        KBoard._2catKomica.漫畫,
        KBoard._2catKomica.動畫,
        KBoard._2catKomica.車,
        KBoard._2catKomica.萌,
        KBoard._2cat.碧藍幻想,
        KBoard._2cat.手機遊戲,
        KBoard._2cat.`Azur-Lane`,
        KBoard.少女前線,
        KBoard.AGA,
        KBoard.人外,
        KBoard.格鬥遊戲,
        KBoard.Idolmaster,
        KBoard.`3D-STG`,
        KBoard.魔物獵人,
        KBoard.`TYPE-MOON`,
        KBoard.`Figure-GK`,
        KBoard.艦隊收藏,
    )