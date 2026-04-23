package com.github.kubota6646.train.command;

import com.github.kubota6646.train.manager.TrainManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * /train コマンドを処理するクラス。
 *
 * <ul>
 *   <li>/train link   — プレイヤーが乗っているトロッコを連結モード（インタラクション経由と同様の説明を表示）</li>
 *   <li>/train unlink — プレイヤーが乗っているトロッコの連結を解除</li>
 * </ul>
 */
public class TrainCommand implements CommandExecutor, TabCompleter {

    private final TrainManager trainManager;

    public TrainCommand(TrainManager trainManager) {
        this.trainManager = trainManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ使用できます。");
            return true;
        }

        if (!player.hasPermission("train.use")) {
            player.sendMessage("§cこのコマンドを使用する権限がありません。");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "link" -> handleLink(player);
            case "unlink" -> handleUnlink(player);
            default -> sendUsage(player);
        }
        return true;
    }

    private void handleLink(Player player) {
        player.sendMessage("§eチェーン (Chain) を手に持ち、連結したい2台のトロッコを順番に右クリックしてください。");
        player.sendMessage("§eスニーク+右クリックで連結を解除できます。");
    }

    private void handleUnlink(Player player) {
        if (!(player.getVehicle() instanceof Minecart cart)) {
            player.sendMessage("§cトロッコに乗っている状態で実行してください。");
            return;
        }

        if (trainManager.unlink(cart)) {
            player.sendMessage("§aトロッコの連結を解除しました。");
        } else {
            player.sendMessage("§cこのトロッコは連結されていません。");
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage("§6=== Train Plugin ===");
        player.sendMessage("§e/train link   §f- トロッコの連結方法を表示");
        player.sendMessage("§e/train unlink §f- 乗っているトロッコの連結を解除");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("link", "unlink");
        }
        return List.of();
    }
}
