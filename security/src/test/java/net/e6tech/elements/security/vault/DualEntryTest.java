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

package net.e6tech.elements.security.vault;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

public class DualEntryTest {

    @Test
    void basic() {
        DualEntry de = new DualEntry("user1", "password1".toCharArray());
        List<String> questions = new ArrayList<>();
        questions.add("What is the name of your first pet? ");
        questions.add("What city did you meet your wife? ");
        List<String> answers = de.run("This is a test", 1099, questions);
        System.out.println(answers);
    }

    @Test
    void basic2() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(1099)) {
            serverSocket.setReuseAddress(true);
            DualEntry de = new DualEntry();
            List<String> questions = new ArrayList<>();
            questions.add("What is the name of your first pet? ");
            questions.add("What city did you meet your wife? ");
            List<String> answers = de.run("This is a test", serverSocket, questions);
            System.out.println(answers);
        }
    }
}
