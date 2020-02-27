/*
 * Copyright 2015-2019 Futeh Kao
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

import java.io.File;
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

    private static final String[] EMPTY_FILE_LIST = new String[0];

    private FileUtil() {
    }

    public static String[] listFiles(String path, String extension) throws IOException {
        String[] paths = EMPTY_FILE_LIST;
        if (path.endsWith("**")) {
            String dir = path.substring(0, path.length() - 2);
            if (Paths.get(dir).toFile().isDirectory()) {
                paths = listFiles(dir, extension, true);
            }
        } else if (path.endsWith("*")) {
            String dir = path.substring(0, path.length() - 1);
            if (Paths.get(dir).toFile().isDirectory()) {
                paths = listFiles(dir, extension, false);
            }
        } else {
            paths = getSingleFile(path, extension);
        }
        return paths;
    }

    private static String[] getSingleFile(String path, String extension) {
        String p = path.endsWith(extension) ? path : path + extension;
        if (path.startsWith("classpath:")) {
            return new String[]{ p };
        } else {
            File f = new File(p);
            if (!f.isDirectory() && f.exists())
                return new String[] { p };

            return EMPTY_FILE_LIST;
        }
    }

    private static String[] listFiles(String path, String extension, boolean recursive) throws IOException {
        List<String> directories = new LinkedList<>();
        List<String> list = new LinkedList<>();

        if (new File(path).isDirectory()) { // a directory
            directories.add(path);
        } else if(extension == null || path.endsWith(extension)) {
            list.add(path);
        }

        while (!directories.isEmpty()) {
            String parent = directories.remove(0);
            try (Stream<Path> stream = Files.list(Paths.get(parent))) {
                stream.forEach(f -> {
                    if (f.toFile().isDirectory()) {
                        if (recursive) directories.add(f.toString());
                    } else if (extension == null || f.toString().endsWith(extension)) {
                        list.add(f.toString());
                    }
                });
            }
        }
        return list.toArray(new String[list.size()]);
    }
}
