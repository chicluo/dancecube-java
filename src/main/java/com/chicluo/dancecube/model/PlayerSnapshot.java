package com.chicluo.dancecube.model;

import java.util.Map;

/**
 * 玩家快照：一次登录拉到的个人数据，对标 Python 版 Player 的 info/account/stats_items/history。
 *
 * @param info       角色信息（UserName/UserID/MusicScore/ComboPercent/RankNation/LvRatio/CityName/TeamName…）
 * @param account    账号信息（Gold…）
 * @param statsItems 统计项列表（ReplyTextItemID/Content）
 * @param history    天梯历史
 */
public record PlayerSnapshot(
        Map<String, Object> info,
        Map<String, Object> account,
        java.util.List<Map<String, Object>> statsItems,
        java.util.List<Map<String, Object>> history) {
}
