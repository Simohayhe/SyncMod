package net.simohaya;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerPlayerEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public class PlayerEventHandler {

    public static void registerEvents() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            Fabsyncmod.LOGGER.info(newPlayer.getName().getString() + " がリスポーン/ログインしました。インベントリをロード中..."); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            InventorySync.loadPlayerInventory(newPlayer);
        });

        ServerPlayerEvents.DISCONNECT.register(player -> {
            Fabsyncmod.LOGGER.info(player.getName().getString() + " がログアウトしました。インベントリを保存中..."); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            InventorySync.savePlayerInventory(player);
        });
    }
}