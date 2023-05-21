/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.internal.schematic.backends;

import com.sk89q.worldedit.internal.util.LogManagerCompat;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A backend that scans the folder tree, then caches the result for a certain amount of time.
 */
public class PollingSchematicsBackend implements SchematicsBackend {

    private static final Logger LOGGER = LogManagerCompat.getLogger();

    private static final Duration MAX_RESULT_AGE = Duration.ofSeconds(10);

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Path schematicsDir;
    private Instant lastUpdateTs = Instant.EPOCH;
    private List<Path> schematics = new ArrayList<>();

    private PollingSchematicsBackend(Path schematicsDir) {
        this.schematicsDir = schematicsDir;
    }

    /**
     * Create a new instance of the polling SchematicsManager backend.
     * @param schematicsFolder Root folder for schematics.
     * @return A new PollingSchematicsBackend instance.
     */
    public static PollingSchematicsBackend create(Path schematicsFolder) {
        return new PollingSchematicsBackend(schematicsFolder);
    }

    private List<Path> scanFolder(Path root) {
        List<Path> pathList = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    pathList.addAll(scanFolder(path));
                } else {
                    pathList.add(path);
                }
            }
        } catch (IOException e) {
            LOGGER.error(e);
        }
        return pathList;
    }

    private void runRescan() {
        LOGGER.debug("Rescanning schematics");
        this.schematics = scanFolder(schematicsDir);
        lastUpdateTs = Instant.now();
    }

    @Override
    public void init() {
    }

    @Override
    public void uninit() {
    }

    @Override
    public synchronized List<Path> getList() {
        // udpate internal cache if requried (determined by age)
        Duration age = Duration.between(lastUpdateTs, Instant.now());
        if (age.compareTo(MAX_RESULT_AGE) >= 0) {
            runRescan();
        }
        return List.copyOf(schematics);
    }

    @Override
    public synchronized void update() {
        lastUpdateTs = Instant.EPOCH; // invalidate cache
    }
}