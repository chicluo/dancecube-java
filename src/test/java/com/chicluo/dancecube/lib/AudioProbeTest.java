package com.chicluo.dancecube.lib;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AudioProbe 的 URL 解析：曲目 AudioUrl 可能带空格和中文，解析不能抛。 */
class AudioProbeTest {

    @Test
    void parsesPlainUrl() {
        URI uri = AudioProbe.toUri("http://asset-music.oss-cn-shanghai.aliyuncs.com/dancecube/a.mp3");
        assertEquals("asset-music.oss-cn-shanghai.aliyuncs.com", uri.getHost());
        assertEquals("/dancecube/a.mp3", uri.getPath());
    }

    @Test
    void encodesSpaceAndChineseInsteadOfThrowing() {
        // 实测 23812「断罪」的 AudioUrl 就是这种形态
        URI uri = AudioProbe.toUri(
                "http://asset-music.oss-cn-shanghai.aliyuncs.com/dancecube/502_adjudicatorz 断罪_convert.mp3");
        assertEquals("asset-music.oss-cn-shanghai.aliyuncs.com", uri.getHost());
        assertEquals("/dancecube/502_adjudicatorz%20%E6%96%AD%E7%BD%AA_convert.mp3", uri.getRawPath());
        assertTrue(uri.getPath().endsWith("_convert.mp3"));
    }

    @Test
    void keepsExistingPercentEscapes() {
        URI uri = AudioProbe.toUri("http://h/x%20y.mp3");
        assertEquals("/x%20y.mp3", uri.getRawPath());
    }
}