package tw.kevinzhang.komica_api.model

sealed class KBoard(val name: String, val url: String) {
    sealed class Sora(name: String, url: String) : KBoard(name, url) {
        // 連線板
        object 綜合 : Sora("綜合", "https://gita.komica1.org/00b/pixmicat.php")
        object 掛圖 : Sora("掛圖", "https://iris.komica1.org/64/pixmicat.php")
        object 政治 : Sora("政治", "https://iris.komica1.org/67/pixmicat.php")
        object 模型 : Sora("模型", "https://gaia.komica1.org/09/pixmicat.php")
        object 歡樂惡搞 : Sora("歡樂惡搞", "https://iris.komica1.org/12/pixmicat.php")
        object 特攝 : Sora("特攝", "https://gaia.komica1.org/13/pixmicat.php")
        object 蘿蔔 : Sora("蘿蔔", "https://gaia.komica1.org/15/pixmicat.php")
        object 軍武 : Sora("軍武", "https://gaia.komica1.org/17/pixmicat.php")

        // 連線二板
        object 生活消費 :
            Sora("生活消費", "http://gzone-anime.info/UnitedSites/shopping/pixmicat.php")

        object 藝術 : Sora("藝術", "http://gzone-anime.info/UnitedSites/art/pixmicat.php")
        object 旅遊 : Sora("旅遊", "https://travel.voidfactory.com/pixmicat.php")
        object 圖書 : Sora("圖書", "http://gzone-anime.info/UnitedSites/books/pixmicat.php")
        object 鋼普拉 : Sora("鋼普拉", "https://iris.komica1.org/61/pixmicat.php")

        // 影音
        object Vtuber : Sora("Vtuber", "https://gaia.komica1.org/74/pixmicat.php")

        // 遊戲
        object 獨立遊戲 : Sora("獨立遊戲", "https://komica.dbfoxtw.me/indiegame/pixmicat.php")

        // 遊戲作品
        object 艦隊收藏 : Sora("艦隊收藏", "http://acgspace.wsfun.com/kancolle/pixmicat.php")

        // 製作公司
        object `TYPE-MOON` :
            Sora("TYPE-MOON", "http://gzone-anime.info/UnitedSites/TypeMoon/pixmicat.php")

        // 專題板
        object 御姊 : Sora("御姊", "https://sister.boguspix.com/pixmicat.php")
        object 機娘 : Sora("機娘", "https://msgirls.boguspix.com/pixmicat.php")
        object 人外 : Sora("人外", "https://komica.dbfoxtw.me/jingai/pixmicat.php")

        // 本地版
        object 男性角色 : Sora("男性角色", "https://gaia.komica1.org/38/pixmicat.php")
        object 女性角色 : Sora("女性角色", "https://gaia.komica1.org/19/pixmicat.php")
        object GIF : Sora("GIF", "https://iris.komica1.org/23/pixmicat.php")
        object 小說 : Sora("小說", "https://iris.komica1.org/35/pixmicat.php")
        object `奇幻-科幻` : Sora("奇幻/科幻", "https://gaia.komica1.org/60/pixmicat.php")
        object 四格 : Sora("四格", "https://gaia.komica1.org/42/pixmicat.php")
    }

    sealed class _2catSora(name: String, url: String) : KBoard(name, url) {
        // 連線板
        object 新番實況 : _2catSora("新番實況", "https://gaia.komica1.org/78/pixmicat.php")
        object 新番捏他 : _2catSora("新番捏他", "https://gaia.komica1.org/79/pixmicat.php")
        object 螢幕攝 :
            _2catSora("螢幕攝", "https://pixmicat.alica.idv.tw/screenshot/index.php/pixmicat.php")

        object 車 : _2catSora("車", "https://www.gomiga.org/car/pixmicat.php")
        object 萌 : _2catSora("萌", "https://2cat.komica1.org/~kirur/img2/pixmicat.php")

        // 連線二板
        object 故事接龍 : _2catSora("故事接龍", "https://storysol.boguspix.com/pixmicat.php")

        // 遊戲
        object 遊戲設計 :
            _2catSora("遊戲設計", "https://komica.dbfoxtw.me/gameprogramming/pixmicat.php")

        // 遊戲作品
        object GTA : _2catSora("GTA", "https://fenrisulfr.org/gta/pixmicat.php")
        object `World-of-Tanks` :
            _2catSora("World of Tanks", "https://fenrisulfr.org/wot/pixmicat.php")

