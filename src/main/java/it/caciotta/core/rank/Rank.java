package it.caciotta.core.rank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Rappresenta un grado/rank del server con prefisso, colore, permessi e statistiche associate.
 */
public class Rank implements Comparable<Rank> {

    private final String id;
    private String displayName;
    private String prefix;
    private String suffix;
    private String chatColor;
    private int weight;
    private boolean isDefault;
    private int maxHomes;
    private final List<String> permissions;

    public Rank(String id, String displayName, String prefix, String suffix, String chatColor,
                int weight, boolean isDefault, int maxHomes, List<String> permissions) {
        this.id = id.toLowerCase();
        this.displayName = displayName != null ? displayName : id;
        this.prefix = prefix != null ? prefix : "";
        this.suffix = suffix != null ? suffix : "";
        this.chatColor = chatColor != null ? chatColor : "&f";
        this.weight = weight;
        this.isDefault = isDefault;
        this.maxHomes = Math.max(1, maxHomes);
        this.permissions = permissions != null ? new ArrayList<>(permissions) : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public String getChatColor() {
        return chatColor;
    }

    public void setChatColor(String chatColor) {
        this.chatColor = chatColor;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public int getMaxHomes() {
        return maxHomes;
    }

    public void setMaxHomes(int maxHomes) {
        this.maxHomes = maxHomes;
    }

    public List<String> getPermissions() {
        return Collections.unmodifiableList(permissions);
    }

    public void addPermission(String permission) {
        if (permission != null && !permission.isBlank() && !permissions.contains(permission)) {
            permissions.add(permission.trim());
        }
    }

    public boolean removePermission(String permission) {
        if (permission == null) return false;
        return permissions.removeIf(p -> p.equalsIgnoreCase(permission.trim()));
    }

    public void setPermissions(List<String> newPermissions) {
        this.permissions.clear();
        if (newPermissions != null) {
            for (String p : newPermissions) {
                if (p != null && !p.isBlank()) {
                    this.permissions.add(p.trim());
                }
            }
        }
    }

    @Override
    public int compareTo(Rank other) {
        // Ordina per peso decrescente (rank più alto per primo)
        int cmp = Integer.compare(other.weight, this.weight);
        if (cmp != 0) {
            return cmp;
        }
        return this.id.compareToIgnoreCase(other.id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rank rank = (Rank) o;
        return Objects.equals(id, rank.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Rank{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", weight=" + weight +
                ", isDefault=" + isDefault +
                '}';
    }
}
