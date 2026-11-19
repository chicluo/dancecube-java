package com.chicluo.dancecube.util;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 搜索归一化：和 Python 版 {@code core/music.py} 的 {@code _norm_text} 对齐。
 *
 * <ul>
 *     <li>繁简统一到简体（opencc4j，对标 zhconv）</li>
 *     <li>日文异体字归一到中文写法（「収」→「收」等）</li>
 *     <li>西里尔字母转拉丁形近字母（「Наш Рассвет」→「haw paccbet」）</li>
 *     <li>忽略大小写</li>
 * </ul>
 */
public final class TextNormalizer {

    /** 日文异体字：zhconv / opencc 的繁简表不处理这些日式写法。 */
    private static final Map<Character, Character> JA_VARIANTS = new HashMap<>();

    /** 西里尔 → 拉丁形近字母，让拉丁键盘也能搜俄语曲名。 */
    private static final Map<Character, Character> CYRILLIC_LATIN = new HashMap<>();

    static {
        String[][] ja = {
                {"収", "收"}, {"増", "增"}, {"対", "对"}, {"発", "发"}, {"実", "实"},
                {"変", "变"}, {"広", "广"}, {"売", "卖"}, {"読", "读"}, {"気", "气"},
                {"県", "县"}, {"戦", "战"}, {"検", "检"}, {"験", "验"}, {"権", "权"},
                {"済", "济"}, {"聴", "听"}, {"仏", "佛"}, {"伝", "传"}, {"帰", "归"},
                {"闘", "斗"}, {"駅", "驿"}, {"駆", "驱"}, {"雑", "杂"}, {"霊", "灵"},
                {"騒", "骚"}, {"鶏", "鸡"}, {"齢", "龄"}, {"歴", "历"}, {"塩", "盐"},
        };
        for (String[] pair : ja) {
            JA_VARIANTS.put(pair[0].charAt(0), pair[1].charAt(0));
        }

        String[][] cy = {
                {"А", "a"}, {"В", "b"}, {"Е", "e"}, {"К", "k"}, {"М", "m"},
                {"Н", "h"}, {"О", "o"}, {"Р", "p"}, {"С", "c"}, {"Т", "t"},
                {"У", "y"}, {"Х", "x"}, {"Ш", "w"}, {"Щ", "w"},
                {"а", "a"}, {"в", "b"}, {"е", "e"}, {"к", "k"}, {"м", "m"},
                {"н", "h"}, {"о", "o"}, {"р", "p"}, {"с", "c"}, {"т", "t"},
                {"у", "y"}, {"х", "x"}, {"ш", "w"}, {"щ", "w"},
        };
        for (String[] pair : cy) {
            CYRILLIC_LATIN.put(pair[0].charAt(0), pair[1].charAt(0));
        }
    }

    private TextNormalizer() {
    }

    /** 归一化一段文本用于搜索比较。 */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String s = text.strip();
        try {
            s = ZhConverterUtil.toSimple(s);
        } catch (RuntimeException ignored) {
            // 转换失败就按原文比较，和 Python 版的容错一致
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            Character ja = JA_VARIANTS.get(c);
            if (ja != null) {
                sb.append(ja);
                continue;
            }
            Character cy = CYRILLIC_LATIN.get(c);
            sb.append(cy != null ? cy : c);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** 归一化后的包含判断：search 里用。 */
    public static boolean contains(String haystack, String needle) {
        return normalize(haystack).contains(normalize(needle));
    }
}
