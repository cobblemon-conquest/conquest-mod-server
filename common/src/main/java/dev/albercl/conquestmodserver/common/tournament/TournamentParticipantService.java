package dev.albercl.conquestmodserver.common.tournament;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Tournament participant registry with JSON persistence.
 */
public final class TournamentParticipantService {
    private static final String STORE_DIRECTORY = "conquest_mod";
    private static final String STORE_FILE = "tournament_participants.json";
    private static final String SCHEMA_VERSION_KEY = "schemaVersion";
    private static final String PARTICIPANTS_KEY = "participants";
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, ParticipantRecord> PARTICIPANTS_BY_NAME = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PARTICIPANT_NAME_BY_PLAYER = new ConcurrentHashMap<>();
    private static final Stat[] COMPETITIVE_STATS_ORDER = new Stat[] {
        Stats.HP,
        Stats.ATTACK,
        Stats.DEFENCE,
        Stats.SPECIAL_ATTACK,
        Stats.SPECIAL_DEFENCE,
        Stats.SPEED
    };

    private static volatile Path loadedStorageFile;
    private static volatile MinecraftServer loadedServer;
    private static volatile boolean loaded;

    private TournamentParticipantService() {
    }

    public static synchronized RegisterResult register(String participantName, ServerPlayer player, MinecraftServer server) {
        ensureLoaded(server);

        String normalizedName = normalizeParticipantName(participantName);
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("El nombre del participante no puede estar vacio.");
        }
        if (PARTICIPANTS_BY_NAME.containsKey(normalizedName)) {
            throw new IllegalStateException("Ya existe un participante registrado con ese nombre.");
        }

        String currentForPlayer = PARTICIPANT_NAME_BY_PLAYER.get(player.getUUID());
        if (currentForPlayer != null) {
            throw new IllegalStateException("Este jugador ya tiene un registro activo como '" + currentForPlayer + "'.");
        }

        CompetitiveTeamSnapshot snapshot = createSnapshot(player);
        ParticipantRecord record = new ParticipantRecord(
            normalizedName,
            participantName,
            player.getUUID(),
            player.getGameProfile().getName(),
            snapshot,
            Instant.now().toEpochMilli()
        );

