package myau.management;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.Myau;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class StatsManager {
    private static final StatsManager INSTANCE = new StatsManager();

    private static final String API_KEY = "5cd03e63-7ec3-4e00-a085-3db3f78ba503";
    private static final String HYPIXEL_API_URL = "https://api.hypixel.net/v2/player";
    private static final String MOJANG_API_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final long CACHE_DURATION = 60000;

    private final ConcurrentHashMap<String, String> uuidCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedKDR> kdrCache = new ConcurrentHashMap<>();

    private StatsManager() {}

    public static StatsManager getInstance() {
        return INSTANCE;
    }

    private static class CachedKDR {
        private final double skywarsKDR;
        private final double bedwarsKDR;
        private final long timestamp;

        public CachedKDR(double skywarsKDR, double bedwarsKDR) {
            this.skywarsKDR = skywarsKDR;
            this.bedwarsKDR = bedwarsKDR;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isValid() {
            return System.currentTimeMillis() - timestamp < CACHE_DURATION;
        }

        public double getSkywarsKDR() { return skywarsKDR; }
        public double getBedwarsKDR() { return bedwarsKDR; }
    }

    public CompletableFuture<String> getSkyWarsKDR(String playerName) {
        return getKDR(playerName, "SKYWARS");
    }

    public CompletableFuture<String> getBedWarsKDR(String playerName) {
        return getKDR(playerName, "BEDWARS");
    }

    public CompletableFuture<String[]> getBothKDR(String playerName) {
        CachedKDR cached = kdrCache.get(playerName);
        if (cached != null && cached.isValid()) {
            return CompletableFuture.completedFuture(new String[]{
                    formatKDR(cached.getSkywarsKDR()),
                    formatKDR(cached.getBedwarsKDR())
            });
        }

        CompletableFuture<String[]> result = new CompletableFuture<>();

        getPlayerUUID(playerName).thenAccept(uuid -> {
            if (uuid == null) {
                result.complete(new String[]{"0", "0"});
                return;
            }

            fetchPlayerStats(uuid).thenAccept(json -> {
                if (json == null || !json.has("success") || !json.get("success").getAsBoolean()) {
                    result.complete(new String[]{"0", "0"});
                    return;
                }

                JsonObject player = json.getAsJsonObject("player");
                if (player == null) {
                    result.complete(new String[]{"0", "0"});
                    return;
                }

                double swKDR = extractSkyWarsKDR(player);
                double bwKDR = extractBedWarsKDR(player);
                kdrCache.put(playerName, new CachedKDR(swKDR, bwKDR));
                result.complete(new String[]{formatKDR(swKDR), formatKDR(bwKDR)});
            }).exceptionally(ex -> {
                result.complete(new String[]{"0", "0"});
                return null;
            });
        }).exceptionally(ex -> {
            result.complete(new String[]{"0", "0"});
            return null;
        });

        return result;
    }

    private CompletableFuture<String> getKDR(String playerName, String mode) {
        CachedKDR cached = kdrCache.get(playerName);
        if (cached != null && cached.isValid()) {
            if ("SKYWARS".equals(mode)) {
                return CompletableFuture.completedFuture(formatKDR(cached.getSkywarsKDR()));
            } else if ("BEDWARS".equals(mode)) {
                return CompletableFuture.completedFuture(formatKDR(cached.getBedwarsKDR()));
            }
        }

        CompletableFuture<String> result = new CompletableFuture<>();

        getPlayerUUID(playerName).thenAccept(uuid -> {
            if (uuid == null) {
                result.complete("0");
                return;
            }

            fetchPlayerStats(uuid).thenAccept(json -> {
                if (json == null || !json.has("success") || !json.get("success").getAsBoolean()) {
                    result.complete("0");
                    return;
                }

                JsonObject player = json.getAsJsonObject("player");
                if (player == null) {
                    result.complete("0");
                    return;
                }

                double kdr;
                if ("SKYWARS".equals(mode)) {
                    kdr = extractSkyWarsKDR(player);
                } else if ("BEDWARS".equals(mode)) {
                    kdr = extractBedWarsKDR(player);
                } else {
                    kdr = 0.0;
                }

                double swKDR = extractSkyWarsKDR(player);
                double bwKDR = extractBedWarsKDR(player);
                kdrCache.put(playerName, new CachedKDR(swKDR, bwKDR));

                result.complete(formatKDR(kdr));
            }).exceptionally(ex -> {
                result.complete("0");
                return null;
            });
        }).exceptionally(ex -> {
            result.complete("0");
            return null;
        });

        return result;
    }

    private CompletableFuture<JsonObject> fetchPlayerStats(String uuid) {
        CompletableFuture<JsonObject> result = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                String urlString = HYPIXEL_API_URL + "?key=" + API_KEY + "&uuid=" + uuid;
                URL url = URI.create(urlString).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() != 200) {
                    result.complete(null);
                    return;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    result.complete(JsonParser.parseString(response.toString()).getAsJsonObject());
                }
            } catch (Exception e) {
                result.complete(null);
            }
        });

        return result;
    }

    private CompletableFuture<String> getPlayerUUID(String playerName) {
        if (uuidCache.containsKey(playerName)) {
            return CompletableFuture.completedFuture(uuidCache.get(playerName));
        }

        CompletableFuture<String> result = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                String urlString = MOJANG_API_URL + playerName;
                URL url = URI.create(urlString).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() != 200) {
                    result.complete(null);
                    return;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String response = reader.lines().reduce("", (a, b) -> a + b);
                    JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                    String uuid = json.get("id").getAsString();
                    uuid = uuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
                    uuidCache.put(playerName, uuid);
                    result.complete(uuid);
                }
            } catch (Exception e) {
                result.complete(null);
            }
        });

        return result;
    }

    private double extractSkyWarsKDR(JsonObject player) {
        JsonObject stats = player.getAsJsonObject("stats");
        if (stats == null) return 0.0;

        JsonObject skywars = stats.getAsJsonObject("SkyWars");
        if (skywars == null) return 0.0;

        int kills = skywars.has("kills") ? skywars.get("kills").getAsInt() : 0;
        int deaths = skywars.has("deaths") ? skywars.get("deaths").getAsInt() : 0;

        if (deaths == 0) return kills > 0 ? kills : 0.0;
        return (double) kills / deaths;
    }

    private double extractBedWarsKDR(JsonObject player) {
        JsonObject stats = player.getAsJsonObject("stats");
        if (stats == null) return 0.0;

        JsonObject bedwars = stats.getAsJsonObject("Bedwars");
        if (bedwars == null) return 0.0;

        int kills = bedwars.has("final_kills_bedwars") ? bedwars.get("final_kills_bedwars").getAsInt() : 0;
        int deaths = bedwars.has("final_deaths_bedwars") ? bedwars.get("final_deaths_bedwars").getAsInt() : 0;

        if (deaths == 0) return kills > 0 ? kills : 0.0;
        return (double) kills / deaths;
    }

    private String formatKDR(double kdr) {
        if (kdr == Math.floor(kdr)) {
            return String.format("%.0f", kdr);
        }
        return String.format("%.2f", kdr);
    }
}