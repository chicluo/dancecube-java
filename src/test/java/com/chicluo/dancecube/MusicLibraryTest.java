package com.chicluo.dancecube;

import com.chicluo.dancecube.lib.MusicLibrary;
import com.chicluo.dancecube.model.Song;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicLibraryTest {

    @TempDir
    Path dataDir;

    @Test
    void searchesBothLibraries() throws IOException {
        Files.writeString(dataDir.resolve("music_library.json"), """
                [{"Name":"Calavera","MusicID":291,"MusicCategoryNew":4,
                  "MusicItemList":[{"MusicLevNew":15,"MusicLev":2,"MusicLevel":14,"MusicCombo":1200}]}]
                """);
        Files.writeString(dataDir.resolve("music_library_user.json"), """
                [{"GoodsName":"Calavera","MusicID":6482,"OwnerID":1,"OwnerName":"作者",
                  "LevelList":[{"MusicLevNew":14,"MusicLev":1,"MusicLevel":13,"MusicCombo":900}]}]
                """);

        MusicLibrary lib = new MusicLibrary(dataDir);
        List<Song> hits = lib.search("calavera", 10);
        assertEquals(2, hits.size());
        assertTrue(hits.get(0).isOfficial());
        assertEquals(291, hits.get(0).musicId());
        assertEquals("高级", hits.get(0).levels().get(0).tier());
        assertEquals("[高]传奇(14)", hits.get(0).levels().get(0).label());

        Song byId = lib.findById(6482);
        assertTrue(byId != null && !byId.isOfficial());
        assertEquals("作者", byId.ownerName());
    }

    @Test
    void normalizesSearchInput() throws IOException {
        Files.writeString(dataDir.resolve("music_library_user.json"), """
                [{"GoodsName":"不幸人間強制収容所","MusicID":23813,"LevelList":[]}]
                """);
        MusicLibrary lib = new MusicLibrary(dataDir);
        assertEquals(1, lib.search("不幸人间强制收容所", 10).size());
    }
}
