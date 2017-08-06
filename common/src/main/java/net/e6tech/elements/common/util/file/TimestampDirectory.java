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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * This class is used to return a new file output stream so that it's location
 * is located in a directory of which name is in a timestamp form.
 *
 * Created by futeh.
 */
public class TimestampDirectory {

    private String directory;

    public TimestampDirectory() {}

    public TimestampDirectory(String directory) {
        this.directory = directory;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String outDir) {
        String outputDirectory = outDir;
        while (outputDirectory.endsWith(File.separator) || outputDirectory.endsWith("/"))
            outputDirectory = outputDirectory.substring(0, outputDirectory.length() - 1);
        this.directory = outputDirectory;
    }

    public Location open() {
        return new Location(directory);
    }

    public Location open(String baseDirectory) {
        return new Location(directory, baseDirectory);
    }

    public static class Location {
        private String rootDirectory;
        private String baseDirectory;
        private String directoryTimestampPattern = "yyyy/MM/dd";
        private String fileExtension;
        private String fileName;
        private String fileTimestampPattern = "yyyyMMddHHmm";

        public Location(String rootDirectory) {
            this.rootDirectory = rootDirectory;
        }

        public Location(String rootDirectory,
                        String baseDirectory) {
            this.rootDirectory = rootDirectory;
            this.baseDirectory = baseDirectory;
        }

        public String baseDirectory() {
            return baseDirectory;
        }

        public Location baseDirectory(String rootDirectory) {
            this.baseDirectory = rootDirectory;
            return this;
        }

        public String directoryTimestampPattern() {
            return directoryTimestampPattern;
        }

        public Location directoryTimestampPattern(String directoryTimestampPattern) {
            this.directoryTimestampPattern = directoryTimestampPattern;
            return this;
        }

        public String fileName() {
            return fileName;
        }

        public Location fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public String fileExtension() {
            return fileExtension;
        }

        public Location fileExtension(String extension) {
            this.fileExtension = extension;
            return this;
        }

        public String fileTimestampPattern() {
            return fileTimestampPattern;
        }

        public Location fileTimestampPattern(String fileTimestampPattern) {
            this.fileTimestampPattern = fileTimestampPattern;
            return this;
        }

        public OutputStream getOutputStream() throws IOException {
            return Files.newOutputStream(getOutputPath());
        }

        public OutputStream getOutputStream(String fileName, String extension) throws IOException {
            return Files.newOutputStream(getOutputPath(fileName, extension));
        }

        public OutputStream getOutputStream(String file, String ext, ZonedDateTime timestamp) throws IOException {
        	return Files.newOutputStream(getOutputPath(file,ext,timestamp));
        }

        public Path getOutputPath() throws IOException {
        	return getOutputPath(fileName, null, null);
        }

        public Path getOutputPath(String fileName, String extension) throws IOException {
            return getOutputPath(fileName, extension, null);
        }

        @SuppressWarnings("squid:MethodCyclomaticComplexity")
        public Path getOutputPath(String file, String ext, ZonedDateTime timestamp) throws IOException {
            if (file != null)
                fileName = file;
            if (ext != null)
                fileExtension = ext;

            if (fileName == null)
                throw new IOException("File name is not specified");
            String dir;
            String timestampDir = "";
            if (directoryTimestampPattern != null && timestamp != null)
                timestampDir = "/" + timestamp.format(DateTimeFormatter.ofPattern(directoryTimestampPattern));
            if (baseDirectory != null) {
                dir = rootDirectory + "/" + baseDirectory + timestampDir;
            } else {
                dir = rootDirectory + timestampDir;
            }

            Path dirPath = Files.createDirectories(Paths.get(dir));

            String fullPath = fileName;

            if (fileTimestampPattern != null && timestamp != null) {
                fullPath += timestamp.format(DateTimeFormatter.ofPattern(fileTimestampPattern));
            }

            if (fileExtension != null) {
                if (!fileExtension.startsWith("."))
                    fileExtension = "." + fileExtension;
                fullPath += fileExtension;
            }

            return Paths.get(dirPath.toString(), fullPath);
        }
    }
}
