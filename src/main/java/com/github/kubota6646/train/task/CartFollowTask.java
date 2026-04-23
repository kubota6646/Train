package com.github.kubota6646.train.task;

import com.github.kubota6646.train.manager.TrainManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 連結されたトロッコがリーダーに追従するよう速度を制御するタスク。
 * 2 tick ごとに実行されます。
 */
public class CartFollowTask extends BukkitRunnable {

    /** フォロワーがリーダーに対して維持しようとする目標距離（ブロック） */
    private static final double TARGET_DISTANCE = 2.0;

    /** 速度補正のスケール係数 */
    private static final double SPEED_SCALE = 0.4;

    /** 最大補正速度 */
    private static final double MAX_SPEED = 1.0;

    private final TrainManager trainManager;

    /** UUID → Minecart の WeakReference キャッシュ（全エンティティ走査を回避） */
    private final Map<UUID, WeakReference<Minecart>> entityCache = new HashMap<>();

    public CartFollowTask(TrainManager trainManager) {
        this.trainManager = trainManager;
    }

    @Override
    public void run() {
        Map<UUID, UUID> links = trainManager.getFollowerToLeaderMap();
        if (links.isEmpty()) return;

        // ConcurrentModificationException を防ぐためにスナップショットを取る
        List<Map.Entry<UUID, UUID>> entries = new ArrayList<>(links.entrySet());

        for (Map.Entry<UUID, UUID> entry : entries) {
            UUID followerId = entry.getKey();
            UUID leaderId = entry.getValue();

            Minecart follower = resolveMinecart(followerId);
            Minecart leader = resolveMinecart(leaderId);

            // どちらかが消滅していたら連結を解除
            if (follower == null || leader == null) {
                entityCache.remove(followerId);
                entityCache.remove(leaderId);
                if (follower != null) trainManager.unlink(follower);
                if (leader != null) trainManager.unlink(leader);
                continue;
            }

            applyFollowVelocity(leader, follower);
        }
    }

    /**
     * フォロワーカートがリーダーカートを追従するよう速度ベクトルを設定します。
     */
    private void applyFollowVelocity(Minecart leader, Minecart follower) {
        Vector leaderPos = leader.getLocation().toVector();
        Vector followerPos = follower.getLocation().toVector();
        Vector toLeader = leaderPos.clone().subtract(followerPos);
        double distance = toLeader.length();

        if (distance < 0.01) return;

        double gap = distance - TARGET_DISTANCE;

        if (gap > 0.3) {
            // フォロワーがリーダーから離れすぎ → 前進
            double speed = Math.min(gap * SPEED_SCALE, MAX_SPEED);
            follower.setVelocity(toLeader.normalize().multiply(speed));
        } else if (gap < -0.3) {
            // フォロワーがリーダーに近づきすぎ → 減速または後退
            double speed = Math.min(-gap * SPEED_SCALE, MAX_SPEED * 0.5);
            follower.setVelocity(toLeader.normalize().multiply(-speed));
        }
        // 目標距離付近では速度を変更しない（レールに任せる）
    }

    /**
     * UUID から Minecart エンティティを返します。
     * まず WeakReference キャッシュを参照し、キャッシュが無効な場合のみ
     * 全ワールドを走査してキャッシュを更新します。
     * エンティティが存在しない・無効な場合は null を返します。
     */
    private Minecart resolveMinecart(UUID uuid) {
        WeakReference<Minecart> ref = entityCache.get(uuid);
        if (ref != null) {
            Minecart cached = ref.get();
            if (cached != null && cached.isValid()) {
                return cached;
            }
            // GC 済みまたは無効になったキャッシュを削除
            entityCache.remove(uuid);
        }

        // キャッシュミス: 全ワールドを一度だけ走査
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(uuid) && entity instanceof Minecart minecart) {
                    entityCache.put(uuid, new WeakReference<>(minecart));
                    return minecart;
                }
            }
        }
        return null;
    }
}
