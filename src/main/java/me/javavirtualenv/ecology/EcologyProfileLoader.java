package me.javavirtualenv.ecology;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

public final class EcologyProfileLoader {
	private static final ResourceLocation BASE_TEMPLATE = ResourceLocation.fromNamespaceAndPath("better-ecology", "templates/mod_registry.yaml");

	private final Logger logger;
	private final Yaml yaml = new Yaml();

	public EcologyProfileLoader(Logger logger) {
		this.logger = logger;
	}

	public List<EcologyProfile> loadAll(ResourceManager manager) {
		Map<String, Object> base = loadYaml(manager, BASE_TEMPLATE);
		Map<ResourceLocation, Resource> yamlResources = manager.listResources("mobs",
				resourceLocation -> resourceLocation.getPath().endsWith(".yaml") || resourceLocation.getPath().endsWith(".yml"));
		Map<ResourceLocation, Resource> jsonResources = manager.listResources("mobs",
				resourceLocation -> resourceLocation.getPath().endsWith(".json"));

		List<EcologyProfile> profiles = new ArrayList<>();

		for (Map.Entry<ResourceLocation, Resource> entry : yamlResources.entrySet()) {
			ResourceLocation yamlId = entry.getKey();
			Map<String, Object> profileMap = loadYaml(entry.getValue(), yamlId);
			if (profileMap.isEmpty()) {
				continue;
			}

			// Find and merge corresponding behaviors.json if exists
			ResourceLocation jsonId = findBehaviorsJson(yamlId);
			if (jsonResources.containsKey(jsonId)) {
				Map<String, Object> jsonData = loadJson(jsonResources.get(jsonId), jsonId);
				if (!jsonData.isEmpty()) {
					profileMap = EcologyMerge.merge(profileMap, List.of(jsonData));
				}
			}

			List<Map<String, Object>> overlays = new ArrayList<>();
			overlays.addAll(loadArchetypes(manager, profileMap));
			overlays.add(profileMap);
			Map<String, Object> merged = EcologyMerge.merge(base, overlays);

			String mobIdString = readString(merged, "identity.mob_id");
			if (mobIdString == null || mobIdString.isBlank()) {
				logger.warn("Better Ecology: profile {} missing identity.mob_id", yamlId);
				continue;
			}
			ResourceLocation mobId = ResourceLocation.tryParse(mobIdString);
			if (mobId == null) {
				logger.warn("Better Ecology: invalid mob_id '{}' in {}", mobIdString, yamlId);
				continue;
			}
			profiles.add(new EcologyProfile(mobId, merged));
		}
		return profiles;
	}

	/**
	 * Converts a YAML registry path to the corresponding behaviors.json path.
	 * Example: better-ecology:mobs/passive/cow/mod_registry.yaml
	 *          -> better-ecology:mobs/passive/cow/behaviors.json
	 */
	private ResourceLocation findBehaviorsJson(ResourceLocation yamlId) {
		String path = yamlId.getPath();
		int lastSlash = path.lastIndexOf('/');
		String dirPath = lastSlash >= 0 ? path.substring(0, lastSlash + 1) : "";
		return ResourceLocation.fromNamespaceAndPath(yamlId.getNamespace(), dirPath + "behaviors.json");
	}

	private List<Map<String, Object>> loadArchetypes(ResourceManager manager, Map<String, Object> profileMap) {
		List<String> archetypeIds = readStringList(profileMap, "archetypes");
		if (archetypeIds.isEmpty()) {
			return List.of();
		}
		List<Map<String, Object>> archetypes = new ArrayList<>();
		for (String archetypeId : archetypeIds) {
			ResourceLocation baseId = ResourceLocation.tryParse(archetypeId);
			if (baseId == null) {
				logger.warn("Better Ecology: invalid archetype id '{}'", archetypeId);
				continue;
			}
			String path = baseId.getPath();
			if (!path.endsWith(".yaml") && !path.endsWith(".yml")) {
				path = path + ".yaml";
			}
			ResourceLocation archetypeLoc = ResourceLocation.fromNamespaceAndPath(baseId.getNamespace(), "archetypes/" + path);
			Optional<Resource> resource = manager.getResource(archetypeLoc);
			if (resource.isEmpty()) {
				logger.warn("Better Ecology: missing archetype {}", archetypeLoc);
				continue;
			}
			Map<String, Object> archetypeMap = loadYaml(resource.get(), archetypeLoc);
			if (!archetypeMap.isEmpty()) {
				archetypes.add(archetypeMap);
			}
		}
		return archetypes;
	}

