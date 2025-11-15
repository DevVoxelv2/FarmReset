package de.farmreset.models;

import org.bukkit.Location;

public class FarmData {

    private final String name;
    private final Location spawnLocation;
    private final Location pos1;
    private final Location pos2;

    public FarmData(String name, Location spawnLocation, Location pos1, Location pos2) {
        this.name = name;
        this.spawnLocation = spawnLocation;
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    public String getName() {
        return name;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public Location getPos1() {
        return pos1;
    }

    public Location getPos2() {
        return pos2;
    }
}

