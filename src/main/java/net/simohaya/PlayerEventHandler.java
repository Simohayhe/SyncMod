package net.simohaya;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents; // このインポートが必要です

public class PlayerEventHandler {

    public static void registerEvents() {
        // サーバーが完全に起動した際にRegistryLookupを設定
        // このイベントはFabsyncmod.javaでも登録されているため、ここではInventorySync.setServerInstanceのみに絞ります。
        // DatabaseManager.setRegistryLookup() はFabsyncmod.javaで呼び出すのがより明確です。
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // InventorySyncにサーバーインスタンスを渡す
            InventorySync.setServerInstance(server);
            // DatabaseManager.setRegistryLookup(server); はFabsyncmod.javaのServerLifecycleEvents.SERVER_STARTEDで呼び出すべきです。
        });


        // ★ 修正済み：プレイヤーがサーバーにログインした際のイベント
        // ServerPlayConnectionEvents.JOIN を使用してプレイヤーの接続イベントを処理します。
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // handler.player で ServerPlayerEntity を取得できます。
            Fabsyncmod.LOGGER.info(handler.player.getName().getString() + " がログインしました。インベントリをロード中...");
            InventorySync.loadPlayerInventory(handler.player);
        });

        // ★ 修正済み：プレイヤーがサーバーから切断（ログアウト）した際のイベント
        // ServerPlayConnectionEvents.DISCONNECT を使用してプレイヤーの切断イベントを処理します。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // handler.player で ServerPlayerEntity を取得できます。
            Fabsyncmod.LOGGER.info(handler.player.getName().getString() + " がログアウトしました。インベントリを保存中...");
            InventorySync.savePlayerInventory(handler.player);
        });
    }
}