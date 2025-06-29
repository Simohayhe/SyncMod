package net.simohaya;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.text.Text;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InventorySync {

    private static ScheduledExecutorService autoSaveScheduler;

    public static void savePlayerInventory(ServerPlayerEntity player) {
        DefaultedList<ItemStack> inventoryToSave = DefaultedList.ofSize(player.getInventory().size(), ItemStack.EMPTY);
        for (int i = 0; i < player.getInventory().size(); i++) {
            inventoryToSave.set(i, player.getInventory().getStack(i));
        }

        DatabaseManager.saveInventory(player.getUuid(), inventoryToSave);
        player.sendMessage(Text.of("§aインベントリをサーバーに保存しました。"), false);
    }

    public static void loadPlayerInventory(ServerPlayerEntity player) {
        DefaultedList<ItemStack> loadedInventory = DatabaseManager.loadInventory(player.getUuid());

        player.getInventory().clear();
        for (int i = 0; i < loadedInventory.size(); i++) {
            player.getInventory().setStack(i, loadedInventory.get(i));
        }
        player.sendMessage(Text.of("§aインベントリをサーバーからロードしました。"), false);
    }

    public static void saveAllPlayersInventories(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            savePlayerInventory(player);
        }
    }

    public static void startAutoSaveTask(int intervalSeconds) {
        if (autoSaveScheduler != null && !autoSaveScheduler.isShutdown()) {
            autoSaveScheduler.shutdownNow();
        }

        autoSaveScheduler = Executors.newSingleThreadScheduledExecutor();
        autoSaveScheduler.scheduleAtFixedRate(() -> {
            // スケジューラーはメインスレッドとは別のスレッドで実行されるため、
            // MinecraftServerインスタンスへのアクセスは注意が必要です。
            // サーバーがまだ稼働していることを確認し、安全にプレイヤーリストを取得します。
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.invoker().getServer().execute(() -> {
                MinecraftServer server = net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.invoker().getServer();
                if (server != null) {
                    Fabsyncmod.LOGGER.info("自動保存タスク実行中... 全プレイヤーのインベントリを保存します。"); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
                    saveAllPlayersInventories(server);
                } else {
                    Fabsyncmod.LOGGER.warn("自動保存中にMinecraftServerインスタンスが取得できませんでした。"); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
                }
            });
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        Fabsyncmod.LOGGER.info("インベントリ自動保存タスクを " + intervalSeconds + " 秒間隔で開始しました。"); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
    }

    public static void stopAutoSaveTask() {
        if (autoSaveScheduler != null && !autoSaveScheduler.isShutdown()) {
            autoSaveScheduler.shutdown();
            Fabsyncmod.LOGGER.info("インベントリ自動保存タスクを停止しました。"); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
        }
    }
}