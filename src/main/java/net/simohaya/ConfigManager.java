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

    public static class ConfigData {
        public String mysqlHost;
        public String mysqlDatabase;
        public String mysqlUser;
        public String mysqlPassword;
        public int autoSaveIntervalSeconds;

        public ConfigData() {}

        public ConfigData(String host, String db, String user, String pass, int autoSaveInterval) {
            this.mysqlHost = host;
            this.mysqlDatabase = db;
            this.mysqlUser = user;
            this.mysqlPassword = pass;
            this.autoSaveIntervalSeconds = autoSaveInterval;
        }
    }

    public static ConfigData loadOrCreateConfig() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(Fabsyncmod.MOD_ID); // ★ SyncMod.MOD_ID -> Fabsyncmod.MOD_ID に変更
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);

        if (!Files.exists(configDir)) {
            try {
                Files.createDirectories(configDir);
                Fabsyncmod.LOGGER.info("設定ディレクトリを作成しました: " + configDir.toString()); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            } catch (IOException e) {
                Fabsyncmod.LOGGER.error("設定ディレクトリの作成に失敗しました: " + configDir.toString(), e); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
                config = createDefaultConfig();
                saveConfig(config);
                return config;
            }
        }

        if (!Files.exists(configFile)) {
            Fabsyncmod.LOGGER.info("設定ファイルが見つかりません。デフォルト設定を生成します: " + configFile.toString()); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            config = createDefaultConfig();
            saveConfig(config);
            return config;
        }

        try (FileReader reader = new FileReader(configFile.toFile())) {
            config = GSON.fromJson(reader, ConfigData.class);
            if (config == null) {
                Fabsyncmod.LOGGER.warn("設定ファイルが空か、形式が不正です。デフォルト設定を生成します。"); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
                config = createDefaultConfig();
                saveConfig(config);
            }
            if (config.autoSaveIntervalSeconds <= 0) {
                config.autoSaveIntervalSeconds = createDefaultConfig().autoSaveIntervalSeconds;
                Fabsyncmod.LOGGER.warn("設定ファイルに自動保存間隔の項目が見つからないか不正です。デフォルト値 " + config.autoSaveIntervalSeconds + " 秒を適用しました。"); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
                saveConfig(config);
            }
            Fabsyncmod.LOGGER.info("設定ファイルを正常にロードしました: " + configFile.toString()); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            return config;
        } catch (IOException e) {
            Fabsyncmod.LOGGER.error("設定ファイルの読み込みに失敗しました: " + configFile.toString(), e); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            config = createDefaultConfig();
            saveConfig(config);
            return config;
        } catch (JsonSyntaxException e) {
            Fabsyncmod.LOGGER.error("設定ファイルのJSON構文が不正です。デフォルト設定を生成します。", e); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            config = createDefaultConfig();
            saveConfig(config);
            return config;
        }
    }

    public static void saveConfig(ConfigData configData) {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(Fabsyncmod.MOD_ID); // ★ SyncMod.MOD_ID -> Fabsyncmod.MOD_ID に変更
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);

        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            GSON.toJson(configData, writer);
            Fabsyncmod.LOGGER.info("設定ファイルを正常に保存しました: " + configFile.toString()); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
        } catch (IOException e) {
            Fabsyncmod.LOGGER.error("設定ファイルの保存に失敗しました: " + configFile.toString(), e); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
        }
    }

    private static ConfigData createDefaultConfig() {
        return new ConfigData("localhost", "minecraftDB", "root", "simo", 5);
    }

    public static ConfigData getConfig() {
        return config;
    }
}