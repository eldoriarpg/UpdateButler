package de.eldoria.updatebutler.config;

import com.google.common.hash.Hashing;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import de.eldoria.updatebutler.util.ArgumentParser;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Data
public class Application {
    @Expose
    private final Set<Long> owner;
    @Expose
    private final String webhook;
    @Expose
    private final HashMap<String, Release> releases = new HashMap<>();
    /**
     * The id of the application. immutable
     */
    @Expose
    private int id;
    /**
     * A unique string identifier for this application. mutable.
     */
    @Expose
    private String identifier;
    /**
     * The display name of the application
     */
    @SerializedName("display_name")
    @Expose
    private String displayName;
    /**
     * Short description of the application
     */
    @Expose
    private String description;
    @Expose
    private String[] alias;
    @Expose
    private Long channel;

    public Application(int id, String identifier, String displayName, String description, String[] alias, Long owner, Long channel) {
        this.id = id;
        this.identifier = identifier;
        this.displayName = displayName;
        this.description = description;
        this.alias = alias;
        this.owner = new HashSet<>(Collections.singletonList(owner));
        this.channel = channel;
        this.webhook = Hashing.sha256()
                .hashString(displayName + Instant.now().toEpochMilli(), StandardCharsets.UTF_8)
                .toString().toLowerCase();
    }

    public boolean isOwner(ISnowflake user) {
        return owner.contains(user.getIdLong());
    }

    public boolean addOwner(ISnowflake user) {
        return owner.add(user.getIdLong());
    }

    public boolean removeOwner(ISnowflake user) {
        return owner.remove(user.getIdLong());
    }

    public void addRelease(String key, Release release) {
        releases.put(key, release);
    }

    public Optional<Release> getRelease(String key) {
        if ("latest".equalsIgnoreCase(key)) {
            return Optional.ofNullable(getReleases(true).get(0));
        }

        for (var release : releases.entrySet()) {
            if (release.getKey().equalsIgnoreCase(key.replace("_", " "))) {
                return Optional.ofNullable(release.getValue());
            }
        }
        return Optional.empty();
    }

    public boolean deleteRelease(String key) {
        return releases.remove(key) != null;
    }

    public MessageEmbed getReleaseInfo(Configuration configuration, ArgumentParser parser, Guild guild, Release release) {
        String owner = parser.getGuildMembers(guild, this.owner
                .stream().
                        map(Object::toString).
                        collect(Collectors.toList()))
                .stream()
                .map(Member::getAsMention)
                .collect(Collectors.joining(", "));

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("#" + id + " " + getDisplayName() + " " + release.getVersion())
                .setDescription(release.getTitle())
                .addField("Patchnotes", release.getPatchnotes(), false)
                .addField("Stable", release.isDevBuild() ? "dev" : "stable", true)
                .addField("Download", configuration.getHostName() + "/download?id=" + id + "&version=" + release.getVersion().replace(" ", "_"), true)
                .addField("Checksum Sha256", release.getChecksum(), true)
                .setTimestamp(release.getPublished());
        return builder.build();
    }

    public MessageEmbed getApplicationInfo(Configuration configuration, Guild guild, ArgumentParser parser) {
        Optional<Release> optionalRelease = getLatestStableVersion();
        if (optionalRelease.isEmpty()) {
            optionalRelease = getLatestVersion();
        }

        String owner = parser.getGuildMembers(guild, this.owner
                .stream().
                        map(Object::toString).
                        collect(Collectors.toList()))
                .stream()
                .map(Member::getAsMention)
                .collect(Collectors.joining(", "));

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("#" + id + " " + getDisplayName())
                .setDescription(getDescription())
                .addField("Owner", owner, true);

        if (alias.length != 0) {
            builder.addField("Alias", String.join(", ", alias), true);
        }

        if (optionalRelease.isPresent()) {
            Release release = optionalRelease.get();
            builder.addField("Latest Version", release.getVersion(), true)
                    .addField("Download", configuration.getHostName() + "/download?id=" + id + "&version=" + release.getVersion().replace(" ", "_"), true)
                    .addField("Checksum Sha256", release.getChecksum(), true);
            builder.setTimestamp(release.getPublished());
        }

        return builder.build();
    }

    public Optional<Release> getLatestStableVersion() {
        if (releases.isEmpty()) return Optional.empty();
        return releases.values().stream()
                .filter(r -> !r.isDevBuild())
                .max(Comparator.comparing(Release::getPublished));
    }

    /**
     * Get the latest release including dev builds
     *
     * @return latest release
     */
    public Optional<Release> getLatestVersion() {
        if (releases.isEmpty()) return Optional.empty();
        return releases.values().stream().max(Comparator.comparing(Release::getPublished));
    }

    /**
     * Get all releases of a application
     *
     * @param dev true when dev builds should be included
     *
     * @return list of dev builds.
     */
    public List<Release> getReleases(boolean dev) {
        List<Release> collect = releases.values()
                .stream()
                .filter(r -> dev || !r.isDevBuild())
                .sorted(Comparator.comparing(Release::getPublished))
                .collect(Collectors.toList());
        Collections.reverse(collect);
        return Collections.unmodifiableList(collect);
    }
}
