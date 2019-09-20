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

import net.e6tech.elements.persist.EntityManagerProviderProxy


atom("persist3") {

    configuration = """
        entityManagerProvider3.persistenceUnitName: sample
        entityManagerProvider3.providerName: delegate
        entityManagerProvider3.transactionTimeout: ${entityManagerTxTimeout}
        entityManagerProvider3.monitorTransaction: ${entityManagerMonitorTransaction}
        entityManagerProvider3.longTransaction: ${entityManagerLongTransaction}
        entityManagerProvider3.delegateName: entityManagerProvider
        entityManagerProvider3.persistenceProperties:
            javax.persistence.nonJtaDataSource: ^dataSource3
        _tableId:
            defaultIncrementSize: 20
            defaultInitialValue: ^ 10 + _tableId.defaultIncrementSize
        _tableId2:
            defaultIncrementSize: 30
    """

    entityManagerProvider3 = EntityManagerProviderProxy
}
