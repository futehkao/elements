/*
 * Copyright 2016 Futeh Kao
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
import net.e6tech.elements.persist.InvocationListener

import java.lang.reflect.Method

atom("datasource") {
    /* below is for Hikari */
    configuration = """
        dataSource:
            driverClassName: net.e6tech.elements.persist.mariadb.Driver
            username: sample
            password: password
            jdbcUrl: "jdbc:mariadb://127.0.0.1:3306/sample"
            maximumPoolSize: $dataSourceMaxPoolSize
            minimumIdle: 1
            transactionIsolation: 'TRANSACTION_READ_COMMITTED'
            autoCommit: false
            registerMbeans: true
            connectionTimeout: 3000
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

    dataSource = ElementsHikariDataSource
    //  To set init statements
    // dataSource.connectionInitStatements = dataSourceConnectionInitStatements
    // dataSource = ComboPooledDataSource
}

class Listener implements InvocationListener {

    void beforeInvocation(Class callingClass, Object proxy, Method method, Object[] args) {
        println "Before invocation " + proxy.getClass().getName() + "::" + method.getName()
    }

    void afterInvocation(Class callingClass, Object proxy, Method method, Object[] args, Object returnValue) {
        println "After invocation " + proxy.getClass().getName() + "::" + method.getName();
    }

    void onException(Class callingClass, Object proxy, Method method, Object[] args, Throwable ex) {

    }
}

atom("persist")
        .settings([
                unitName: 'sample',
                txTimout: entityManagerTxTimeout,
                monitorTransaction: entityManagerMonitorTransaction,
                longTransaction: entityManagerLongTransaction,
                providerName: 'default',
                dataSourceName: 'dataSource',
                beanName: 'entityManagerProvider'])
        .from("$__dir/persist_prototype")
        .build() {
            postInit {
                // testing if EntityManager can be created correctly
                open({ resources ->
                    EntityManager em = resources.getInstance(EntityManager)
                    resources.abort()
                })
            }
        }

/*
atom("persist") {

    configuration = """
        entityManagerProvider.persistenceUnitName: sample
        entityManagerProvider.entityManagerListener: ^_emListener
        entityManagerProvider.queryListener: ^_queryListener
        entityManagerProvider.transactionTimeout: ${entityManagerTxTimeout}
        entityManagerProvider.monitorTransaction: ${entityManagerMonitorTransaction}
        entityManagerProvider.longTransaction: ${entityManagerLongTransaction}
        entityManagerProvider.persistenceProperties:
            javax.persistence.nonJtaDataSource: ^dataSource
        _tableId:
            defaultIncrementSize: 20
            defaultInitialValue: ^ 10 + _tableId.defaultIncrementSize
        _tableId2:
            defaultIncrementSize: 30
    """

    _emListener = Listener
    _queryListener = Listener

    entityManagerProvider = HibernateEntityManagerProvider

    _tableId = TableIdGenerator
    _tableId2 = TableIdGenerator

    entityManagerProvider.register('tableId', _tableId)
            .register('tableId2', _tableId2)

    postInit {
        // testing if EntityManager can be created correctly
        open({ resources ->
            EntityManager em = resources.getInstance(EntityManager)
            resources.abort()
        })
    }
}

 */