	private Map<String, Object> loadYaml(ResourceManager manager, ResourceLocation id) {
		Optional<Resource> resource = manager.getResource(id);
		if (resource.isEmpty()) {
			logger.warn("Better Ecology: missing base template {}", id);
			return Map.of();
		}
		return loadYaml(resource.get(), id);
	}

	private Map<String, Object> loadYaml(Resource resource, ResourceLocation id) {
		try (BufferedReader reader = resource.openAsReader()) {
			Object data = yaml.load(reader);
			if (data instanceof Map<?, ?> map) {
				return normalizeMap(map);
			}
		} catch (IOException ex) {
			logger.warn("Better Ecology: failed to read {} ({})", id, ex.getMessage());
		}
		return Map.of();
	}

	private Map<String, Object> loadJson(Resource resource, ResourceLocation id) {
		try (BufferedReader reader = resource.openAsReader()) {
			JsonElement element = JsonParser.parseReader(reader);
			if (element.isJsonObject()) {
				return convertJsonToMap(element.getAsJsonObject());
			}
		} catch (IOException ex) {
			logger.warn("Better Ecology: failed to read JSON {} ({})", id, ex.getMessage());
		}
		return Map.of();
	}

	private Map<String, Object> convertJsonToMap(JsonObject jsonObject) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
			result.put(entry.getKey(), convertJsonElement(entry.getValue()));
		}
		return result;
	}

	private Object convertJsonElement(JsonElement element) {
		if (element.isJsonObject()) {
			return convertJsonToMap(element.getAsJsonObject());
		}
		if (element.isJsonArray()) {
			List<Object> list = new ArrayList<>();
			for (JsonElement item : element.getAsJsonArray()) {
				list.add(convertJsonElement(item));
			}
			return list;
		}
		if (element.isJsonPrimitive()) {
			if (element.getAsJsonPrimitive().isBoolean()) {
				return element.getAsBoolean();
			}
			if (element.getAsJsonPrimitive().isNumber()) {
				return element.getAsNumber();
			}
			return element.getAsString();
		}
		return null;
	}

	private Map<String, Object> normalizeMap(Map<?, ?> input) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : input.entrySet()) {
			String key = String.valueOf(entry.getKey());
			out.put(key, normalizeValue(entry.getValue()));
		}
		return out;
	}

	private Object normalizeValue(Object value) {
		if (value instanceof Map<?, ?> map) {
			return normalizeMap(map);
		}
		if (value instanceof List<?> list) {
			List<Object> out = new ArrayList<>();
			for (Object entry : list) {
				out.add(normalizeValue(entry));
			}
			return out;
		}
		return value;
	}

	@Nullable
	private String readString(Map<String, Object> map, String path) {
		Object value = readPath(map, path);
		if (value instanceof String string) {
			return string;
		}
		return null;
	}

	private List<String> readStringList(Map<String, Object> map, String path) {
		Object value = readPath(map, path);
		if (!(value instanceof List<?> list)) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (Object entry : list) {
			if (entry instanceof String string) {
				out.add(string);
			}
		}
		return out;
	}

	@Nullable
	private Object readPath(Map<String, Object> map, String path) {
		String[] parts = path.split("\\.");
		Object current = map;
		for (String part : parts) {
			if (!(current instanceof Map<?, ?> currentMap)) {
				return null;
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> cast = (Map<String, Object>) currentMap;
			current = cast.get(part);
			if (current == null) {
				return null;
			}
		}
		return current;
	}
}
