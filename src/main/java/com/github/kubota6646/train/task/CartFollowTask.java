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
 *
 * <p>スプリングダンパー制御を採用しています。位置誤差（比例項）と
 * 速度誤差（微分項）の両方を加味することで、振動を抑えた滑らかな追従を実現します。</p>
 */
public class CartFollowTask extends BukkitRunnable {

    /** フォロワーがリーダーに対して維持しようとする目標距離（ブロック） */
    private static final double TARGET_DISTANCE = 2.0;

    /** 位置誤差に対する比例ゲイン */
    private static final double KP = 0.25;

    /** 速度誤差に対する微分ゲイン（ダンピング） */
    private static final double KD = 0.6;

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
     * フォロワーカートがリーダーカートをスムーズに追従するよう速度ベクトルを設定します。
     *
     * <p>スプリングダンパー制御：</p>
     * <ul>
     *   <li>比例項 (KP)：リーダーとの距離が目標からずれているほど強く引き寄せる / 押し返す</li>
     *   <li>微分項 (KD)：リーダーとの相対速度差を打ち消してオーバーシュートを抑える</li>
     * </ul>
     */
    private void applyFollowVelocity(Minecart leader, Minecart follower) {
        Vector leaderPos = leader.getLocation().toVector();
        Vector followerPos = follower.getLocation().toVector();
        Vector offset = leaderPos.clone().subtract(followerPos);
        double distance = offset.length();

        if (distance < 0.01) return;

        Vector direction = offset.normalize();
        double positionError = distance - TARGET_DISTANCE;

        // リーダー・フォロワーの速度を方向軸に投影して相対速度を計算
        double leaderSpeed = leader.getVelocity().dot(direction);
        double followerSpeed = follower.getVelocity().dot(direction);
        double velocityError = leaderSpeed - followerSpeed;

        // スプリングダンパー: 目標速度 = 現在速度 + 位置補正 + 速度ダンピング
        double targetSpeed = followerSpeed + KP * positionError + KD * velocityError;
        targetSpeed = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, targetSpeed));

        follower.setVelocity(direction.multiply(targetSpeed));
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
