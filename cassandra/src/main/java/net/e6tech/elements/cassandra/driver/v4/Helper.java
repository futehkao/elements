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

package net.e6tech.elements.cassandra.driver.v4;

import com.datastax.oss.driver.api.core.data.GettableByName;
import com.datastax.oss.driver.api.core.data.SettableByName;
import com.datastax.oss.driver.api.mapper.MapperContext;
import com.datastax.oss.driver.api.mapper.entity.saving.NullSavingStrategy;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.delete.DeleteSelection;
import com.datastax.oss.driver.api.querybuilder.insert.InsertInto;
import com.datastax.oss.driver.api.querybuilder.insert.RegularInsert;
import com.datastax.oss.driver.api.querybuilder.relation.Relation;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.datastax.oss.driver.api.querybuilder.select.SelectFrom;
import com.datastax.oss.driver.api.querybuilder.update.UpdateStart;
import com.datastax.oss.driver.internal.mapper.entity.EntityHelperBase;
import com.datastax.oss.driver.internal.querybuilder.update.DefaultUpdate;
import net.e6tech.elements.cassandra.etl.Inspector;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;

import java.util.HashSet;
import java.util.Set;

public class Helper<T> extends EntityHelperBase<T> {
    private static final Logger LOG = Logger.getLogger();
    private Class<T> entityClass;
    private Inspector inspector;

    public Helper(MapperContext context, Class<T> entityClass, Inspector inspector) {
        super(context, inspector.tableName(entityClass));
        LOG.debug("[{}] Entity will be mapped to {}{}",
                context.getSession().getName(),
                getKeyspaceId() == null ? "" : getKeyspaceId() + ".",
                getTableId());

        this.entityClass = entityClass;
        this.inspector = inspector;
    }

    @Override
    public Class<T> getEntityClass() {
        return entityClass;
    }

    @SuppressWarnings("squid:S1905") // this is just nonsense from sonarlint.
    @Override
    public <S extends SettableByName<S>> S set(T entity,
                                               S target,
                                               NullSavingStrategy nullSavingStrategy) {

        for (Inspector.ColumnAccessor accessor : inspector.getColumns()) {
            Object value = accessor.get(entity);
            if (value != null || nullSavingStrategy == NullSavingStrategy.SET_TO_NULL) {
                target = (S) target.set(accessor.getColumnName(), value, accessor.getType());
            }
        }

        return target;
    }

    @Override
    public T get(GettableByName source) {
        T returnValue = null;
        try {
            returnValue = entityClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new SystemException(e);
        }

        for (Inspector.ColumnAccessor accessor : inspector.getColumns()) {
            accessor.set(returnValue, source.get(accessor.getColumnName(), accessor.getType()));
        }
        return returnValue;
    }

    @Override
    public RegularInsert insert() {
        throwIfKeyspaceMissing();
        InsertInto insertInto = (keyspaceId == null)
                ? QueryBuilder.insertInto(tableId)
                : QueryBuilder.insertInto(keyspaceId, tableId);

        RegularInsert regularInsert = null;
        boolean first = true;
        for (Inspector.ColumnAccessor accessor : inspector.getColumns()) {
            String column = accessor.getColumnName();
            if (first) {
                first = false;
                regularInsert = insertInto.value(column, QueryBuilder.bindMarker(column));
            } else {
                regularInsert = regularInsert.value(column, QueryBuilder.bindMarker(column));
            }
        }
        return regularInsert;
    }

    @Override
    public Select selectByPrimaryKey() {
        Select selectStart =  selectStart();
        boolean first = true;
        Select select = null;
        for (Inspector.ColumnAccessor accessor : inspector.getPrimaryKeyColumns()) {
            String column = accessor.getColumnName();
            if (first) {
                first = false;
                select = selectStart.whereColumn(column).isEqualTo(QueryBuilder.bindMarker(column));
            } else {
                select = select.whereColumn(column).isEqualTo(QueryBuilder.bindMarker(column));
            }
        }
        return select;
    }

    @Override
    public Select selectStart() {
        throwIfKeyspaceMissing();
        SelectFrom selectFrom = (keyspaceId == null)
                ? QueryBuilder.selectFrom(tableId)
                : QueryBuilder.selectFrom(keyspaceId, tableId);

        Select select = null;
        boolean first = true;
        for (Inspector.ColumnAccessor accessor : inspector.getColumns()) {
            String column = accessor.getColumnName();
            if (first) {
                first = false;
                select = selectFrom.column(column);
            } else {
                select = select.column(column);
            }
        }

        return select;
    }

    @Override
    public Delete deleteByPrimaryKey() {
        throwIfKeyspaceMissing();
        DeleteSelection deleteFrom = (keyspaceId == null)
                ? QueryBuilder.deleteFrom(tableId)
                : QueryBuilder.deleteFrom(keyspaceId, tableId);

        Delete delete = null;
        boolean first = true;
        for (Inspector.ColumnAccessor accessor : inspector.getPrimaryKeyColumns()) {
            String column = accessor.getColumnName();
            if (first) {
                first = false;
                delete = deleteFrom.whereColumn(column).isEqualTo(QueryBuilder.bindMarker(column));
            } else {
                delete = delete.whereColumn(column).isEqualTo(QueryBuilder.bindMarker(column));
            }
        }

        return delete;
    }

    @Override
    public DefaultUpdate updateStart() {
        throwIfKeyspaceMissing();
        UpdateStart updateSt = (keyspaceId == null)
                ? QueryBuilder.update(tableId)
                : QueryBuilder.update(keyspaceId, tableId);

        Set<String> keyColumns = new HashSet<>();
        for (Inspector.ColumnAccessor accessor : inspector.getPrimaryKeyColumns()) {
            keyColumns.add(accessor.getColumnName());
        }

        DefaultUpdate update = null;
        boolean first = true;
        for (Inspector.ColumnAccessor accessor : inspector.getColumns()) {
            String column = accessor.getColumnName();
            if (keyColumns.contains(column))
                continue;
            if (first) {
                first = false;
                update = (DefaultUpdate) updateSt.setColumn(column, QueryBuilder.bindMarker(column));
            } else {
                update = (DefaultUpdate) update.whereColumn(column).isEqualTo(QueryBuilder.bindMarker(column));
            }
        }

        return update;
    }

    @Override
    public DefaultUpdate updateByPrimaryKey() {
        DefaultUpdate update = null;
        boolean first = true;
        for (Inspector.ColumnAccessor accessor : inspector.getPrimaryKeyColumns()) {
            String column = accessor.getColumnName();
            if (first) {
                first = false;
                update = (DefaultUpdate) updateStart().where(Relation.column("\"" + column + "\"").isEqualTo(QueryBuilder.bindMarker("\"" + column + "\"")));
            } else {
                update = (DefaultUpdate) update.where(Relation.column("\"" + column + "\"").isEqualTo(QueryBuilder.bindMarker("\"" + column + "\"")));
            }
        }

        return update;
    }
}

