/*
 * Copyright 2017 Futeh Kao
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

package net.e6tech.elements.persist.hibernate;

import org.hibernate.MappingException;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.relational.QualifiedName;
import org.hibernate.boot.model.relational.QualifiedNameParser;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.id.enhanced.StandardOptimizerDescriptor;
import org.hibernate.id.enhanced.TableGenerator;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

import java.util.Properties;

/**
 * This class uses a table to generate primary key.
 * Created by futeh.
 */
public class TableIdGenerator extends TableGenerator implements Cloneable {

    private String defaultTableName = "sequence";
    private int defaultInitialValue = 1;
    private int defaultIncrementSize = 100;

    public TableIdGenerator clone() {
        try {
            return (TableIdGenerator) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public String getDefaultTableName() {
        return defaultTableName;
    }

    public void setDefaultTableName(String defaultTableName) {
        this.defaultTableName = defaultTableName;
    }

    public int getDefaultInitialValue() {
        return defaultInitialValue;
    }

    public void setDefaultInitialValue(int defaultInitialValue) {
        this.defaultInitialValue = defaultInitialValue;
    }

    public int getDefaultIncrementSize() {
        return defaultIncrementSize;
    }

    public void setDefaultIncrementSize(int defaultIncrementSize) {
        this.defaultIncrementSize = defaultIncrementSize;
    }

    protected QualifiedName determineGeneratorTableName(Properties params, JdbcEnvironment jdbcEnvironment) {
        final String tableName = ConfigurationHelper.getString( TABLE_PARAM, params, defaultTableName );

        if ( tableName.contains( "." ) ) {
            return QualifiedNameParser.INSTANCE.parse( tableName );
        } else {
            final Identifier catalog = jdbcEnvironment.getIdentifierHelper().toIdentifier(
                    ConfigurationHelper.getString( CATALOG, params )
            );
            final Identifier schema = jdbcEnvironment.getIdentifierHelper().toIdentifier(
                    ConfigurationHelper.getString( SCHEMA, params )
            );
            return new QualifiedNameParser.NameParts(
                    catalog,
                    schema,
                    jdbcEnvironment.getIdentifierHelper().toIdentifier( tableName )
            );
        }
    }

    protected int determineInitialValue(Properties params) {
        return ConfigurationHelper.getInt( INITIAL_PARAM, params, defaultInitialValue );
    }

    protected String determineDefaultSegmentValue(Properties params) {
        return params.getProperty( TABLE );
    }

    protected int determineIncrementSize(Properties params) {
        return ConfigurationHelper.getInt( INCREMENT_PARAM, params, defaultIncrementSize );
    }

    @Override
    public void configure(Type type, Properties params, ServiceRegistry serviceRegistry) throws MappingException {
        if (params.getProperty(OPT_PARAM) == null) {
            params.setProperty(OPT_PARAM, StandardOptimizerDescriptor.POOLED_LO.getExternalName());
        }
        super.configure(type, params, serviceRegistry);
    }
}
