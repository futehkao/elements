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

package net.e6tech.elements.security.hsm.atalla.simulator;

import java.util.concurrent.Callable;

/**
 * Created by futeh.
 */
public class CommandException extends Exception {
    private String errorType = "08";
    private int fieldNumber;
    private String revision = "00";


    public CommandException(int fieldNumber, Throwable th) {
        super(th);
        this.fieldNumber = fieldNumber;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    public String error() {
        return errorType + formatField() + revision;
    }

    public String formatField() {
        if (fieldNumber > 10) return "" + fieldNumber;
        else return "0" + fieldNumber;
    }
}
