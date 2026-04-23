package com.github.kubota6646.train;

import com.github.kubota6646.train.command.TrainCommand;
import com.github.kubota6646.train.listener.CartLinkListener;
import com.github.kubota6646.train.manager.TrainManager;
import com.github.kubota6646.train.task.CartFollowTask;
import org.bukkit.plugin.java.JavaPlugin;

public class TrainPlugin extends JavaPlugin {

    private TrainManager trainManager;

    @Override
    public void onEnable() {
        trainManager = new TrainManager();

        getServer().getPluginManager().registerEvents(new CartLinkListener(trainManager), this);

        TrainCommand trainCommand = new TrainCommand(trainManager);
        getCommand("train").setExecutor(trainCommand);
        getCommand("train").setTabCompleter(trainCommand);

        new CartFollowTask(trainManager).runTaskTimer(this, 0L, 2L);

        getLogger().info("Train plugin enabled.");
    }

    @Override
    public void onDisable() {
        if (trainManager != null) {
            trainManager.clearAll();
        }
        getLogger().info("Train plugin disabled.");
    }
}
