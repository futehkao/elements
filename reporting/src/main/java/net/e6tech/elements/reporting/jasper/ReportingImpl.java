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

package net.e6tech.elements.reporting.jasper;

import net.e6tech.elements.reporting.Print;
import net.e6tech.elements.reporting.ReportInfo;
import net.e6tech.elements.reporting.Reporting;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.xml.JRXmlLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by futeh on 1/14/16.
 */
public class ReportingImpl extends Reporting {

    private static Map<String, ReportInfo> cache = new Hashtable<>();

    public ReportingImpl() {
    }

    public ReportingImpl(String baseDir) {
        super(baseDir);
    }

    public Object loadObject(String path) throws IOException {
        JasperReport report = loadReport(path);
        if (report == null) {
            return info(path).getFullPath();
        }
        return report;
    }

    protected JasperReport loadReport(String path) throws IOException {
        ReportInfo reportInfo = info(path);
        if (cache.get(reportInfo.getName()) != null) return cache.get(reportInfo.getName()).value(getJasperReport());

        if (reportInfo.getExtension().equals(".jasper")) {
            ReportInfo jrxmlFile = reportInfo.withExtension(".jrxml");
            if (jrxmlFile.exists() && jrxmlFile.getTimeStamp() > reportInfo.getTimeStamp()) {
                reportInfo = jrxmlFile;
            }
        }

        JasperReport report = null;
        if (reportInfo.exists()) {
            report = reportInfo.value(getJasperReport());
            if (report != null) {
                cache.put(reportInfo.getName(), reportInfo);
            }
        }
        return report;
    }

    private Function<ReportInfo, JasperReport> getJasperReport() {
        return (info)-> {
            if (info.getExtension().equals(".jasper")) {
                ReportInfo jrxmlFile = info.withExtension(".jrxml");
                if (jrxmlFile.exists() && jrxmlFile.getTimeStamp() > info.getTimeStamp()) {
                    info = jrxmlFile;
                    info.changeExtension(".jrxml");
                }
            }

            try (InputStream in = info.getInputStream()){
                if (info.getExtension().equals(".jrxml"))
                    return compile(in);
                else if (info.getExtension().equals(".jasper"))
                    return (JasperReport) JRLoader.loadObject(in);
            } catch (JRException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        };
    }

    private JasperReport compile(InputStream in) throws JRException {
        JasperDesign design = JRXmlLoader.load(in);
        return JasperCompileManager.compileReport(design);
    }

    public Print report(String mainReport, Map<String, Object> params, List data) throws IOException {
        JasperReport report = loadReport(mainReport);
        if (params == null) params = new HashMap<>();
        params.put("Reporting", this);
        try {
            JasperPrint print = JasperFillManager.fillReport(report, params, new JRBeanCollectionDataSource(data));
            return new PrintImpl(print);
        } catch (JRException e) {
            throw new IOException(e);
        }
    }
}
