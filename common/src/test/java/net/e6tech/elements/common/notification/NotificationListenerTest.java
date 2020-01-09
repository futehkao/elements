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

package net.e6tech.elements.common.notification;

import net.e6tech.elements.common.Tags;
import org.junit.jupiter.api.Test;


/**
 * Created by futeh.
 */
@Tags.Common
public class NotificationListenerTest {

    @Test
    @SuppressWarnings("unchecked")
    public void notificationClass() throws Exception {
        Class<? extends Notification>[] types = (new TestNotificationListener()).getNotificationTypes();
        for (Class cls : types) System.out.println(cls);
        types = (new TestNotificationListener2()).getNotificationTypes();
        for (Class cls : types) System.out.println(cls);
    }

    private static class TestNotificationListener implements NotificationListener<TestNotification> {
        @Override
        public void onEvent(TestNotification notification) {

        }
    }

    private static class TestNotificationListener2 implements NotificationListener {
        @Override
        public void onEvent(Notification notification) {

        }
    }

    private static class TestNotification implements Notification {

    }

    private static class TestNotification2 implements Notification {

    }

}
