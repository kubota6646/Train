package com.github.kubota6646.train.manager;

import org.bukkit.entity.Minecart;

import java.util.*;

/**
 * トロッコの連結状態を管理するクラス。
 * 連結はリーダー→フォロワーの有向ペアとして管理し、
 * チェーン構造（A→B→C）をサポートします。
 */
public class TrainManager {

    /** フォロワーUUID → リーダーUUID のマッピング */
    private final Map<UUID, UUID> followerToLeader = new HashMap<>();

    /** リーダーUUID → フォロワーUUID のマッピング */
    private final Map<UUID, UUID> leaderToFollower = new HashMap<>();

    /**
     * 2つのトロッコを連結します。cart2 が cart1 の後ろに続きます。
     *
     * @param leader   先頭になるトロッコ
     * @param follower 追従するトロッコ
     * @return 連結に成功した場合 true、すでに連結済みの場合 false
     */
    public boolean link(Minecart leader, Minecart follower) {
        if (leader.getUniqueId().equals(follower.getUniqueId())) {
            return false;
        }
        // すでに連結済みか確認
        if (followerToLeader.containsKey(follower.getUniqueId())) {
            return false;
        }
        if (leaderToFollower.containsKey(leader.getUniqueId())) {
            return false;
        }
        // 循環参照チェック（follower がすでに leader のリーダー側にある）
        if (isAncestor(follower.getUniqueId(), leader.getUniqueId())) {
            return false;
        }

        followerToLeader.put(follower.getUniqueId(), leader.getUniqueId());
        leaderToFollower.put(leader.getUniqueId(), follower.getUniqueId());
        return true;
    }

    /**
     * 指定トロッコが持つ連結をすべて解除します（前後の連結を解除）。
     *
     * @param cart 連結を解除するトロッコ
     * @return 1つ以上連結が解除された場合 true
     */
    public boolean unlink(Minecart cart) {
        UUID id = cart.getUniqueId();
        boolean changed = false;

        // フォロワー側の連結を解除
        UUID leaderId = followerToLeader.remove(id);
        if (leaderId != null) {
            leaderToFollower.remove(leaderId);
            changed = true;
        }

        // リーダー側の連結を解除
        UUID followerId = leaderToFollower.remove(id);
        if (followerId != null) {
            followerToLeader.remove(followerId);
            changed = true;
        }

        return changed;
    }

    /**
     * 指定トロッコが連結されているかどうかを返します。
     */
    public boolean isLinked(Minecart cart) {
        UUID id = cart.getUniqueId();
        return followerToLeader.containsKey(id) || leaderToFollower.containsKey(id);
    }

    /**
     * フォロワーUUID → リーダーUUID のマッピングを返します（読み取り専用）。
     */
    public Map<UUID, UUID> getFollowerToLeaderMap() {
        return Collections.unmodifiableMap(followerToLeader);
    }

    /**
     * 全連結情報を削除します。
     */
    public void clearAll() {
        followerToLeader.clear();
        leaderToFollower.clear();
    }

    // ----- private helpers -----

    /** candidateId が targetId の祖先（リーダー方向）に存在するか確認 */
    private boolean isAncestor(UUID candidateId, UUID targetId) {
        UUID current = candidateId;
        Set<UUID> visited = new HashSet<>();
        while (current != null) {
            if (current.equals(targetId)) return true;
            if (!visited.add(current)) break; // 循環ガード
            current = followerToLeader.get(current);
        }
        return false;
    }
}
