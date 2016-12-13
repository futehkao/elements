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
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRTextExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleTextExporterConfiguration;
import net.sf.jasperreports.export.SimpleTextReportConfiguration;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;
import net.sf.jasperreports.view.JasperViewer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by futeh on 1/14/16.
 */
public class PrintImpl implements Print {
    private JasperPrint print;

    public PrintImpl(JasperPrint print) {
        this.print = print;
    }

    public void exportToPdfFile(String outputFile) throws IOException {
        try {
            JasperExportManager.exportReportToPdfFile(print, outputFile);
        } catch (JRException e) {
            throw new IOException(e);
        }
    }

    public void exportToPdfStream(OutputStream outputStream) throws IOException {
        try {
            JasperExportManager.exportReportToPdfStream(print, outputStream);
        } catch (JRException e) {
            throw new IOException(e);
        } finally {
            outputStream.close();
        }
    }

    public void exportToHtmlFile(String outputFile) throws IOException {
        try {
            JasperExportManager.exportReportToHtmlFile(print, outputFile);
        } catch (JRException e) {
            throw new IOException(e);
        }
    }

    public void exportToTextFile(String outputFile) throws IOException {
        try {
            JRTextExporter exporter = buildJRTextExporter();
            File destFile = new File(outputFile);
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleWriterExporterOutput(destFile));
            exporter.exportReport();
        } catch (JRException e) {
            throw new IOException(e);
        }
    }

    public void exportToTextStream(OutputStream outputStream) throws IOException {
        try {
            JRTextExporter exporter = buildJRTextExporter();
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleWriterExporterOutput(outputStream));
            exporter.exportReport();
        } catch (JRException e) {
            throw new IOException(e);
        }
    }

    private JRTextExporter buildJRTextExporter() {
        JRTextExporter exporter = new JRTextExporter();
        SimpleTextExporterConfiguration textExportConfig = new SimpleTextExporterConfiguration();
        exporter.setConfiguration(textExportConfig);

        SimpleTextReportConfiguration textReportConfig = new SimpleTextReportConfiguration();
        textReportConfig.setCharHeight(new Float(13.948f));
        textReportConfig.setCharWidth(new Float(7.238f));
        textReportConfig.setPageWidthInChars(new Integer(80));
        textReportConfig.setPageHeightInChars(new Integer(71));
        textReportConfig.setOverrideHints(false);
        exporter.setConfiguration(textReportConfig);

        return exporter;
    }

    @Override
    public void view() {
        JasperViewer.viewReport(print);
    }
}
