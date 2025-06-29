package net.simohaya;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Fabsyncmod implements ModInitializer {
	public static final String MOD_ID = "fab_sync_mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Fabsyncmodを初期化しています...");

		ConfigManager.ConfigData config = ConfigManager.loadOrCreateConfig();

		if (!DatabaseManager.initializeDatabase(
				config.mysqlHost,
				config.mysqlDatabase,
				config.mysqlUser,
				config.mysqlPassword
		)) {
			LOGGER.error("MySQLデータベースへの接続に失敗しました。サーバーの起動を中止します。");
			throw new RuntimeException("Fabsyncmod: MySQL接続に失敗しました。サーバーの起動を中止します。");
		}

		// サーバー起動時にDatabaseManagerにRegistryLookupを設定し、InventorySyncにサーバーインスタンスを設定
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			DatabaseManager.setRegistryLookup(server); // ★ DatabaseManagerにRegistryLookupを設定
			InventorySync.setServerInstance(server);   // ★ InventorySyncにサーバーインスタンスを設定
		});

		PlayerEventHandler.registerEvents(); // PlayerEventHandlerのイベント登録はここで行う

		InventorySync.startAutoSaveTask(config.autoSaveIntervalSeconds);

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("サーバーシャットダウン中... 全プレイヤーのインベントリを保存します。");
			InventorySync.saveAllPlayersInventories(server);
			DatabaseManager.closeConnection();
			LOGGER.info("全プレイヤーのインベントリ保存とデータベース接続のクローズが完了しました。");
		});

		LOGGER.info("Fabsyncmodの初期化が正常に完了しました。");
	}
}