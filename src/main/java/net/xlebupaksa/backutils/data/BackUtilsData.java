package net.xlebupaksa.backutils.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Holds one manager per database file and initialises them together. */
public class BackUtilsData {

    private final NameData nameData;
    private final ChatData chatData;
    private final LogData logData;
    private final ActiveData activeData;
    private final SilenceData silenceData;
    private final MusicZoneData musicZoneData;

    public BackUtilsData(Path worldPath) {
        Path dbDir = worldPath.resolve("serverconfig").resolve("backutils");
        try {
            Files.createDirectories(dbDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create backutils dir", e);
        }

        this.nameData = new NameData(worldPath);
        this.chatData = new ChatData(worldPath);
        this.logData = new LogData(worldPath);
        this.activeData = new ActiveData(worldPath);
        this.silenceData = new SilenceData(worldPath);
        this.musicZoneData = new MusicZoneData(worldPath);

        initAll();
    }

    public void initAll() {
        nameData.initDb();
        chatData.initDb();
        logData.initDb();
        activeData.initDb();
        silenceData.initDb();
        musicZoneData.initDb();
    }

    public void closeAll() {
        nameData.close();
        chatData.close();
        logData.close();
        activeData.close();
        silenceData.close();
        musicZoneData.close();
    }

    public NameData names() { return nameData; }
    public ChatData chats() { return chatData; }
    public LogData logs() { return logData; }
    public ActiveData active() { return activeData; }
    public SilenceData silences() { return silenceData; }
    public MusicZoneData musicZones() { return musicZoneData; }
}