        object 戰地風雲 : _2catSora("戰地風雲", "https://fenrisulfr.org/battlefield/pixmicat.php")
        object 戰爭雷霆 : _2catSora("戰爭雷霆", "https://fenrisulfr.org/war_thunder/pixmicat.php")
        object 戰機世界 : _2catSora("戰機世界", "https://fenrisulfr.org/wowp/pixmicat.php")
        object 戰艦世界 : _2catSora("戰艦世界", "https://fenrisulfr.org/wows/pixmicat.php")

        // 動漫作品
        object Homestuck :
            _2catSora("Homestuck", "https://komica.dbfoxtw.me/homestuck/pixmicat.php")

        // 製作公司
        object KOEI : _2catSora("KOEI", "https://www.karlsland.net/koei/pixmicat.php")

        // 專題板
        object 巫女 : _2catSora("巫女", "https://pixmicat.alica.idv.tw/miko/pixmicat.php")
    }

    sealed class _2cat(name: String, url: String) : KBoard(name, url) {
        // 連線二板
        object 職業相談 : _2cat("職業相談", "https://2cat.org/career")
        object 理財 : _2cat("理財", "https://cat.2nyan.org/finance")
        object 法律 : _2cat("法律", "https://cat.2nyan.org/law")
        object 繪師版 : _2cat("繪師版", "https://2cat.org/artist")
        object 猜謎 : _2cat("猜謎", "https://cat.2nyan.org/quiz")
        object 天文 : _2cat("天文", "https://2cat.org/astronomy")
        object 超常現象 : _2cat("超常現象", "https://cat.2nyan.org/supernature")
        object 服飾穿搭 : _2cat("服飾穿搭", "https://2cat.org/dressing")

        // 遊戲
        object 女性向遊戲 : _2cat("女性向遊戲", "https://2cat.org/boylove")
        object `手機-平板遊戲` : _2cat("手機/平板遊戲", "https://2cat.org/cellphone")
        object 體感遊戲 : _2cat("體感遊戲", "https://2cat.org/motion")
        object 桌上遊戲 : _2cat("桌上遊戲", "https://2cat.org/boardgame")
        object 網頁遊戲 : _2cat("網頁遊戲", "https://2cat.org/webgame2")
        object 手機遊戲 : _2cat("手機遊戲", "https://2cat.org/handheld")

        // 遊戲作品
        object 碧藍幻想 : _2cat("碧藍幻想", "https://2cat.org/granblue")
        object `Azur-Lane` : _2cat("Azur Lane", "https://2cat.org/azurlane")

        // 動漫作品
        object 東方 : _2cat("東方", "https://2cat.org/~touhou")
        object 龍騎士07 : _2cat("龍騎士07", "https://2cat.org/07expansion")
        object 涼宮 : _2cat("涼宮", "https://eclair.nagatoyuki.org/szmy")

        // 飲食
        object `咖啡-茶飲` : _2cat("咖啡-茶飲", "https://2cat.org/coffee")
        object 烹飪版 : _2cat("烹飪版", "https://2cat.org/cooking")

        // 動物
        object 動物寵物 : _2cat("動物寵物", "https://2cat.org/animal")
        object 鳥 : _2cat("鳥", "https://2cat.org/bird")

        // 桌布壁紙
        object 二次壁 : _2cat("二次壁", "https://2cat.org/wallpaper")

        // 電腦網路
        object 程設交流 : _2cat("程設交流", "https://www.gomiga.org/cs")
        object 人工智慧 : _2cat("人工智慧", "https://www.gomiga.org/ai")
    }

    sealed class SoraKomica2(name: String, url: String) : KBoard(name, url) {
        object 二次裡A避難版 : SoraKomica2("二次裡A避難版", "https://2cat.uk/~chatura/pixmicat.php")
        object 三次裡避難版 : SoraKomica2("三次裡避難版", "https://2cat.uk/~realura/pixmicat.php")
        object 高解析裡 : SoraKomica2("高解析裡", "https://2cat.org/hiso")
    }

    sealed class _2catKomica2(name: String, url: String) : KBoard(name, url) {
        object GIF裏 : _2catKomica2("GIF裏", "https://2cat.org/~gifura/pixmicat.php")
        object 玩具裏 : _2catKomica2("玩具裏", "https://2cat.org/~toy/pixmicat.php")
        object 裏知識 : _2catKomica2("裏知識", "https://2cat.org/~know/pixmicat.php")
        object 偽娘裏 : _2catKomica2("偽娘裏", "https://2cat.org/~futa/pixmicat.php")
        object 東方裏避難版 : _2catKomica2("東方裏避難版", "https://2cat.org/touhoux/pixmicat.php")
    }

