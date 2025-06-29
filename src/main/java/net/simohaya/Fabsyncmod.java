package net.simohaya;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Fabsyncmod implements ModInitializer { // ★ クラス名をFabsyncmodに変更
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

		PlayerEventHandler.registerEvents();

		// 定期的なインベントリ保存タスクの開始
		InventorySync.startAutoSaveTask(config.autoSaveIntervalSeconds);

		// サーバーシャットダウンイベントの登録
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("サーバーシャットダウン中... 全プレイヤーのインベントリを保存します。");
			InventorySync.saveAllPlayersInventories(server);
			DatabaseManager.closeConnection();
			LOGGER.info("全プレイヤーのインベントリ保存とデータベース接続のクローズが完了しました。");
		});

		LOGGER.info("Fabsyncmodの初期化が正常に完了しました。");
	}
}