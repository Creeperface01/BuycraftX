package net.buycraft.plugin.bukkit.signs.purchases;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import org.bukkit.Location;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RecentPurchaseSignStorage {
    private final List<RecentPurchaseSignPosition> signs = new ArrayList<>();
    private transient final Gson gson = new Gson();

    public void addSign(RecentPurchaseSignPosition location) {
        signs.add(location);
    }

    public boolean removeSign(RecentPurchaseSignPosition location) {
        return signs.remove(location);
    }

    public boolean removeSign(Location location) {
        for (Iterator<RecentPurchaseSignPosition> it = signs.iterator(); it.hasNext(); ) {
            RecentPurchaseSignPosition psp = it.next();
            if (psp.getLocation().toBukkitLocation().equals(location)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public List<RecentPurchaseSignPosition> getSigns() {
        return ImmutableList.copyOf(signs);
    }

    public boolean containsLocation(Location location) {
        for (RecentPurchaseSignPosition sign : signs) {
            if (sign.getLocation().toBukkitLocation().equals(location))
                return true;
        }
        return false;
    }

    public void load(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                RecentPurchaseSignStorage s = gson.fromJson(reader, RecentPurchaseSignStorage.class);
                signs.addAll(s.signs);
            }
        }
    }

    public void save(Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            gson.toJson(this, writer);
        }
    }
}
