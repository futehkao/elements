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
            if (Paths.get(dir).toFile().isDirectory()) {
                paths = listFiles(Paths.get(dir), extension, true);
            } else if (!Paths.get(dir).toFile().exists()) {
                throw new IOException("Directory " + dir + " does not exist");
            }
        } else if (path.endsWith("*")) {
            String dir = path.substring(0, path.length() - 1);
            if (Paths.get(dir).toFile().isDirectory()) {
                paths = listFiles(Paths.get(dir), extension, false);
            } else if (!Paths.get(dir).toFile().exists()) {
                throw new IOException("Directory " + dir + " does not exist");
            }
        } else {
            paths = getSingleFile(path, extension);
        }
        return paths;
    }

    private static Path[] getSingleFile(String path, String extension) {
        if (path.startsWith("classpath:")) {
            Path p = path.endsWith(extension) ? Paths.get(path) : Paths.get(path + extension);
            return new Path[]{ p };
        } else {
            Path p = Paths.get(path);
            if (extension != null) {
                p = path.endsWith(extension) ? Paths.get(path) : Paths.get(path + extension);
            }

            if (!p.toFile().isDirectory() && p.toFile().exists())
                return new Path[] { p };

            return EMPTY_FILE_LIST;
        }
    }

    private static Path[] listFiles(Path path, String extension, boolean recursive) throws IOException {
        List<Path> directories = new LinkedList<>();
        List<Path> list = new LinkedList<>();

        if (path.toFile().isDirectory()) { // a directory
            directories.add(path);
        } else if(extension == null || path.toString().endsWith(extension)) {
            list.add(path);
        }

        while (!directories.isEmpty()) {
            Path parent = directories.remove(0);
            try (Stream<Path> stream = Files.list(parent)) {
                stream.forEach(f -> {
                    if (f.toFile().isDirectory()) {
                        if (recursive) directories.add(f);
                    } else if (extension == null ||f.toString().endsWith(extension)) {
                        list.add(f);
                    }
                });
            }
        }
        return list.toArray(new Path[list.size()]);
    }
}