        PARTICIPANTS_BY_NAME.put(normalizedName, record);
        PARTICIPANT_NAME_BY_PLAYER.put(player.getUUID(), normalizedName);
        persist(server);
        return new RegisterResult(record.displayName(), snapshot.occupiedSlots());
    }

    public static synchronized boolean remove(String participantName, MinecraftServer server) {
        ensureLoaded(server);

        String normalizedName = normalizeParticipantName(participantName);
        ParticipantRecord removed = PARTICIPANTS_BY_NAME.remove(normalizedName);
        if (removed == null) {
            return false;
        }

        String indexedName = PARTICIPANT_NAME_BY_PLAYER.get(removed.playerUuid());
        if (normalizedName.equals(indexedName)) {
            PARTICIPANT_NAME_BY_PLAYER.remove(removed.playerUuid());
        }
        persist(server);
        return true;
    }

    public static synchronized ClearResult clear(MinecraftServer server) {
        ensureLoaded(server);
        int removedParticipants = PARTICIPANTS_BY_NAME.size();
        PARTICIPANTS_BY_NAME.clear();
        PARTICIPANT_NAME_BY_PLAYER.clear();
        persist(server);
        return new ClearResult(removedParticipants);
    }

    public static synchronized CheckResult check(ServerPlayer player, MinecraftServer server) {
        return check(player, server, -1);
    }

    public static synchronized CheckResult check(ServerPlayer player, MinecraftServer server, int requiredMatches) {
        ensureLoaded(server);

        String participantKey = PARTICIPANT_NAME_BY_PLAYER.get(player.getUUID());
        if (participantKey == null) {
            throw new IllegalStateException("Este jugador no tiene participante registrado.");
        }

        ParticipantRecord record = PARTICIPANTS_BY_NAME.get(participantKey);
        if (record == null) {
            PARTICIPANT_NAME_BY_PLAYER.remove(player.getUUID());
            persist(server);
            throw new IllegalStateException("No se encontro el registro del participante asociado a este jugador.");
        }

        int registeredPokemon = record.snapshot().occupiedSlots();
        int effectiveRequiredMatches = requiredMatches <= 0 ? registeredPokemon : requiredMatches;
        if (effectiveRequiredMatches > registeredPokemon) {
            throw new IllegalArgumentException(
                "No se puede requerir " + effectiveRequiredMatches + " coincidencias: el participante solo tiene "
                    + registeredPokemon + " Pokemon registrados."
            );
        }

        CompetitiveTeamSnapshot currentSnapshot = createSnapshot(player);
        CheckComparison comparison = compare(record.snapshot(), currentSnapshot, effectiveRequiredMatches);
        return new CheckResult(
            record.displayName(),
            comparison.matches(),
            comparison.matchedPokemon(),
            comparison.requiredMatches(),
            registeredPokemon,
            comparison.differences()
        );
    }

    public static synchronized Optional<String> getParticipantNameForPlayer(UUID playerUuid) {
        return Optional.ofNullable(PARTICIPANT_NAME_BY_PLAYER.get(playerUuid));
    }

    public static synchronized List<String> listParticipantDisplayNames(MinecraftServer server) {
        ensureLoaded(server);
        List<String> names = new ArrayList<>();
        for (ParticipantRecord record : PARTICIPANTS_BY_NAME.values()) {
            names.add(record.displayName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static synchronized ViewResult view(String participantName, MinecraftServer server) {
        ensureLoaded(server);

        String normalizedName = normalizeParticipantName(participantName);
        ParticipantRecord record = PARTICIPANTS_BY_NAME.get(normalizedName);
        if (record == null) {
            throw new IllegalStateException("No existe un participante registrado con ese username.");
        }

        List<ViewSlotResult> slots = new ArrayList<>();
        for (TeamSlotSnapshot slot : record.snapshot().slots()) {
            int slotNumber = slot.slot() + 1;
            if (slot.pokemon() == null) {
                slots.add(new ViewSlotResult(slotNumber, null));
                continue;
            }

            PokemonSnapshot p = slot.pokemon();
            slots.add(new ViewSlotResult(slotNumber, new ViewPokemon(
                p.speciesId(),
                p.formId(),
                p.level(),
                p.abilityId(),
                p.natureId(),
                p.heldItemId(),
                p.gender(),
                p.ivs(),
                p.evs(),
                p.moveIds()
            )));
        }

        return new ViewResult(record.displayName(), record.playerName(), record.playerUuid(), record.snapshot().occupiedSlots(), slots);
    }

    private static void ensureLoaded(MinecraftServer server) {
        Path storageFile = resolveStorageFile(server);
        if (loaded && storageFile.equals(loadedStorageFile) && loadedServer == server) {
            return;
        }
        loadFromDisk(storageFile);
        loadedStorageFile = storageFile;
        loadedServer = server;
        loaded = true;
    }

    private static Path resolveStorageFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(STORE_DIRECTORY).resolve(STORE_FILE);
    }

    private static void loadFromDisk(Path storageFile) {
        PARTICIPANTS_BY_NAME.clear();
        PARTICIPANT_NAME_BY_PLAYER.clear();

        if (!Files.exists(storageFile)) {
            return;
        }

        try {
            String rawJson = Files.readString(storageFile, StandardCharsets.UTF_8);
            if (rawJson.isBlank()) {
                return;
            }

            JsonElement rootElement = JsonParser.parseString(rawJson);
            if (!rootElement.isJsonObject()) {
                return;
            }

            JsonObject root = rootElement.getAsJsonObject();
            int schemaVersion = root.has(SCHEMA_VERSION_KEY) ? root.get(SCHEMA_VERSION_KEY).getAsInt() : 0;
            if (schemaVersion != SCHEMA_VERSION) {
                return;
            }

            if (!root.has(PARTICIPANTS_KEY) || !root.get(PARTICIPANTS_KEY).isJsonArray()) {
                return;
            }

            for (JsonElement participantElement : root.getAsJsonArray(PARTICIPANTS_KEY)) {
                if (!participantElement.isJsonObject()) {
                    continue;
                }
                ParticipantRecord record = fromJson(participantElement.getAsJsonObject());
                PARTICIPANTS_BY_NAME.put(record.key(), record);
                PARTICIPANT_NAME_BY_PLAYER.put(record.playerUuid(), record.key());
            }
        } catch (Exception exception) {
            Cobblemon.LOGGER.error("No se pudo cargar el torneo persistido desde {}", storageFile, exception);
        }
    }

    private static void persist(MinecraftServer server) {
        if (loadedStorageFile == null) {
            loadedStorageFile = resolveStorageFile(server);
        }

        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION_KEY, SCHEMA_VERSION);
        JsonArray participants = new JsonArray();

        List<ParticipantRecord> records = new ArrayList<>(PARTICIPANTS_BY_NAME.values());
        records.sort(Comparator.comparing(ParticipantRecord::displayName).thenComparing(ParticipantRecord::key));
        for (ParticipantRecord record : records) {
            participants.add(toJson(record));
        }
        root.add(PARTICIPANTS_KEY, participants);

        Path storageFile = resolveStorageFile(server);
        try {
            Files.createDirectories(storageFile.getParent());
            Files.writeString(storageFile, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo guardar la informacion del torneo.", exception);
        }
    }

    private static ParticipantRecord fromJson(JsonObject json) {
        String key = normalizeParticipantName(getString(json, "key", getString(json, "displayName", "")));
        String displayName = getString(json, "displayName", key);
        UUID playerUuid = UUID.fromString(getString(json, "playerUuid", UUID.randomUUID().toString()));
        String playerName = getString(json, "playerName", displayName);
        long registeredAt = getLong(json, "registeredAt", Instant.now().toEpochMilli());
        CompetitiveTeamSnapshot snapshot = snapshotFromJson(json.has("snapshot") && json.get("snapshot").isJsonObject() ? json.getAsJsonObject("snapshot") : new JsonObject());
        return new ParticipantRecord(key, displayName, playerUuid, playerName, snapshot, registeredAt);
    }

    private static JsonObject toJson(ParticipantRecord record) {
        JsonObject json = new JsonObject();
        json.addProperty("key", record.key());
        json.addProperty("displayName", record.displayName());
        json.addProperty("playerUuid", record.playerUuid().toString());
        json.addProperty("playerName", record.playerName());
        json.addProperty("registeredAt", record.registeredAt());
        json.add("snapshot", toJson(record.snapshot()));
        return json;
    }

    private static CompetitiveTeamSnapshot snapshotFromJson(JsonObject json) {
        List<TeamSlotSnapshot> slots = new ArrayList<>();
        int occupied = 0;
        JsonArray slotArray = json.has("slots") && json.get("slots").isJsonArray() ? json.getAsJsonArray("slots") : new JsonArray();
        for (JsonElement slotElement : slotArray) {
            if (!slotElement.isJsonObject()) {
                continue;
            }
            JsonObject slotJson = slotElement.getAsJsonObject();
            int slotIndex = getInt(slotJson, "slot", slots.size());
            PokemonSnapshot pokemon = null;
            if (slotJson.has("pokemon") && slotJson.get("pokemon").isJsonObject()) {
                pokemon = pokemonFromJson(slotJson.getAsJsonObject("pokemon"));
                occupied++;
            }
            while (slots.size() <= slotIndex) {
                slots.add(TeamSlotSnapshot.empty(slots.size()));
            }
            slots.set(slotIndex, new TeamSlotSnapshot(slotIndex, pokemon));
        }
        return new CompetitiveTeamSnapshot(slots, occupied);
    }

    private static JsonObject toJson(CompetitiveTeamSnapshot snapshot) {
        JsonObject json = new JsonObject();
        JsonArray slots = new JsonArray();
        for (TeamSlotSnapshot slot : snapshot.slots()) {
            JsonObject slotJson = new JsonObject();
            slotJson.addProperty("slot", slot.slot());
            if (slot.pokemon() == null) {
                slotJson.add("pokemon", null);
            } else {
                slotJson.add("pokemon", toJson(slot.pokemon()));
            }
            slots.add(slotJson);
        }
        json.addProperty("occupiedSlots", snapshot.occupiedSlots());
        json.add("slots", slots);
        return json;
    }

    private static PokemonSnapshot pokemonFromJson(JsonObject json) {
        return new PokemonSnapshot(
            getString(json, "speciesId", ""),
            getString(json, "formId", ""),
            getInt(json, "level", 0),
            getString(json, "abilityId", ""),
            getString(json, "natureId", ""),
            getString(json, "heldItemId", "minecraft:air"),
            getString(json, "gender", "UNKNOWN"),
            intArrayFromJson(json.has("ivs") && json.get("ivs").isJsonArray() ? json.getAsJsonArray("ivs") : new JsonArray()),
            intArrayFromJson(json.has("evs") && json.get("evs").isJsonArray() ? json.getAsJsonArray("evs") : new JsonArray()),
            stringListFromJson(json.has("moves") && json.get("moves").isJsonArray() ? json.getAsJsonArray("moves") : new JsonArray())
        );
    }

    private static JsonObject toJson(PokemonSnapshot snapshot) {
        JsonObject json = new JsonObject();
        json.addProperty("speciesId", snapshot.speciesId());
        json.addProperty("formId", snapshot.formId());
        json.addProperty("level", snapshot.level());
        json.addProperty("abilityId", snapshot.abilityId());
        json.addProperty("natureId", snapshot.natureId());
        json.addProperty("heldItemId", snapshot.heldItemId());
        json.addProperty("gender", snapshot.gender());
        json.add("ivs", intArrayToJson(snapshot.ivs()));
        json.add("evs", intArrayToJson(snapshot.evs()));
        json.add("moves", stringListToJson(snapshot.moveIds()));
        return json;
    }

    private static CompetitiveTeamSnapshot createSnapshot(ServerPlayer player) {
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int partySize = party.size();
        List<TeamSlotSnapshot> slotSnapshots = new ArrayList<>(partySize);

        int occupied = 0;
        for (int i = 0; i < partySize; i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon == null) {
                slotSnapshots.add(TeamSlotSnapshot.empty(i));
                continue;
            }
            occupied++;
            slotSnapshots.add(TeamSlotSnapshot.of(i, toPokemonSnapshot(pokemon)));
        }

        return new CompetitiveTeamSnapshot(slotSnapshots, occupied);
    }

    private static PokemonSnapshot toPokemonSnapshot(Pokemon pokemon) {
        String speciesId = pokemon.getSpecies().showdownId();
        String formId = pokemon.getForm().formOnlyShowdownId();
        String abilityId = pokemon.getAbility().getTemplate().getName();
        String natureId = pokemon.getEffectiveNature().getName().toString();
        String heldItemId = toItemId(pokemon.heldItem());
        String gender = pokemon.getGender().name();
        int[] ivs = collectStats(pokemon, true);
        int[] evs = collectStats(pokemon, false);
        List<String> moves = collectMoveIds(pokemon.getMoveSet());

        return new PokemonSnapshot(
            speciesId,
            formId,
            pokemon.getLevel(),
            abilityId,
            natureId,
            heldItemId,
            gender,
            ivs,
            evs,
            moves
        );
    }

    private static int[] collectStats(Pokemon pokemon, boolean ivs) {
        int[] values = new int[COMPETITIVE_STATS_ORDER.length];
        for (int i = 0; i < COMPETITIVE_STATS_ORDER.length; i++) {
            Stat stat = COMPETITIVE_STATS_ORDER[i];
            values[i] = ivs ? pokemon.getIvs().getOrDefault(stat) : pokemon.getEvs().getOrDefault(stat);
        }
        return values;
    }

    private static List<String> collectMoveIds(MoveSet moveSet) {
        List<String> moves = new ArrayList<>(MoveSet.MOVE_COUNT);
        for (int i = 0; i < MoveSet.MOVE_COUNT; i++) {
            Move move = moveSet.get(i);
            moves.add(move == null ? "-" : move.getTemplate().getName());
        }
        return moves;
    }

    private static String toItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:air";
        }
        Item item = stack.getItem();
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? "minecraft:air" : key.toString();
    }

    private static CheckComparison compare(CompetitiveTeamSnapshot expected, CompetitiveTeamSnapshot current, int requiredMatches) {
        int matchedPokemon = countMatchedPokemon(expected, current);

        if (requiredMatches < expected.occupiedSlots()) {
            if (matchedPokemon >= requiredMatches) {
                return new CheckComparison(true, matchedPokemon, requiredMatches, List.of());
            }

            List<String> differences = new ArrayList<>();
            differences.add(
                "Coincidencias insuficientes: se requieren al menos " + requiredMatches
                    + " Pokemon registrados y solo coinciden " + matchedPokemon + "."
            );
            differences.add(
                "Pokemon registrados: " + expected.occupiedSlots() + ", Pokemon actuales: " + current.occupiedSlots() + "."
            );
            return new CheckComparison(false, matchedPokemon, requiredMatches, differences);
        }

        List<String> differences = new ArrayList<>();
        if (expected.occupiedSlots() != current.occupiedSlots()) {
            differences.add(
                "Cantidad de Pokemon distinta: esperado " + expected.occupiedSlots() + ", actual " + current.occupiedSlots() + "."
            );
            return new CheckComparison(false, matchedPokemon, requiredMatches, differences);
        }

        List<PokemonSnapshot> expectedPokemon = extractPokemonSnapshots(expected);
        List<PokemonSnapshot> currentPokemon = extractPokemonSnapshots(current);

        expectedPokemon.sort(Comparator.comparing(TournamentParticipantService::pokemonComparisonKey));
        currentPokemon.sort(Comparator.comparing(TournamentParticipantService::pokemonComparisonKey));

        for (int i = 0; i < expectedPokemon.size(); i++) {
            comparePokemon(expectedPokemon.get(i), currentPokemon.get(i), i + 1, differences);
        }

        return new CheckComparison(differences.isEmpty(), matchedPokemon, requiredMatches, differences);
    }

    private static int countMatchedPokemon(CompetitiveTeamSnapshot expected, CompetitiveTeamSnapshot current) {
        Map<String, Integer> expectedCounts = pokemonCountByKey(expected);
        Map<String, Integer> currentCounts = pokemonCountByKey(current);

        int matches = 0;
        for (Map.Entry<String, Integer> entry : expectedCounts.entrySet()) {
            int currentCount = currentCounts.getOrDefault(entry.getKey(), 0);
            matches += Math.min(entry.getValue(), currentCount);
        }
        return matches;
    }

    private static Map<String, Integer> pokemonCountByKey(CompetitiveTeamSnapshot snapshot) {
        Map<String, Integer> counts = new HashMap<>();
        for (PokemonSnapshot pokemon : extractPokemonSnapshots(snapshot)) {
            counts.merge(pokemonComparisonKey(pokemon), 1, Integer::sum);
        }
        return counts;
    }

    private static void comparePokemon(PokemonSnapshot expected, PokemonSnapshot current, int slotNumber, List<String> differences) {
        compareField(differences, slotNumber, "especie", expected.speciesId(), current.speciesId());
        compareField(differences, slotNumber, "forma", expected.formId(), current.formId());
        compareField(differences, slotNumber, "nivel", expected.level(), current.level());
        compareField(differences, slotNumber, "habilidad", expected.abilityId(), current.abilityId());
        compareField(differences, slotNumber, "naturaleza", expected.natureId(), current.natureId());
        compareField(differences, slotNumber, "objeto", expected.heldItemId(), current.heldItemId());
        compareField(differences, slotNumber, "genero", expected.gender(), current.gender());

        if (!Arrays.equals(expected.ivs(), current.ivs())) {
            differences.add("Slot " + slotNumber + ": IVs distintos (esperado " + formatStats(expected.ivs()) + ", actual " + formatStats(current.ivs()) + ").");
        }
        if (!Arrays.equals(expected.evs(), current.evs())) {
            differences.add("Slot " + slotNumber + ": EVs distintos (esperado " + formatStats(expected.evs()) + ", actual " + formatStats(current.evs()) + ").");
        }
        List<String> expectedMoves = normalizedMoveIds(expected.moveIds());
        List<String> currentMoves = normalizedMoveIds(current.moveIds());
        if (!expectedMoves.equals(currentMoves)) {
            differences.add("Slot " + slotNumber + ": movimientos distintos (esperado " + expectedMoves + ", actual " + currentMoves + ").");
        }
    }

    private static List<PokemonSnapshot> extractPokemonSnapshots(CompetitiveTeamSnapshot snapshot) {
        List<PokemonSnapshot> pokemon = new ArrayList<>();
        for (TeamSlotSnapshot slot : snapshot.slots()) {
            if (slot.pokemon() != null) {
                pokemon.add(slot.pokemon());
            }
        }
        return pokemon;
    }

    private static String pokemonComparisonKey(PokemonSnapshot pokemon) {
        return String.join("|",
            pokemon.speciesId(),
            pokemon.formId(),
            Integer.toString(pokemon.level()),
            pokemon.abilityId(),
            pokemon.natureId(),
            pokemon.heldItemId(),
            pokemon.gender(),
            Arrays.toString(pokemon.ivs()),
            Arrays.toString(pokemon.evs()),
            String.join(",", normalizedMoveIds(pokemon.moveIds()))
        );
    }

    private static List<String> normalizedMoveIds(List<String> moveIds) {
        List<String> normalized = new ArrayList<>(moveIds);
        normalized.sort(String::compareTo);
        return normalized;
    }

    private static void compareField(List<String> differences, int slotNumber, String field, Object expected, Object current) {
        if (!expected.equals(current)) {
            differences.add("Slot " + slotNumber + ": " + field + " distinto (esperado " + expected + ", actual " + current + ").");
        }
    }

    private static int[] intArrayFromJson(JsonArray array) {
        int[] values = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            values[i] = array.get(i).getAsInt();
        }
        return values;
    }

    private static JsonArray intArrayToJson(int[] values) {
        JsonArray array = new JsonArray();
        for (int value : values) {
            array.add(value);
        }
        return array;
    }

    private static List<String> stringListFromJson(JsonArray array) {
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            values.add(element.getAsString());
        }
        return values;
    }

    private static JsonArray stringListToJson(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static String getString(JsonObject json, String key, String defaultValue) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : defaultValue;
    }

    private static int getInt(JsonObject json, String key, int defaultValue) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsInt() : defaultValue;
    }

    private static long getLong(JsonObject json, String key, long defaultValue) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsLong() : defaultValue;
    }

    private static String normalizeParticipantName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String shortStatName(int index) {
        return switch (index) {
            case 0 -> "HP";
            case 1 -> "Atk";
            case 2 -> "Def";
            case 3 -> "SpA";
            case 4 -> "SpD";
            case 5 -> "Spe";
            default -> "?";
        };
    }

    private static String formatStats(int[] values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(shortStatName(i)).append('=').append(values[i]);
        }
        return builder.append(']').toString();
    }

    private record ParticipantRecord(
        String key,
        String displayName,
        UUID playerUuid,
        String playerName,
        CompetitiveTeamSnapshot snapshot,
        long registeredAt
    ) {
    }

    private record CompetitiveTeamSnapshot(List<TeamSlotSnapshot> slots, int occupiedSlots) {
    }

    private record TeamSlotSnapshot(int slot, PokemonSnapshot pokemon) {
        static TeamSlotSnapshot empty(int slot) {
            return new TeamSlotSnapshot(slot, null);
        }

        static TeamSlotSnapshot of(int slot, PokemonSnapshot pokemon) {
            return new TeamSlotSnapshot(slot, pokemon);
        }
    }

    private record PokemonSnapshot(
        String speciesId,
        String formId,
        int level,
        String abilityId,
        String natureId,
        String heldItemId,
        String gender,
        int[] ivs,
        int[] evs,
        List<String> moveIds
    ) {
    }

    private record CheckComparison(boolean matches, int matchedPokemon, int requiredMatches, List<String> differences) {
    }

    public record RegisterResult(String participantName, int teamSize) {
    }

    public record CheckResult(
        String participantName,
        boolean matches,
        int matchedPokemon,
        int requiredMatches,
        int registeredPokemon,
        List<String> differences
    ) {
    }

    public record ClearResult(int removedParticipants) {
    }

    public record ViewResult(String participantName, String playerName, UUID playerUuid, int occupiedSlots, List<ViewSlotResult> slots) {
    }

    public record ViewSlotResult(int slotNumber, ViewPokemon pokemon) {
        public boolean isEmpty() {
            return pokemon == null;
        }
    }

    public record ViewPokemon(
        String speciesId,
        String formId,
        int level,
        String abilityId,
        String natureId,
        String heldItemId,
        String gender,
        int[] ivs,
        int[] evs,
        List<String> moveIds
    ) {
    }
}

