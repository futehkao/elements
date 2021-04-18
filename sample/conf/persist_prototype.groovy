/*
 * Copyright 2015-2020 Futeh Kao
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

import net.e6tech.elements.persist.InvocationListener
import net.e6tech.elements.persist.hibernate.HibernateEntityManagerProvider
import net.e6tech.elements.persist.hibernate.TableIdGenerator

import java.lang.reflect.Method

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

// demonstrate how to use complicated prototype
prototype("persist")
        .settings([
                unitName          : 'sample',
                txTimeout         : entityManagerTxTimeout,
                monitorTransaction: entityManagerMonitorTransaction,
                longTransaction   : entityManagerLongTransaction,
                dataSourceName    : 'dataSource',
                providerName      : 'default',
                beanName          : 'entityManagerProvider'])
        .build {
            configuration = """
        _entityManagerProvider.persistenceUnitName: ${unitName}
        _entityManagerProvider.entityManagerListener: ^_emListener
        _entityManagerProvider.queryListener: ^_queryListener
        _entityManagerProvider.transactionTimeout: ${txTimeout}
        _entityManagerProvider.monitorTransaction: ${monitorTransaction}
        _entityManagerProvider.longTransaction: ${longTransaction}
        _entityManagerProvider.persistenceProperties:
            javax.persistence.nonJtaDataSource: ^${dataSourceName}
        _tableId:
            defaultIncrementSize: 20
            defaultInitialValue: ^ 10 + _tableId.defaultIncrementSize
        _tableId2:
            defaultIncrementSize: 30
    """

            _emListener = Listener
            _queryListener = Listener

            _entityManagerProvider = HibernateEntityManagerProvider
            _tableId = TableIdGenerator
            _tableId2 = TableIdGenerator

            _entityManagerProvider.register('tableId', _tableId)
                    .register('tableId2', _tableId2)

            def close = { em ->
                if (em.get('lockTimeout') != null) {
                    em.lockTimeout(em.get('lockTimeout'))
                    em.remove('lockTimeout')
                }
            }
            _entityManagerProvider.extensions = [
                    setLockTimeout: { em, millis ->
                        if (em.get('lockTimeout') == null) {
                            em.put('lockTimeout', em.lockTimeout())
                        }

                        int second = millis / 1000
                        em.createNativeQuery("SET SESSION innodb_lock_wait_timeout = :v")
                                .setParameter('v', second)
                                .executeUpdate()
                        return null
                    },
                    getLockTimeout: { em ->
                        return em.createNativeQuery("select @@innodb_lock_wait_timeout")
                                .getSingleResult()
                                .longValue() * 1000
                    },
                    onCommit: close,
                    onAbort: close
            ]

            resourceManager.registerBean(beanName, _entityManagerProvider)
        }
