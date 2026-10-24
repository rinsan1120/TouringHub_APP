package jp.touringhub.assistant;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Small, local intent parser: no LLM, API credentials, or paid network service. */
public final class JapaneseInput {
    private JapaneseInput() {}
    public static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.JAPANESE).replaceAll("[\\s、。！？!?・]", "");
    }
    public static boolean cancel(String text) {
        return List.of("キャンセル", "中止", "やめる", "終了", "やめて", "中止して", "終了して").contains(normalize(text));
    }
    public static boolean yes(String text) {
        return List.of("はい", "はいお願いします", "うん", "そう", "そうです", "お願いします",
                "そこ", "そこです", "それでお願いします", "それでいいです", "合っています", "あっています",
                "正しいです", "オーケー", "ok").contains(normalize(text));
    }
    public static boolean no(String text) {
        return List.of("いいえ", "違う", "違います", "ちがう", "違うよ", "違いますよ", "違う場所",
                "言い直す", "やり直し", "やり直して", "もう一度", "変更", "変更して").contains(normalize(text));
    }
    public static boolean repeat(String text) {
        return List.of("繰り返して", "もう一度言って", "もう一回言って", "聞き取れなかった").contains(normalize(text));
    }
    public static String destination(String text) {
        String value = Normalizer.normalize(text == null ? "" : text.trim(), Normalizer.Form.NFKC)
                .replaceAll("[。！？!?]+$", "").trim();
        value = value.replaceFirst("(?:んだけど|んですが|のですが)$", "");
        value = value.replaceFirst("^(?:えーと|えっと|あの)[、\\s]*", "");
        value = value.replaceFirst("^(?:目的地は|目的地を|行き先は|行き先を)", "");
        value = value.replaceFirst("(?:まで|へ|に)(?:行きたい(?:です)?|行こう|行って(?:ください)?|向かいたい(?:です)?|案内(?:して)?(?:ください|ほしい)?|連れて(?:いって|行って)(?:ください)?|ナビ(?:して)?(?:ください)?|設定(?:して)?(?:ください)?)$", "");
        value = value.replaceFirst("(?:を)(?:目的地に設定して|案内して|目的地にして)(?:ください)?$", "");
        return value.trim();
    }
    public static int choice(String text, int count) {
        String value = normalize(text).replaceFirst("^(?:候補の|候補)", "")
                .replaceFirst("(?:番目|番|つ目|ばんめ|ばん)(?:の候補)?(?:で|です|をお願い(?:します)?|にして|でお願いします)?$", "");
        String[] kanji = {"一", "二", "三", "四", "五"};
        String[] reading = {"いち", "に", "さん", "よん", "ご"};
        for (int i = 0; i < Math.min(count, 5); i++) {
            if (value.equals(Integer.toString(i + 1)) || value.equals(kanji[i]) || value.equals(reading[i])) return i;
        }
        return -1;
    }
}
