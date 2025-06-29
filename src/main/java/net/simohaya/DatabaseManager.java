package net.simohaya;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.collection.DefaultedList;
import com.mojang.serialization.DynamicOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtSizeTracker; // NbtSizeTrackerをインポート

public class DatabaseManager {
    private static Connection connection;
    private static RegistryWrapper.WrapperLookup registryLookup;

    public static boolean initializeDatabase(String host, String database, String user, String password) {
        String url = "jdbc:mysql://" + host + ":3306/" + database + "?useSSL=false&serverTimezone=UTC";
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(url, user, password);
            Fabsyncmod.LOGGER.info("MySQLデータベースに正常に接続しました: " + database);
            createInventoryTable();
            return true;
        } catch (SQLException e) {
            Fabsyncmod.LOGGER.error("MySQLデータベースへの接続に失敗しました！", e);
            return false;
        } catch (ClassNotFoundException e) {
            Fabsyncmod.LOGGER.error("MySQL JDBCドライバが見つかりません！", e);
            return false;
        }
    }

    public static void setRegistryLookup(MinecraftServer server) {
        registryLookup = server.getRegistryManager();
        if (registryLookup != null) {
            Fabsyncmod.LOGGER.info("RegistryLookupが設定されました。");
        } else {
            Fabsyncmod.LOGGER.warn("RegistryLookupの取得に失敗しました。NBTのエンコード/デコードに問題が発生する可能性があります。");
        }
    }

    private static void createInventoryTable() {
        if (connection == null) {
            Fabsyncmod.LOGGER.error("データベース接続がありません。テーブルを作成できません。");
            return;
        }
        String createTableSQL = "CREATE TABLE IF NOT EXISTS player_inventories (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "inventory_data BLOB NOT NULL" +
                ");";
        try (PreparedStatement statement = connection.prepareStatement(createTableSQL)) {
            statement.executeUpdate();
            Fabsyncmod.LOGGER.info("テーブル 'player_inventories' が正常に作成または確認されました。");
        } catch (SQLException e) {
            Fabsyncmod.LOGGER.error("テーブル 'player_inventories' の作成に失敗しました！", e);
        }
    }

    /**
     * プレイヤーのインベントリをMySQLデータベースに保存または更新します。
     * ItemStackの encode/decode には DynamicOps が必要です。
     */
    public static void saveInventory(UUID playerUuid, DefaultedList<ItemStack> inventory) {
        if (connection == null) {
            Fabsyncmod.LOGGER.error("データベース接続がありません。インベントリを保存できません。");
            return;
        }
        if (registryLookup == null) {
            Fabsyncmod.LOGGER.error("RegistryLookupが設定されていません。インベントリを保存できません。");
            return;
        }

        try {
            DynamicOps<NbtElement> registryOps = RegistryOps.of(NbtOps.INSTANCE, registryLookup);

            NbtCompound rootNbt = new NbtCompound();
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.get(i);
                if (!stack.isEmpty()) {
                    rootNbt.put("slot_" + i, ItemStack.CODEC.encodeStart(registryOps, stack).getOrThrow());
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
                Fabsyncmod.LOGGER.info("プレイヤー " + playerUuid.toString() + " のインベントリを正常に保存しました。");
            }
        } catch (SQLException | IOException e) {
            Fabsyncmod.LOGGER.error("プレイヤー " + playerUuid.toString() + " のインベントリ保存中にエラーが発生しました。", e);
        }
    }

    /**
     * プレイヤーのインベントリをMySQLデータベースから読み込みます。
     */
    public static DefaultedList<ItemStack> loadInventory(UUID playerUuid) {
        DefaultedList<ItemStack> loadedInventory = DefaultedList.ofSize(41, ItemStack.EMPTY);

        if (connection == null) {
            Fabsyncmod.LOGGER.error("データベース接続がありません。インベントリをロードできません。");
            return loadedInventory;
        }
        if (registryLookup == null) {
            Fabsyncmod.LOGGER.error("RegistryLookupが設定されていません。インベントriをロードできません。");
            return loadedInventory;
        }

        DynamicOps<NbtElement> registryOps = RegistryOps.of(NbtOps.INSTANCE, registryLookup);

        String selectSQL = "SELECT inventory_data FROM player_inventories WHERE uuid = ?;";
        try (PreparedStatement statement = connection.prepareStatement(selectSQL)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    byte[] data = resultSet.getBytes("inventory_data");
                    if (data != null && data.length > 0) {
                        ByteArrayInputStream bis = new ByteArrayInputStream(data);
                        // ★ 修正済み：NbtSizeTrackerのコンストラクタ引数を修正
                        // NbtSizeTracker(long maxBytes, int maxNbtDepth)
                        // デフォルト値として、maxBytes = 2097152L (2MB), maxNbtDepth = 512 がよく使われます。
                        NbtCompound rootNbt = NbtIo.readCompressed(bis, new NbtSizeTracker(2097152L, 512)); // ★ ここを修正

                        for (int i = 0; i < loadedInventory.size(); i++) {
                            String slotKey = "slot_" + i;
                            if (rootNbt.contains(slotKey)) {
                                Optional<ItemStack> itemStackOptional = ItemStack.CODEC.decode(registryOps, rootNbt.get(slotKey)).result().map(pair -> pair.getFirst());
                                if (itemStackOptional.isPresent()) {
                                    loadedInventory.set(i, itemStackOptional.get());
                                } else {
                                    Fabsyncmod.LOGGER.warn("スロット " + i + " のアイテムのデコードに失敗しました。");
                                }
                            }
                        }
                        Fabsyncmod.LOGGER.info("プレイヤー " + playerUuid.toString() + " のインベントリを正常にロードしました。");
                    } else {
                        Fabsyncmod.LOGGER.warn("プレイヤー " + playerUuid.toString() + " のインベントリデータがデータベースに空で保存されています。");
                    }
                } else {
                    Fabsyncmod.LOGGER.warn("プレイヤー " + playerUuid.toString() + " のインベントリデータがデータベースに見つかりません。新規のインベントリで開始します。");
                }
            }
        } catch (SQLException | IOException e) {
            Fabsyncmod.LOGGER.error("プレイヤー " + playerUuid.toString() + " のインベントリロード中にエラーが発生しました。", e);
        }
        return loadedInventory;
    }

    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                Fabsyncmod.LOGGER.info("MySQLデータベース接続を閉じました。");
            } catch (SQLException e) {
                Fabsyncmod.LOGGER.error("MySQLデータベース接続のクローズに失敗しました。", e);
            }
        }
    }

    public static Connection getConnection() {
        return connection;
    }
}