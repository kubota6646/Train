package com.github.kubota6646.train.listener;

import com.github.kubota6646.train.manager.TrainManager;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤーがトロッコを右クリックしたときの連結操作を処理するリスナー。
 *
 * <ul>
 *   <li>チェーン ({@link Material#CHAIN}) を手に持ちトロッコを右クリック → 連結モード開始</li>
 *   <li>連結モード中に別のトロッコを右クリック → 2つを連結</li>
 *   <li>スニーク + チェーン右クリック → 連結解除</li>
 * </ul>
 */
public class CartLinkListener implements Listener {

    private final TrainManager trainManager;

    /** 連結待機中のプレイヤー: プレイヤーUUID → 1台目に選択したトロッコ */
    private final Map<UUID, Minecart> pendingLink = new HashMap<>();

    public CartLinkListener(TrainManager trainManager) {
        this.trainManager = trainManager;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // オフハンドのイベントは無視（重複呼び出し防止）
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (event.getRightClicked().getType() != EntityType.MINECART
                && event.getRightClicked().getType() != EntityType.CHEST_MINECART
                && event.getRightClicked().getType() != EntityType.FURNACE_MINECART
                && event.getRightClicked().getType() != EntityType.HOPPER_MINECART
                && event.getRightClicked().getType() != EntityType.TNT_MINECART) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("train.use")) return;

        // チェーンを手に持っているか確認
        if (player.getInventory().getItemInMainHand().getType() != Material.CHAIN) return;

        // チェーンを持っている場合はトロッコへの乗車をキャンセル
        event.setCancelled(true);

        Minecart clicked = (Minecart) event.getRightClicked();

        if (player.isSneaking()) {
            // スニーク右クリック → 連結解除
            pendingLink.remove(player.getUniqueId());
            if (trainManager.unlink(clicked)) {
                player.sendMessage("§aトロッコの連結を解除しました。");
            } else {
                player.sendMessage("§cこのトロッコは連結されていません。");
            }
            return;
        }

        Minecart pending = pendingLink.get(player.getUniqueId());

        if (pending == null) {
            // 1台目を選択
            pendingLink.put(player.getUniqueId(), clicked);
            player.sendMessage("§e1台目のトロッコを選択しました。もう1台のトロッコを右クリックしてください。");
        } else {
            // 2台目を選択 → 連結試行
            pendingLink.remove(player.getUniqueId());

            if (pending.getUniqueId().equals(clicked.getUniqueId())) {
                player.sendMessage("§c同じトロッコを選択しました。最初からやり直してください。");
                return;
            }

            // 1台目のエンティティがまだ有効か確認
            if (!pending.isValid()) {
                player.sendMessage("§c最初に選択したトロッコが見つかりません。やり直してください。");
                return;
            }

            if (trainManager.link(pending, clicked)) {
                player.sendMessage("§aトロッコを連結しました！");
            } else {
                player.sendMessage("§c連結できませんでした。すでに連結済みか、循環する連結になります。");
            }
        }
    }
}