    // TODO: 以下尚未實現 Parser
    // 連線版
    object 影視 : KBoard("影視", "https://www.akraft.net/service/66a6eca2bfccee3f04a52bc4")
    object 祭典 : KBoard("祭典(双猫村祭典広場)", "https://rthost.win/sd")
    object COSPLAY : KBoard("COSPLAY", "https://selene.zawarudo.org/costumade")

    // 連線二版
    object 海外 : KBoard("海外", "https://eclair.nagatoyuki.org/outremer")
    object 流言終結 : KBoard("流言終結", "https://eclair.nagatoyuki.org/myth")
    object 相談 : KBoard("相談", "https://eclair.nagatoyuki.org/conseil")
    object 安價 : KBoard("安價", "https://eclair.nagatoyuki.org/ancre")

    // 遊戲作品
    object Dota2 : KBoard("Dota2", "https://www.akraft.net/service/61bc09b0e27a80b99d12c095")
    object 蔚藍檔案 : KBoard("蔚藍檔案", "https://www.gomiga.org/bluearchive")
    object 少女前線 : KBoard("少女前線", "https://secilia.zawarudo.org/gf")
    object AGA : KBoard("AGA", "https://secilia.zawarudo.org/alicegearaegis")

    // 動漫作品
    object `Strike-Witches` : KBoard("Strike-Witches", "https://www.karlsland.net/sw")

    // 專題版
    object 獸 : KBoard("獸", "https://kemono.wtako.net/kemono")

    // 創作
    object 線上繪圖 : KBoard("線上繪圖", "https://selene.zawarudo.org/dessin")
    object 塗鴉保育區 : KBoard("塗鴉保育區", "https://kemono.wtako.net/kemozone")
    object `MMD-Vocaloid` : KBoard("MMD/Vocaloid", "https://selene.zawarudo.org/avenir")

    // 飲食
    object 酒 : KBoard("酒", "https://eclair.nagatoyuki.org/beverage")

    // Komica2
    object 詢問裡 : KBoard("詢問裡", "https://majeur.zawarudo.org/demande")
    object 二次元獵奇 : KBoard("二次元獵奇", "https://majeur.zawarudo.org/guro")
    object 遊戲裡避難版 : KBoard("遊戲裡避難版", "https://majeur.zawarudo.org/hgame")

}

fun 連線版() =
    listOf(
        KBoard.Sora.綜合,
        KBoard.Sora.掛圖,
        KBoard.Sora.政治,
        KBoard.Sora.模型,
        KBoard.Sora.歡樂惡搞,
        KBoard.Sora.特攝,
        KBoard.Sora.蘿蔔,
        KBoard.Sora.軍武,
        KBoard._2catSora.新番實況,
        KBoard._2catSora.新番捏他,
        KBoard._2catSora.螢幕攝,
        KBoard._2catSora.車,
        KBoard._2catSora.萌,
        KBoard.影視,
        KBoard.祭典,
        KBoard.COSPLAY,
    )

fun 連線二版() =
    listOf(
        KBoard.Sora.生活消費,
        KBoard.Sora.藝術,
        KBoard.Sora.旅遊,
        KBoard.Sora.圖書,
        KBoard.Sora.鋼普拉,
        KBoard._2catSora.故事接龍,
        KBoard._2cat.職業相談,
        KBoard._2cat.理財,
        KBoard._2cat.法律,
        KBoard._2cat.繪師版,
        KBoard._2cat.猜謎,
        KBoard._2cat.天文,
        KBoard._2cat.超常現象,
        KBoard._2cat.服飾穿搭,
        KBoard.海外,
        KBoard.流言終結,
        KBoard.相談,
        KBoard.安價,
    )

fun 影音() =
    listOf(
        KBoard.Sora.Vtuber,
    )

fun 遊戲() =
    listOf(
        KBoard.Sora.獨立遊戲,
        KBoard._2catSora.遊戲設計,
        KBoard._2cat.`手機-平板遊戲`,
        KBoard._2cat.體感遊戲,
        KBoard._2cat.女性向遊戲,
        KBoard._2cat.桌上遊戲,
        KBoard._2cat.網頁遊戲,
        KBoard._2cat.手機遊戲,
    )

