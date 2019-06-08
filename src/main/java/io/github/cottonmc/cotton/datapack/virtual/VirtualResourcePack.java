package io.github.cottonmc.cotton.datapack.virtual;

import com.google.common.base.Charsets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.SharedConstants;
import net.minecraft.resource.AbstractFileResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A virtual resource pack that is not represented by actual files.
 */
public class VirtualResourcePack extends AbstractFileResourcePack {
	private static final int PACK_FORMAT = SharedConstants.getGameVersion().getPackVersion();
	private static final Logger LOGGER = LogManager.getLogger();
	private final Set<String> namespaces;
	final Map<String, Supplier<String>> contents;
	private final String id;

	/**
	 * The constructor.
	 *
	 * @param id         an identifier for this data pack (does not have to be unique)
	 * @param namespaces the namespaces that this pack provides
	 * @param contents   the contents as a [resource path]=>[contents] map
	 */
	public VirtualResourcePack(String id, Set<String> namespaces, Map<String, Supplier<String>> contents) {
		super(null);
		this.id = id;
		namespaces.forEach(namespace -> {
			if (!Identifier.isValid(namespace + ":test"))
				throw new InvalidIdentifierException("Invalid namespace: " + namespace);
		});
		this.namespaces = namespaces;
		this.contents = contents;
	}

	@Override
	protected InputStream openFile(String s) throws IOException {
		if (contents.containsKey(s)) return new ReaderInputStream(new StringReader(contents.get(s).get()), Charsets.UTF_8);
		else throw new FileNotFoundException("Unknown file in virtual resource pack: " + s);
	}

	@Override
	protected boolean containsFile(String s) {
		return contents.containsKey(s);
	}

	@Override
	public Collection<Identifier> findResources(ResourceType type, String path, int depth, Predicate<String> predicate) {
		List<Identifier> ids = new ArrayList<>();
		Set<String> contentKeys = contents.keySet();

		for (String namespace : getNamespaces(type)) {
			String prefix = String.format("%s/%s/%s", type.getName(), namespace, path);
			Stream<String> matchingKeys = contentKeys.stream().filter(s -> s.startsWith(prefix));
			matchingKeys.map(s -> s.split("/"))
					.filter(split -> predicate.test(split[split.length - 1]))
					.forEach(split -> {
						try {
							ids.add(new Identifier(namespace, String.join("/", ArrayUtils.subarray(split, 2, split.length))));
						} catch (InvalidIdentifierException e) {
							LOGGER.error("Invalid identifier found in virtual resource pack", e);
						}
					});
		}

		return ids;
	}

	@Override
	public Set<String> getNamespaces(ResourceType resourceType) {
		return namespaces;
	}

	@Override
	public void close() {}

	@Override
	public String getName() {
		return String.format("%s (virtual)", id);
	}

	String getId(int index) {
		return String.format("virtual/%d_%s", index, id);
	}

	@Nullable
	@Override
	public <T> T parseMetadata(ResourceMetadataReader<T> reader) {
		JsonObject packMetadata = new JsonObject();
		packMetadata.addProperty("pack_format", PACK_FORMAT);
		packMetadata.addProperty("description", "Virtual resource pack generated by Cotton.");

		JsonObject metadata = new JsonObject();
		metadata.add("pack", packMetadata);

		if (metadata.has(reader.getKey())) {
			try {
				return reader.fromJson(metadata.getAsJsonObject(reader.getKey()));
			} catch (JsonParseException e) {
				LOGGER.error("Couldn't load {} metadata from virtual resource pack", reader.getKey(), e);
			}
		}

		return null;
	}
}
