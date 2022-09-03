/*
 * Copyright 2015-2022 Futeh Kao
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

package net.e6tech.elements.persist.mariadb;

import org.mariadb.jdbc.UrlParser;
import org.mariadb.jdbc.internal.util.DeRegister;
import org.mariadb.jdbc.internal.util.constant.HaMode;
import org.mariadb.jdbc.internal.util.constant.Version;
import org.mariadb.jdbc.util.DefaultOptions;
import org.mariadb.jdbc.util.Options;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class Driver implements java.sql.Driver {
    private List<ConnectionListener> listeners = Collections.synchronizedList(new ArrayList<>());

    static {
        try {
            DriverManager.registerDriver(new Driver(), new DeRegister());
        } catch (SQLException e) {
            throw new RuntimeException("Could not register driver", e);
        }
    }

    public void addListener(ConnectionListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ConnectionListener listener) {
        listeners.remove(listener);
    }

    /**
     * Connect to the given connection string.
     *
     * @param url the url to connect to
     * @return a connection
     * @throws SQLException if it is not possible to connect
     */
    public Connection connect(final String url, final Properties props) throws SQLException {
        UrlParser urlParser = UrlParser.parse(url, props);
        if (urlParser == null || urlParser.getHostAddresses() == null) {
            return null;
        } else {
            if (!listeners.isEmpty())
                listeners.forEach(ConnectionListener::onConnect);
            return MariaDbConnectionExt.newConnection(urlParser, null);
        }
    }

    /**
     * returns true if the driver can accept the url.
     *
     * @param url the url to test
     * @return true if the url is valid for this driver
     */
    @Override
    public boolean acceptsURL(String url) {
        return UrlParser.acceptsUrl(url);
    }

    /**
     * Get the property info.
     *
     * @param url the url to get properties for
     * @param info the info props
     * @return all possible connector options
     * @throws SQLException if there is a problem getting the property info
     */
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        Options options;
        if (url != null && !url.isEmpty()) {
            UrlParser urlParser = UrlParser.parse(url, info);
            if (urlParser == null || urlParser.getOptions() == null) {
                return new DriverPropertyInfo[0];
            }
            options = urlParser.getOptions();
        } else {
            options = DefaultOptions.parse(HaMode.NONE, "", info, null);
        }

        List<DriverPropertyInfo> props = new ArrayList<>();
        for (DefaultOptions o : DefaultOptions.values()) {
            try {
                Field field = Options.class.getField(o.getOptionName());
                Object value = field.get(options);
                DriverPropertyInfo propertyInfo =
                        new DriverPropertyInfo(field.getName(), value == null ? null : value.toString());
                propertyInfo.description = o.getDescription();
                propertyInfo.required = o.isRequired();
                props.add(propertyInfo);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // eat error
            }
        }
        return props.toArray(new DriverPropertyInfo[props.size()]);
    }

    /**
     * gets the major version of the driver.
     *
     * @return the major versions
     */
    public int getMajorVersion() {
        return Version.majorVersion;
    }

    /**
     * gets the minor version of the driver.
     *
     * @return the minor version
     */
    public int getMinorVersion() {
        return Version.minorVersion;
    }

    /**
     * checks if the driver is jdbc compliant.
     *
     * @return true since the driver is not compliant
     */
    public boolean jdbcCompliant() {
        return true;
    }

    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Use logging parameters for enabling logging.");
    }
}
