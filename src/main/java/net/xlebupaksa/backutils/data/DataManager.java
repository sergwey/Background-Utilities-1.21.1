package net.xlebupaksa.backutils.data;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final String nameConnectionString;
    private final String chatConnectionString;
    private final String activeConnectionString;
    private final String logConnectionString;
    private Connection connection;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    public DataManager(Path worldPath){
        Path dbPath = worldPath.resolve("serverconfig").resolve("backutils");
        try{
            Files.createDirectories(dbPath);
        } catch (IOException e){
            e.printStackTrace();
        }
        this.nameConnectionString = "jdbc:sqlite:" + dbPath.resolve("name_profile.db");
        this.chatConnectionString = "jdbc:sqlite:" + dbPath.resolve("chat_profile.db");
        this.activeConnectionString = "jdbc:sqlite:" + dbPath.resolve("active_profile.db");
        this.logConnectionString = "jdbc:sqlite:" + dbPath.resolve("action_log.db");

        initNamesDb();
        initChatDb();
        initActiveDb();
        initLogDb();
    }

    private void initNamesDb() {
        try(
                Connection connection = DriverManager.getConnection(nameConnectionString);
                Statement stmt = connection.createStatement()
        ){
            Class.forName("org.sqlite.JDBC");
            stmt.execute("CREATE TABLE IF NOT EXISTS name_profile (" +
                    "name_profile_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name_profile_name TEXT NOT NULL, " +
                    "displayed_name TEXT NOT NULL, " +
                    "player_name TEXT NOT NULL" +
                    ");");


        } catch (ClassNotFoundException e){
            throw new RuntimeException("Eblan, where is your SQLite driver? at names", e);
        } catch (SQLException e){
            throw new RuntimeException("Epic SQL connection fail at names", e);
        }
    }
    private void initChatDb() {
        try(
                Connection connection = DriverManager.getConnection(chatConnectionString);
                Statement stmt = connection.createStatement()
        ){
            Class.forName("org.sqlite.JDBC");
            stmt.execute("CREATE TABLE IF NOT EXISTS chat_profile (" +
                    "chat_profile_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "chat_profile_name TEXT NOT NULL, " +
                    "chat_format TEXT NOT NULL, " +
                    "player_name TEXT NOT NULL" +
                    ");");


        } catch (ClassNotFoundException e){
            throw new RuntimeException("Eblan, where is your SQLite driver? at chat", e);
        } catch (SQLException e){
            throw new RuntimeException("Epic SQL connection fail at chat", e);
        }
    }
    private void initActiveDb() {
        try(
                Connection connection = DriverManager.getConnection(activeConnectionString);
                Statement stmt = connection.createStatement()
        ){
            Class.forName("org.sqlite.JDBC");
            stmt.execute("CREATE TABLE IF NOT EXISTS active_profile (" +
                    "player_name TEXT PRIMARY KEY, " +
                    "name_profile_id INTEGER NOT NULL, " +
                    "chat_profile_id INTEGER NOT NULL" +
                    ");");


        } catch (ClassNotFoundException e){
            throw new RuntimeException("Eblan, where is your SQLite driver? at active", e);
        } catch (SQLException e){
            throw new RuntimeException("Epic SQL connection fail at active", e);
        }
    }
    private void initLogDb() {
        try (
                Connection connection = DriverManager.getConnection(logConnectionString);
                Statement stmt = connection.createStatement()
        ){
            Class.forName("org.sqlite.JDBC");
            stmt.execute("CREATE TABLE IF NOT EXISTS action_log (" +
                    "log_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "access_list TEXT NOT NULL, " +
                    "contents TEXT NOT NULL," +
                    "created_at DATETIME DEFAULT (datetime('now', 'localtime')), " +
                    ");");
        } catch (ClassNotFoundException e){
            throw new RuntimeException("Eblan, where is your SQLite driver? at log", e);
        } catch (SQLException e){
            throw new RuntimeException("Epic SQL connection fail at log", e);
        }
    }

}
