package com.chicluo.dancecube.model;

/**
 * 一条单谱成绩，对标 Python 版 {@code Player.scores()} 产出的 dict。
 *
 * @param musicId       曲目 ID
 * @param name          曲名
 * @param ownerType     1 = 官谱，其余 = 自制谱
 * @param cls           分类（2..6）
 * @param clsName       分类名
 * @param levelType     难度档位（MusicLev）
 * @param levelTypeName 档位名
 * @param diff          旧难度（-1 表示无）
 * @param level         定数
 * @param acc           准确率（0..100）
 * @param score         分数
 * @param combo         最大连击
 * @param miss          miss 数
 * @param time          记录时间
 * @param rating        单谱 Rating
 * @param grade         评级（SSS/SS/S/A…）
 */
public record Score(
        long musicId,
        String name,
        int ownerType,
        int cls,
        String clsName,
        int levelType,
        String levelTypeName,
        int diff,
        int level,
        double acc,
        int score,
        int combo,
        int miss,
        Object time,
        int rating,
        String grade) {

    public boolean isOfficial() {
        return ownerType == 1;
    }
}
