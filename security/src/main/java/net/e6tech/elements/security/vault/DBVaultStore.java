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

package net.e6tech.elements.security.vault;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import net.e6tech.elements.common.resources.Retry;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static net.e6tech.elements.security.vault.Constants.mapper;

/**
 * Created by futeh on 1/4/16.
 */
public class DBVaultStore implements VaultStore {

    private String tableName = "h3_vault";
    private long latestRefreshPeriod = 10 * 60 * 1000L;

    private Map<String, DBVault> vaults = new HashMap<>();
    private DataSource dataSource;

    @Inject(optional = true)
    private Retry retry;

    public DBVaultStore() {}

    public DBVaultStore(DataSource ds) {
        this.dataSource = ds;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long getLatestRefreshPeriod() {
        return latestRefreshPeriod;
    }

    public void setLatestRefreshPeriod(long latestRefreshPeriod) {
        this.latestRefreshPeriod = latestRefreshPeriod;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public VaultStore manage(String ... vaultNames) {
        if (vaultNames == null) return this;
        for (String vaultName : vaultNames) {
            if (vaults.get(vaultName) == null) {
                vaults.put(vaultName, new DBVault(vaultName));
            }
        }
        return this;
    }

    @Override
    public VaultStore unmanage(String vaultName) {
        vaults.remove(vaultName);
        return this;
    }

    protected Retry getRetry() {
        if (retry == null) {
            retry = new Retry() {
                @Override
                public boolean shouldRetry(Throwable th) {
                    return false;
                }
            };
        }
        return retry;
    }

    public Vault getVault(String vaultName) {
        return vaults.get(vaultName);
    }

    public void backup(String version) throws IOException {
        copy(true, version);
    }

    public void restore(String version) throws IOException {
        copy(false, version);
    }

    private void commitOrAbort(Connection connection, Exception exception) throws Exception {
        if (connection != null) {
            if (exception == null) {
                connection.commit();
            } else {
                connection.rollback();
            }
            connection.close();
        }
        if (exception != null) throw exception;
    }

    protected void copy(boolean backup, String version) throws IOException {
        try {
            getRetry().retry(() -> {
                Connection connection = null;
                Exception exception = null;
                try {
                    connection = dataSource.getConnection();
                    for (Map.Entry<String, DBVault> entry : vaults.entrySet()) {
                        if (backup) entry.getValue().backup(connection, version);
                        else entry.getValue().restore(connection, version);
                    }
                } catch (SQLException ex) {
                    exception = ex;
                } finally {
                    commitOrAbort(connection, exception);
                }
                return null;
            });
        } catch (Throwable th) {
            throw new IOException(th);
        }
    }

    @Override
    public void save() throws IOException {
        if (dataSource == null) throw new IOException("null data source");
        try {
            getRetry().retry(() -> {
                Exception exception = null;
                Connection connection = null;
                try {
                    connection = dataSource.getConnection();
                    for (Map.Entry<String, DBVault> entry : vaults.entrySet()) {
                        entry.getValue().save(connection);
                    }
                } catch (SQLException ex) {
                    exception = ex;
                } finally {
                    commitOrAbort(connection, exception);
                }
                return null;
            });

        } catch (Throwable th) {
            throw new IOException(th);
        }
    }

    @Override
    public void open() throws IOException {
    }

    @Override
    public void close() throws IOException {
        if (dataSource != null) {
            if (dataSource instanceof Closeable) {
                Closeable closeable = (Closeable) dataSource;
                closeable.close();
            }
        }
    }

    public String writeString() throws IOException {
        Connection connection = null;
        PreparedStatement pstmt = null;
        try {
            Map<String, VaultImpl> result = new LinkedHashMap<>();
            // read from database
            pstmt = connection.prepareStatement("select v.secret from " + tableName + " v where v.name = ? ");

            for (DBVault v : vaults.values()) {
                VaultImpl impl = new VaultImpl();
                pstmt.setString(1, v.getName());
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    String encoded = rs.getString(1);
                    try {
                        Secret secret = mapper.readValue(encoded, Secret.class);
                        impl.addSecret(secret);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                rs.close();
            }
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(new VaultFormat(result));
        } catch (JsonProcessingException e) {
            throw new IOException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (pstmt != null) try { pstmt.close(); } catch (SQLException ex) {}
        }
    }

    private class DBVault implements Vault {
        List<Secret> addedSecrets = new ArrayList<>();
        String name;
        Map<String, SortedMap<String, Secret>> cache = new HashMap<>();
        Map<String, LatestSecret> latestSecret = new Hashtable<>();

        DBVault(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Secret getSecret(String alias, String version) {
            if (version != null) {
                synchronized (cache) {
                    SortedMap<String, Secret> versions = cache.get(alias);
                    if (versions != null) {
                        Secret secret = versions.get(version);
                        if (secret != null) return secret;
                    }
                }
            } else {
                LatestSecret l = latestSecret.get(alias);
                if (l != null && l.timestamp > System.currentTimeMillis() - latestRefreshPeriod) {
                    return l.secret;
                }
            }

            Secret secret = null;
            try {
                secret = getRetry().retry(()-> {
                    Secret ret = null;
                    Connection connection = null;
                    PreparedStatement select = null;
                    ResultSet rs = null;
                    try {
                        connection = dataSource.getConnection();
                        if (version != null) {
                            select = connection.prepareStatement("select v.secret from " + tableName + " v where v.name = ? and v.alias = ? and v.version = ? ");
                            select.setLong(3, new Long(version));
                        } else {
                            select = connection.prepareStatement("select v.secret from " + tableName + " v where v.name = ? and v.alias = ? " +
                                    "and v.version = (select max(v1.version) from " + tableName + " v1 where v1.name = ? and v1.alias = ?)");
                            select.setString(3, name);
                            select.setString(4, alias);
                        }
                        select.setString(1, name);
                        select.setString(2, alias);

                        rs = select.executeQuery();
                        String str = null;
                        if (rs.next())  str = rs.getString(1);
                        if (str == null) return null;
                        try {
                            ret = mapper.readValue(str, Secret.class);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } finally {
                        if ( rs != null) try { rs.close(); } catch (SQLException ex) {}
                        if ( select != null) try { select.close(); } catch (SQLException ex) {}
                        if ( connection != null) try { connection.close(); } catch (SQLException ex) {}
                    }
                    return ret;
                });
            } catch (Throwable th) {
                throw new RuntimeException(th);
            }

            updateCache(secret);
            if (version == null) {
                updateLatest(secret);
            }
            return secret;
        }

        @Override
        public void addSecret(Secret secret) {
            addedSecrets.add(secret);
            updateCache(secret);
            updateLatest(secret);
        }

        public void removeSecret(String alias, String version) {
            if (dataSource == null) throw new RuntimeException("null data source");

            try {
                getRetry().retry(() -> {
                    Exception exception = null;
                    Connection connection = null;
                    PreparedStatement removeVersion = null;
                    PreparedStatement removeAll = null;
                    try {
                        connection = dataSource.getConnection();
                        if (version != null) {
                            removeVersion = connection.prepareStatement("delete from " + tableName + " where name = ? and alias = ? and version = ? ");
                            removeVersion.setString(1, name);
                            removeVersion.setString(2, alias);
                            removeVersion.setLong(3, new Long(version));
                            removeAll.executeUpdate();
                        } else {
                            removeAll = connection.prepareStatement("delete from " + tableName + " where name = ? and alias = ?");
                            removeAll.setString(1, name);
                            removeAll.setString(2, alias);
                            removeAll.executeUpdate();
                        }
                        connection.commit();
                    } catch (SQLException ex) {
                        exception = ex;
                    } finally {
                        if (removeVersion != null) try {
                            removeVersion.close();
                        } catch (SQLException ex) {
                        }
                        if (removeAll != null) try {
                            removeAll.close();
                        } catch (SQLException ex) {
                        }
                        commitOrAbort(connection, exception);
                    }
                    return null;
                });
            } catch (Throwable th) {
                throw new RuntimeException(th);
            }


            Iterator<Secret> secrets = addedSecrets.iterator();
            while (secrets.hasNext()) {
                Secret secret = secrets.next();
                if (alias.equals(secret.alias())) {
                    if (version == null || version.equals(secret.version())) secrets.remove();
                }
            }

            synchronized (cache) {
                SortedMap<String, Secret> versions = cache.get(alias);
                if (versions != null) {
                    if (version == null) cache.remove(alias);
                    else versions.remove(version);
                }
            }

            synchronized (latestSecret) {
                LatestSecret latest = latestSecret.get(alias);
                if (latest != null) {
                    if (version == null || version.equals(latest.secret.version())) latestSecret.remove(alias);
                }
            }
        }

        private void updateCache(Secret secret) {
            if (secret == null) return;
            synchronized (cache) {
                SortedMap<String, Secret> versions = cache.get(secret.alias());
                if (versions == null) {
                    versions = new TreeMap<>();
                    cache.put(secret.alias(), versions);
                }
                versions.put(secret.version(), secret);
            }
        }

        private void updateLatest(Secret secret) {
            if (secret == null) return;
            latestSecret.put(secret.alias(), new LatestSecret(secret));
        }

        public Set<String> aliases() {
            if (dataSource == null) throw new RuntimeException("null data source");
            Set<String> aliases = new HashSet<>();
            for (Secret secret : addedSecrets) {
                aliases.add(secret.alias());
            }

            try {
                getRetry().retry(() -> {
                    Connection connection = null;
                    PreparedStatement pstmt = null;
                    ResultSet rs = null;
                    try {
                        connection = dataSource.getConnection();
                        pstmt = connection.prepareStatement("select distinct v.alias from " + tableName + " v where v.name = ? ");
                        pstmt.setString(1, name);
                        rs = pstmt.executeQuery();
                        while (rs.next()) {
                            String alias = rs.getString(1);
                            aliases.add(alias);
                        }
                    } finally {
                        if ( rs != null) try { rs.close(); } catch (SQLException ex) {}
                        if ( pstmt != null) try { pstmt.close(); } catch (SQLException ex) {}
                        if ( connection != null) try { connection.close(); } catch (SQLException ex) {}
                    }
                    return null;
                });
            } catch (Throwable th) {
                throw new RuntimeException(th);
            }
            return aliases;
        }

        public Set<Long> versions(String alias) {
            if (dataSource == null) throw new RuntimeException("null data source");
            Set<Long> versions;
            try {
                versions = getRetry().retry(() -> {
                    Connection connection = null;
                    PreparedStatement pstmt = null;
                    ResultSet rs = null;
                    Set<Long> vers = new LinkedHashSet<>();
                    try {
                        connection = dataSource.getConnection();
                        pstmt = connection.prepareStatement("select v.version from " + tableName + " v where v.name = ? and v.alias = ? ");
                        pstmt.setString(1, name);
                        pstmt.setString(2, alias);
                        rs = pstmt.executeQuery();
                        while (rs.next()) {
                            Long version = rs.getLong(1);
                            vers.add(version);
                        }
                    } finally {
                        if ( rs != null) try { rs.close(); } catch (SQLException ex) {}
                        if ( pstmt != null) try { pstmt.close(); } catch (SQLException ex) {}
                        if ( connection != null) try { connection.close(); } catch (SQLException ex) {}
                    }
                    return vers;
                });
            } catch (Throwable th) {
                throw new RuntimeException(th);
            }

            return versions;
        }

        @Override
        public int size() {
            return aliases().size();
        }

        public void backup(Connection connection, String version) {
            copy(connection, name, name + "." + version);
        }

        public void restore(Connection connection, String version) {
            copy(connection, name + "." + version, name);
            latestSecret.clear();
            cache.clear();
        }

        public void copy(Connection connection, String from, String to) {
            PreparedStatement select = null;
            PreparedStatement remove = null;
            PreparedStatement insert = null;
            ResultSet rs = null;
            try {
                remove = connection.prepareStatement("delete from " + tableName + " where name = ? ");
                remove.setString(1, to);
                remove.executeUpdate();
                insert = connection.prepareStatement("insert into " + tableName + "(name, alias, version, secret) values(?,?,?,?)");

                select = connection.prepareStatement("select v.name, v.alias, v.version, v.secret from " + tableName + " v " +
                        "where v.name = ? ");
                select.setString(1, from);
                rs = select.executeQuery();
                while (rs.next()) {
                    String vaultName = rs.getString(1);
                    String alias = rs.getString(2);
                    Long ver = rs.getLong(3);
                    String secret = rs.getString(4);
                    insert.setString(1, to);
                    insert.setString(2, alias);
                    insert.setLong(3, ver);
                    insert.setString(4, secret);
                    insert.executeUpdate();
                    insert.clearParameters();
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            } finally {
                if (rs != null) try { rs.close(); } catch (SQLException ex) {}
                if (select != null) try { select.close(); } catch (SQLException ex) {}
                if (remove != null) try { remove.close(); } catch (SQLException ex) {}
                if (insert != null) try { insert.close(); } catch (SQLException ex) {}
            }
        }

        public void save(Connection connection) {
            PreparedStatement count = null;
            PreparedStatement update = null;
            PreparedStatement insert = null;

            try {
                count = connection.prepareStatement("select count(*) from " + tableName + " v where v.name = ? and v.alias = ? and v.version = ? ");
                update = connection.prepareStatement("update " + tableName + " set secret = ? where name = ? and alias = ? and version = ? ");
                insert = connection.prepareStatement("insert into " + tableName + "(name, alias, version, secret) values(?,?,?,?)");

                for (Secret secret : addedSecrets) {
                    count.setString(1, getName());
                    count.setString(2, secret.alias());
                    count.setLong(3, new Long(secret.version()));
                    ResultSet rs = count.executeQuery();
                    int c = 0;
                    if (rs.next()) c = rs.getInt(1);
                    rs.close();

                    String encoded = null;
                    try {
                        encoded = mapper.writeValueAsString(secret);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    if (c == 0) {
                        insert.setString(1, name);
                        insert.setString(2, secret.alias());
                        insert.setLong(3, new Long(secret.version()));
                        insert.setString(4, encoded);
                        insert.executeUpdate();
                        insert.clearParameters();
                    } else {
                        update.setString(1, encoded);
                        update.setString(2, name);
                        update.setString(3, secret.alias());
                        update.setLong(4, new Long(secret.version()));
                        update.executeUpdate();
                        update.clearParameters();
                    }
                }
                addedSecrets.clear();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            } finally {
                if (count != null) try { count.close(); } catch (SQLException ex) {}
                if (update != null) try { update.close(); } catch (SQLException ex) {}
                if (insert != null) try { insert.close(); } catch (SQLException ex) {}
            }
        }
    }

    private class LatestSecret {
        long timestamp;
        Secret secret;
        LatestSecret(Secret secret) {
            timestamp = System.currentTimeMillis();
            this.secret = secret;
        }
    }
}
