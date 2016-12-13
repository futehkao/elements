/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/


package net.e6tech.elements.reporting;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

/**
 * Created by futeh on 1/14/16.
 */
public class ReportInfo implements Cloneable {
    private String baseDir = "";
    private String fullPath;
    private String fileName;
    private String extension;
    private long timeStamp;
    private Object value;
    private boolean classpath = false;

    public ReportInfo(String baseDir, String file) {
        if (baseDir == null) baseDir = "";
        this.baseDir = baseDir.trim();
        init(file);
    }

    public void init(String file) {
        fullPath = file;
        if (baseDir != null && baseDir.length() > 0) {
            while (fullPath.startsWith(File.separator) || fullPath.startsWith("/")) fullPath = fullPath.substring(1);
            fullPath = baseDir + fullPath;
        }

        File f = new File(fullPath);
        String name = f.getName();
        int idx = name.lastIndexOf(".");
        if (idx > 0) {
            idx = fullPath.lastIndexOf(".");
            fileName = fullPath.substring(0, idx);
            extension = fullPath.substring(idx).toLowerCase();
        } else {
            fileName = fullPath;
            extension = "";
        }
        exists();
    }

    public String getFullPath() {
        return fullPath;
    }

    public String getName() {
        return fileName;
    }

    public String getExtension() {
        return extension;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void changeExtension(String ext) {
        if (extension.length() > 0) {
            int idx = fullPath.lastIndexOf(".");
            if (ext == null || ext.trim().length() == 0) {
                ext = "";
            } else if (!ext.startsWith(".")) {
                ext = "." + ext;
            }
            fullPath = fullPath.substring(0, idx) + ext;
        }
        extension = ext;
        exists();
    }

    public ReportInfo withExtension(String ext) {
        ReportInfo info = (ReportInfo) clone();
        info.extension = ext;
        if (extension.length() > 0) {
            int idx = fullPath.lastIndexOf(".");
            if (ext == null || ext.trim().length() == 0) {
                ext = "";
            } else if (!ext.startsWith(".")) {
                ext = "." + ext;
            }
            info.fullPath = fullPath.substring(0, idx) + ext;
        }
        return info;
    }

    public boolean exists() {
        boolean found = false;
        File file = new File(getFullPath());
        if (file.exists()) {
            classpath = false;
            found = true;
            this.timeStamp = file.lastModified();
        } else if (getClass().getClassLoader().getResource(getFullPath()) != null) {
            classpath = true;
            found = true;
            this.timeStamp = System.currentTimeMillis();
        }

        return found;
    }

    public InputStream getInputStream() throws IOException {
        File file = new File(getFullPath());
        if (file.exists()) {
            return new FileInputStream(file);
        } else {
            return getClass().getClassLoader().getResourceAsStream(getFullPath());
        }
    }

    public <T> T value(Function<ReportInfo, T> function ) {
        if (value == null) {
            value = function.apply(this);
            return (T) value;
        }
        if (classpath) {
            return (T) value;
        } else {
            File file = new File(getFullPath());
            if (file.lastModified() != timeStamp) {
                value = function.apply(this);
                timeStamp = file.lastModified();
            }
        }
        return (T) value;
    }

    protected ReportInfo clone() {
        try {
            return (ReportInfo) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public int hashCode() {
        return fileName.hashCode();
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof ReportInfo)) return false;
        if (getName() == null) return false;
        return getName().equals(((ReportInfo) obj).getName());
    }
}