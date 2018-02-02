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

/**
 * Created by futeh.
 */
public class CommandException extends Exception {
    private static final long serialVersionUID = 1905636163513797994L;
    private final String errorType;
    private final int fieldNumber;
    private static final String REVISION = "00";

    public CommandException(int fieldNumber, Throwable th) {
        super(th);
        this.fieldNumber = fieldNumber;
        this.errorType = "08";
    }

    public CommandException(String errorType, int fieldNumber, Throwable th) {
        super(th);
        this.fieldNumber = fieldNumber;
        this.errorType = errorType;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getRevision() {
        return REVISION;
    }

    public String error() {
        return errorType + formatField() + REVISION;
    }

    public String formatField() {
        if (fieldNumber > 10)
            return Integer.toString(fieldNumber);
        else return "0" + fieldNumber;
    }
}
