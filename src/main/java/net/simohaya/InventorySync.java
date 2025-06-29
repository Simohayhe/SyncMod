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
    private static MinecraftServer currentServerInstance; // サーバーインスタンスを保持するための変数

    /**
     * プレイヤーのインベントリをサーバーのMySQLデータベースに保存します。
     * @param player 保存するプレイヤーエンティティ
     */
    public static void savePlayerInventory(ServerPlayerEntity player) {
        // プレイヤーのメインインベントリ、防具、オフハンドの全てのアイテムを取得
        DefaultedList<ItemStack> inventoryToSave = DefaultedList.ofSize(player.getInventory().size(), ItemStack.EMPTY);
        for (int i = 0; i < player.getInventory().size(); i++) {
            inventoryToSave.set(i, player.getInventory().getStack(i));
        }

        DatabaseManager.saveInventory(player.getUuid(), inventoryToSave);
        player.sendMessage(Text.of("§aインベントリをサーバーに保存しました。"), false);
    }

    /**
     * プレイヤーのインベントリをサーバーのMySQLデータベースからロードし、プレイヤーのインベントリを更新します。
     * @param player ロードするプレイヤーエンティティ
     */
    public static void loadPlayerInventory(ServerPlayerEntity player) {
        DefaultedList<ItemStack> loadedInventory = DatabaseManager.loadInventory(player.getUuid());

        player.getInventory().clear(); // 現在のインベントリをクリア
        for (int i = 0; i < loadedInventory.size(); i++) {
            player.getInventory().setStack(i, loadedInventory.get(i));
        }
        player.sendMessage(Text.of("§aインベントリをサーバーからロードしました。"), false);
    }

    /**
     * 全てのオンラインプレイヤーのインベントリをデータベースに保存します。
     * 主にサーバーシャットダウン時や定期保存時に使用します。
     * @param server Minecraftサーバーインスタンス
     */
    public static void saveAllPlayersInventories(MinecraftServer server) {
        // サーバーがまだ稼働していることを確認
        if (server != null) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                savePlayerInventory(player);
            }
        } else {
            Fabsyncmod.LOGGER.warn("MinecraftServerインスタンスがnullのため、全プレイヤーのインベントリ保存をスキップしました。");
        }
    }

    /**
     * 定期的なインベントリ自動保存タスクを開始します。
     * @param intervalSeconds 保存間隔（秒）
     */
    public static void startAutoSaveTask(int intervalSeconds) {
        if (autoSaveScheduler != null && !autoSaveScheduler.isShutdown()) {
            autoSaveScheduler.shutdownNow(); // 既存のタスクがあれば停止
        }

        autoSaveScheduler = Executors.newSingleThreadScheduledExecutor();
        autoSaveScheduler.scheduleAtFixedRate(() -> {
            // スケジューラーはメインスレッドとは別のスレッドで実行されるため、
            // MinecraftServerインスタンスへのアクセスは注意が必要です。
            // サーバーのTick内で安全に実行するために、サーバー実行キューにタスクを投入します。
            if (currentServerInstance != null) {
                currentServerInstance.execute(() -> { // サーバーのメインスレッドで実行
                    Fabsyncmod.LOGGER.info("自動保存タスク実行中... 全プレイヤーのインベントリを保存します。");
                    saveAllPlayersInventories(currentServerInstance);
                });
            } else {
                Fabsyncmod.LOGGER.warn("自動保存中にMinecraftServerインスタンスが取得できませんでした。サーバーが起動していない可能性があります。");
            }

        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        Fabsyncmod.LOGGER.info("インベントリ自動保存タスクを " + intervalSeconds + " 秒間隔で開始しました。");
    }

    /**
     * 定期的なインベントリ自動保存タスクを停止します。
     * サーバーシャットダウン時に呼び出すことができます。
     */
    public static void stopAutoSaveTask() {
        if (autoSaveScheduler != null && !autoSaveScheduler.isShutdown()) {
            autoSaveScheduler.shutdown();
            Fabsyncmod.LOGGER.info("インベントリ自動保存タスクを停止しました。");
        }
    }

    /**
     * MinecraftServerインスタンスを設定します。
     * FabsyncmodのonInitializeで設定されることを想定しています。
     * @param server 設定するサーバーインスタンス
     */
    public static void setServerInstance(MinecraftServer server) {
        currentServerInstance = server;
    }
}