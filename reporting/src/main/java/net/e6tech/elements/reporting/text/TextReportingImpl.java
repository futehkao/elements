package net.e6tech.elements.reporting.text;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import net.e6tech.elements.reporting.Print;
import net.e6tech.elements.reporting.Reporting;

public class TextReportingImpl extends Reporting {

    @Override
    public Object loadObject(String path) throws IOException {
        throw new RuntimeException("not yet implemented");
    }

    @Override
    public Print report(String mainReport, Map<String, Object> params, List data) throws IOException {
        throw new RuntimeException("not yet implemented");
    }
}
