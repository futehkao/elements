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

import net.e6tech.elements.persist.datasource.hikari.ElementsHikariDataSource
import javax.persistence.EntityManager
import net.e6tech.elements.persist.hibernate.HibernateEntityManagerProvider
import net.e6tech.elements.persist.hibernate.TableIdGenerator

atom("datasource2") {
    /* below is for Hikari */
    configuration = """
        dataSource2:
            driverClassName: org.mariadb.jdbc.Driver
            username: sample
            password: password
            jdbcUrl: "jdbc:mariadb://127.0.0.1:3306/sample"
            maximumPoolSize: $dataSourceMaxPoolSize
            transactionIsolation: 'TRANSACTION_READ_COMMITTED'
            autoCommit: false
    """

    /* below is for c3p0
    configuration = """
        dataSource:
            driverClassName: org.mariadb.jdbc.Driver
            user: sample
            password: password
            jdbcUrl: "jdbc:mariadb://127.0.0.1:3306/sample"
            maxPoolSize: $dataSourceMaxPoolSize
            checkoutTimeout: 30000
            autoCommitOnClose: false
    """
    */

    dataSource2 = ElementsHikariDataSource
    //  To set init statements
    // dataSource.connectionInitStatements = dataSourceConnectionInitStatements
    // dataSource = ComboPooledDataSource
}

atom("persist2") {

    configuration = """
        entityManagerProvider2.persistenceUnitName: sample
        entityManagerProvider2.providerName: sample-rw
        entityManagerProvider2.transactionTimeout: ${entityManagerTxTimeout}
        entityManagerProvider2.monitorTransaction: ${entityManagerMonitorTransaction}
        entityManagerProvider2.longTransaction: ${entityManagerLongTransaction}
        entityManagerProvider2.persistenceProperties:
            javax.persistence.nonJtaDataSource: ^dataSource2
        _tableId:
            defaultIncrementSize: 20
            defaultInitialValue: ^ 10 + _tableId.defaultIncrementSize
        _tableId2:
            defaultIncrementSize: 30
    """

    entityManagerProvider2 = HibernateEntityManagerProvider

    _tableId = TableIdGenerator
    _tableId2 = TableIdGenerator
    entityManagerProvider2.register('tableId', _tableId)
            .register('tableId2', _tableId2)
}
