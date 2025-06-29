package net.simohaya;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.collection.DefaultedList;

public class DatabaseManager {
    private static Connection connection;

    public static boolean initializeDatabase(String host, String database, String user, String password) {
        String url = "jdbc:mysql://" + host + ":3306/" + database + "?useSSL=false&serverTimezone=UTC";
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(url, user, password);
            Fabsyncmod.LOGGER.info("MySQLデータベースに正常に接続しました: " + database); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            createInventoryTable();
            return true;
        } catch (SQLException e) {
            Fabsyncmod.LOGGER.error("MySQLデータベースへの接続に失敗しました！", e); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            return false;
        } catch (ClassNotFoundException e) {
            Fabsyncmod.LOGGER.error("MySQL JDBCドライバが見つかりません！", e); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            return false;
        }
    }

    private static void createInventoryTable() {
        if (connection == null) {
            Fabsyncmod.LOGGER.error("データベース接続がありません。テーブルを作成できません。"); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            return;
        }
        String createTableSQL = "CREATE TABLE IF NOT EXISTS player_inventories (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "inventory_data BLOB NOT NULL" +
                ");";
        try (PreparedStatement statement = connection.prepareStatement(createTableSQL)) {
            statement.executeUpdate();
            Fabsyncmod.LOGGER.info("テーブル 'player_inventories' が正常に作成または確認されました。"); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
        } catch (SQLException e) {
            Fabsyncmod.LOGGER.error("テーブル 'player_inventories' の作成に失敗しました！", e); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
        }
    }

    public static void saveInventory(UUID playerUuid, DefaultedList<ItemStack> inventory) {
        if (connection == null) {
            Fabsyncmod.LOGGER.error("データベース接続がありません。インベントリを保存できません。"); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            return;
        }

        try {
            NbtCompound rootNbt = new NbtCompound();
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.get(i);
                if (!stack.isEmpty()) {
                    rootNbt.put("slot_" + i, stack.writeNbt(new NbtCompound()));
                }
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(rootNbt, bos);
            byte[] data = bos.toByteArray();

            String upsertSQL = "INSERT INTO player_inventories (uuid, inventory_data) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE inventory_data = VALUES(inventory_data);";
            try (PreparedStatement statement = connection.prepareStatement(upsertSQL)) {
                statement.setString(1, playerUuid.toString());
                statement.setBytes(2, data);
                statement.executeUpdate();
                Fabsyncmod.LOGGER.info("プレイヤー " + playerUuid.toString() + " のインベントリを正常に保存しました。"); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            }
        } catch (SQLException | IOException e) {
            Fabsyncmod.LOGGER.error("プレイヤー " + playerUuid.toString() + " のインベントリ保存中にエラーが発生しました。", e); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
        }
    }

    public static DefaultedList<ItemStack> loadInventory(UUID playerUuid) {
        DefaultedList<ItemStack> loadedInventory = DefaultedList.ofSize(41, ItemStack.EMPTY);

        if (connection == null) {
            Fabsyncmod.LOGGER.error("データベース接続がありません。インベントリをロードできません。"); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            return loadedInventory;
        }

        String selectSQL = "SELECT inventory_data FROM player_inventories WHERE uuid = ?;";
        try (PreparedStatement statement = connection.prepareStatement(selectSQL)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    byte[] data = resultSet.getBytes("inventory_data");
                    if (data != null && data.length > 0) {
                        ByteArrayInputStream bis = new ByteArrayInputStream(data);
                        NbtCompound rootNbt = NbtIo.readCompressed(bis);

                        for (int i = 0; i < loadedInventory.size(); i++) {
                            String slotKey = "slot_" + i;
                            if (rootNbt.contains(slotKey)) {
                                loadedInventory.set(i, ItemStack.fromNbt(rootNbt.getCompound(slotKey)));
                            }
                        }
                        Fabsyncmod.LOGGER.info("プレイヤー " + playerUuid.toString() + " のインベントリを正常にロードしました。"); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
                    } else {
                        Fabsyncmod.LOGGER.warn("プレイヤー " + playerUuid.toString() + " のインベントリデータがデータベースに空で保存されています。"); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
                    }
                } else {
                    Fabsyncmod.LOGGER.warn("プレイヤー " + playerUuid.toString() + " のインベントリデータがデータベースに見つかりません。新規のインベントリで開始します。"); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
                }
            }
        } catch (SQLException | IOException e) {
            Fabsyncmod.LOGGER.error("プレイヤー " + playerUuid.toString() + " のインベントリロード中にエラーが発生しました。", e); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
        }
        return loadedInventory;
    }

    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                Fabsyncmod.LOGGER.info("MySQLデータベース接続を閉じました。"); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            } catch (SQLException e) {
                Fabsyncmod.LOGGER.error("MySQLデータベース接続のクローズに失敗しました。", e); // ★ SyncMod.LOGGER -> Fabsyncmod.LOGGER に変更
            }
        }
    }

    public static Connection getConnection() {
        return connection;
    }
}