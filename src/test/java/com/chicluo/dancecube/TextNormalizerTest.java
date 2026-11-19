package com.chicluo.dancecube;

import com.chicluo.dancecube.util.TextNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextNormalizerTest {

    @Test
    void normalizesJaVariants() {
        // 「収」是日文异体字，归一后要和简体「收」一致
        assertEquals(
                TextNormalizer.normalize("不幸人间强制收容所"),
                TextNormalizer.normalize("不幸人間強制収容所"));
    }

    @Test
    void normalizesCyrillic() {
        // 拉丁键盘打俄语：Н→h、а→a、ш→w、Р→p、с→c…
        assertEquals(
                TextNormalizer.normalize("haw paccbet"),
                TextNormalizer.normalize("Наш Рассвет"));
    }

    @Test
    void lowercasesAndContains() {
        assertTrue(TextNormalizer.contains("Calavera", "calavera"));
        assertEquals("", TextNormalizer.normalize(null));
    }
}
