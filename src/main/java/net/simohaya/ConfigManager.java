package net.simohaya;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {

    private static final String CONFIG_FILE_NAME = "SyncConf.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ConfigData config;

    /**
     * 設定データの内部クラス。JSONファイルとJavaオブジェクトのマッピングに使用。
     */
    public static class ConfigData {
        public String mysqlHost;
        public int mysqlPort; // ★ 新しく追加：MySQLポート
        public String mysqlDatabase;
        public String mysqlUser;
        public String mysqlPassword;
        public int autoSaveIntervalSeconds;

        public ConfigData() {}

        // ★ コンストラクタを更新：mysqlPort引数を追加
        public ConfigData(String host, int port, String db, String user, String pass, int autoSaveInterval) {
            this.mysqlHost = host;
            this.mysqlPort = port; // ★ 追加
            this.mysqlDatabase = db;
            this.mysqlUser = user;
            this.mysqlPassword = pass;
            this.autoSaveIntervalSeconds = autoSaveInterval;
        }
    }

    /**
     * 設定ファイルを読み込むか、存在しない場合はデフォルト値で生成する。
     */
    public static ConfigData loadOrCreateConfig() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(Fabsyncmod.MOD_ID);
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);

        if (!Files.exists(configDir)) {
            try {
                Files.createDirectories(configDir);
                Fabsyncmod.LOGGER.info("設定ディレクトリを作成しました: " + configDir.toString());
            } catch (IOException e) {
                Fabsyncmod.LOGGER.error("設定ディレクトリの作成に失敗しました: " + configDir.toString(), e);
                config = createDefaultConfig();
                saveConfig(config);
                return config;
            }
        }

        if (!Files.exists(configFile)) {
            Fabsyncmod.LOGGER.info("設定ファイルが見つかりません。デフォルト設定を生成します: " + configFile.toString());
            config = createDefaultConfig();
            saveConfig(config);
            return config;
        }

        try (FileReader reader = new FileReader(configFile.toFile())) {
            config = GSON.fromJson(reader, ConfigData.class);
            if (config == null) {
                Fabsyncmod.LOGGER.warn("設定ファイルが空か、形式が不正です。デフォルト設定を生成します。");
                config = createDefaultConfig();
                saveConfig(config);
            }
            // 新しい設定項目（mysqlPort, autoSaveIntervalSeconds）が設定ファイルにない場合、デフォルト値を適用し、ファイルを更新
            boolean updatedConfig = false;
            if (config.mysqlPort == 0) { // ポートが未設定（または0）の場合
                config.mysqlPort = createDefaultConfig().mysqlPort;
                Fabsyncmod.LOGGER.warn("設定ファイルにMySQLポートの項目が見つからないか不正です。デフォルト値 " + config.mysqlPort + " を適用しました。");
                updatedConfig = true;
            }
            if (config.autoSaveIntervalSeconds <= 0) {
                config.autoSaveIntervalSeconds = createDefaultConfig().autoSaveIntervalSeconds;
                Fabsyncmod.LOGGER.warn("設定ファイルに自動保存間隔の項目が見つからないか不正です。デフォルト値 " + config.autoSaveIntervalSeconds + " 秒を適用しました。");
                updatedConfig = true;
            }
            if (updatedConfig) {
                saveConfig(config); // デフォルト値を適用した場合はファイルに保存
            }

            Fabsyncmod.LOGGER.info("設定ファイルを正常にロードしました: " + configFile.toString());
            return config;
        } catch (IOException e) {
            Fabsyncmod.LOGGER.error("設定ファイルの読み込みに失敗しました: " + configFile.toString(), e);
            config = createDefaultConfig();
            saveConfig(config);
            return config;
        } catch (JsonSyntaxException e) {
            Fabsyncmod.LOGGER.error("設定ファイルのJSON構文が不正です。デフォルト設定を生成します。", e);
            config = createDefaultConfig();
            saveConfig(config);
            return config;
        }
    }

    /**
     * 現在の設定データをファイルに保存する。
     */
    public static void saveConfig(ConfigData configData) {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(Fabsyncmod.MOD_ID);
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);

        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            GSON.toJson(configData, writer);
            Fabsyncmod.LOGGER.info("設定ファイルを正常に保存しました: " + configFile.toString());
        } catch (IOException e) {
            Fabsyncmod.LOGGER.error("設定ファイルの保存に失敗しました: " + configFile.toString(), e);
        }
    }

    /**
     * デフォルトの設定データを生成する。
     */
    private static ConfigData createDefaultConfig() {
        // ★ MySQL接続情報のデフォルト値をプレースホルダーに変更し、ポートを追加
        return new ConfigData("localhost", 3306, "your_database_name", "your_username", "your_password", 5);
    }

    /**
     * 現在読み込まれている設定データを取得する。
     */
    public static ConfigData getConfig() {
        return config;
    }
}