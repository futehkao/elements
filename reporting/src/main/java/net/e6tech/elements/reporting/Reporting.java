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
import java.io.IOException;
import java.util.List;
import java.util.Map;

import net.e6tech.elements.reporting.text.TextReportingImpl;

/**
 * Created by futeh on 1/14/16.
 */
public abstract class Reporting {

    private String baseDir = "";

    public static Reporting getInstance() {
        return new TextReportingImpl();
    }

    public static Reporting getInstance(String engine) {
        if (!engine.equals("text")) throw new IllegalArgumentException("Unsupported reporting engine: " + engine);
        return new TextReportingImpl();
    }

    public Reporting() {
    }

    public Reporting(String baseDir) {
        setBaseDir(baseDir);
    }

    public String getBaseDir() {
        return baseDir;
    }

    public Reporting baseDir(String baseDir) {
        setBaseDir(baseDir);
        return this;
    }

    public void setBaseDir(String baseDir) {
        if (baseDir == null || baseDir.trim().length() == 0)  {
            // do nothing
        }else if (!baseDir.endsWith(File.separator) && !baseDir.endsWith("/")) {
            this.baseDir = baseDir.trim() + "/";
        } else {
            this.baseDir = baseDir.trim();
        }
    }

    protected ReportInfo info(String path) {
        return new ReportInfo(getBaseDir(), path);
    }

    public abstract Object loadObject(String path) throws IOException;

    public abstract Print report(String mainReport, Map<String, Object> params, List data) throws IOException ;

}