fun 遊戲作品() =
    listOf(
        KBoard.Sora.艦隊收藏,
        KBoard._2catSora.GTA,
        KBoard._2catSora.`World-of-Tanks`,
        KBoard._2catSora.戰地風雲,
        KBoard._2catSora.戰爭雷霆,
        KBoard._2catSora.戰機世界,
        KBoard._2catSora.戰艦世界,
        KBoard._2cat.碧藍幻想,
        KBoard._2cat.`Azur-Lane`,
        KBoard.Dota2,
        KBoard.蔚藍檔案,
        KBoard.少女前線,
        KBoard.AGA,
    )

fun 動漫作品() =
    listOf(
        KBoard._2catSora.Homestuck,
        KBoard._2cat.東方,
        KBoard._2cat.龍騎士07,
        KBoard._2cat.涼宮,
        KBoard.`Strike-Witches`,
    )

fun 製作公司() = listOf(KBoard.Sora.`TYPE-MOON`, KBoard._2catSora.KOEI)

fun 專題版() =
    listOf(
        KBoard.Sora.御姊,
        KBoard.Sora.機娘,
        KBoard.Sora.人外,
        KBoard._2catSora.巫女,
        KBoard.獸,
    )

fun 創作() =
    listOf(
        KBoard.線上繪圖,
        KBoard.塗鴉保育區,
        KBoard.`MMD-Vocaloid`,
    )

fun 飲食() = listOf(
    KBoard._2cat.`咖啡-茶飲`,
    KBoard._2cat.烹飪版,
    KBoard.酒,
)

fun 動物() = listOf(
    KBoard._2cat.動物寵物,
    KBoard._2cat.鳥,
)

fun 桌布壁紙() = listOf(
    KBoard._2cat.二次壁,
)

fun 電腦網路() = listOf(
    KBoard._2cat.程設交流,
    KBoard._2cat.人工智慧,
)

fun 本地版() =
    listOf(
        KBoard.Sora.男性角色,
        KBoard.Sora.女性角色,
        KBoard.Sora.GIF,
        KBoard.Sora.小說,
        KBoard.Sora.`奇幻-科幻`,
        KBoard.Sora.四格,
    )

fun Komica2() = listOf(
    KBoard.SoraKomica2.二次裡A避難版,
    KBoard.SoraKomica2.三次裡避難版,
    KBoard.SoraKomica2.高解析裡,
//    KBoard._2catKomica2.GIF裏,
//    KBoard._2catKomica2.玩具裏,
//    KBoard._2catKomica2.裏知識,
//    KBoard._2catKomica2.偽娘裏,
//    KBoard._2catKomica2.東方裏避難版,
)

fun top50boards() = listOf(
    KBoard.Sora.綜合,
    KBoard._2catSora.新番捏他,
    KBoard.Sora.四格,
    KBoard.Sora.女性角色,
    KBoard.Sora.男性角色,
    KBoard._2catSora.新番實況,
    KBoard.Sora.歡樂惡搞,
    KBoard.Sora.GIF,
    KBoard.Sora.政治,
    KBoard.Sora.模型,
    KBoard.Sora.蘿蔔,
    KBoard.影視,
    KBoard.Sora.鋼普拉,
    KBoard.Sora.軍武,
    KBoard.Sora.特攝,
    KBoard.Sora.`TYPE-MOON`,
    KBoard.Sora.Vtuber,
    KBoard.Sora.`奇幻-科幻`,
    KBoard.Sora.掛圖,
    KBoard._2cat.碧藍幻想,
    KBoard.Sora.小說,
    KBoard._2cat.手機遊戲,
    KBoard.Sora.人外,
    KBoard.AGA,
    KBoard.少女前線,
    KBoard._2cat.網頁遊戲,
    KBoard.Sora.艦隊收藏,
    KBoard._2catSora.車,
    KBoard.祭典,
    )

fun boards() = top50boards()
    .asSequence()
    .plus(連線版())
    .plus(連線二版())
    .plus(影音())
    .plus(遊戲())
    .plus(遊戲作品())
    .plus(動漫作品())
    .plus(製作公司())
    .plus(專題版())
    .plus(創作())
    .plus(飲食())
    .plus(動物())
    .plus(桌布壁紙())
    .plus(電腦網路())
    .plus(本地版())
    .plus(Komica2())
    .distinct().toList()
