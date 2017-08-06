/*
 * Copyright 2015 Futeh Kao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.e6tech.elements.common.util.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S134")
public class FileUtil {

    private static final Path[] EMPTY_FILE_LIST = new Path[0];

    private FileUtil() {
    }

    public static Path[] listFiles(String path, String extension) throws IOException {
        Path[] paths = EMPTY_FILE_LIST;
        if (path.endsWith("**")) {
            String dir = path.substring(0, path.length() - 2);
            if (Files.isDirectory(Paths.get(dir))) {
                paths = listFiles(Paths.get(dir), extension, true);
            } else if (!Files.exists(Paths.get(dir))) {
                throw new IOException("Directory " + dir + " does not exist");
            }
        } else if (path.endsWith("*")) {
            String dir = path.substring(0, path.length() - 1);
            if (Files.isDirectory(Paths.get(dir))) {
                paths = listFiles(Paths.get(dir), extension, false);
            } else if (!Files.exists(Paths.get(dir))) {
                throw new IOException("Directory " + dir + " does not exist");
            }
        } else {
            if (extension != null) {
                paths = path.endsWith(extension) ? new Path[]{Paths.get(path)} : new Path[]{Paths.get(path + extension)};
            } else {
                paths = new Path[]{Paths.get(path)};
            }
        }
        return paths;
    }

    private static Path[] listFiles(Path path, String extension, boolean recursive) throws IOException {
        List<Path> directories = new LinkedList<>();
        List<Path> list = new LinkedList<>();

        if (Files.isDirectory(path)) {
            directories.add(path);
        } else {
            if (extension != null && path.toString().endsWith(extension))
                list.add(path);
            else if (extension == null)
                list.add(path);
        }

        while (!directories.isEmpty()) {
            Path parent = directories.remove(0);
            try (Stream<Path> stream = Files.list(parent)) {
                stream.forEach(f -> {
                    if (Files.isDirectory(f)) {
                        if (recursive) directories.add(f);
                    } else {
                        // f is a file
                        if (extension != null && f.toString().endsWith(extension)) {
                            list.add(f);
                        } else if (extension == null) {
                            list.add(f);
                        }
                    }
                });
            }
        }
        return list.toArray(new Path[list.size()]);
    }
}
