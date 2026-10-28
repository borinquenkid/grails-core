/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.build.logging;

import java.io.IOException;
import java.util.Stack;

import org.grails.build.interactive.CandidateListCompletionHandler;
import org.grails.build.logging.ConsoleAnsi;
import org.grails.build.logging.GrailsConsoleAntBuilder;
import org.grails.build.logging.GrailsConsoleBuildListener;
import org.grails.build.logging.GrailsConsoleErrorPrintStream;
import org.grails.build.logging.GrailsConsolePrintStream;

/**
 * Compiled together with the Groovy sources, so the Java-facing API is pinned at compile time: a constant that stops
 * being a compile-time constant field, a package-private field that becomes a property, a public constructor that
 * disappears and a checked exception that is no longer declared each fail the build of this class.
 * <p>
 * A one-character String constant is not a compile-time constant in Groovy bytecode (Groovy emits no ConstantValue
 * attribute for it), so those constants are pinned as fields only, not as case labels.
 */
final class JavaCallerPin {

    static final String ENABLE_TERMINAL = GrailsConsole.ENABLE_TERMINAL;

    static final String SUSPEND_SYSTEM_OUT_REDIRECT = GrailsConsole.SUSPEND_SYSTEM_OUT_REDIRECT;

    static final String ENABLE_INTERACTIVE = GrailsConsole.ENABLE_INTERACTIVE;

    static final String LINE_SEPARATOR = GrailsConsole.LINE_SEPARATOR;

    static final String CATEGORY_SEPARATOR = GrailsConsole.CATEGORY_SEPARATOR;

    static final String PROMPT = GrailsConsole.PROMPT;

    static final String SPACE = GrailsConsole.SPACE;

    static final String ERROR = GrailsConsole.ERROR;

    static final String WARNING = GrailsConsole.WARNING;

    static final String HISTORYFILE = GrailsConsole.HISTORYFILE;

    static final String STACKTRACE_FILTERED_MESSAGE = GrailsConsole.STACKTRACE_FILTERED_MESSAGE;

    static final String STACKTRACE_MESSAGE = GrailsConsole.STACKTRACE_MESSAGE;

    static final Character SECURE_MASK_CHAR = GrailsConsole.SECURE_MASK_CHAR;

    private JavaCallerPin() {
    }

    static int constants(String value, int code) {
        switch (value) {
            case GrailsConsole.ENABLE_TERMINAL:
            case GrailsConsole.PROMPT:
            case GrailsConsole.HISTORYFILE:
                break;
            default:
                return -1;
        }
        switch (code) {
            case ConsoleAnsi.BOLD:
            case ConsoleAnsi.BOLD_OFF:
            case ConsoleAnsi.FG_RED:
            case ConsoleAnsi.FG_GREEN:
            case ConsoleAnsi.FG_YELLOW:
            case ConsoleAnsi.FG_CYAN:
            case ConsoleAnsi.FG_DEFAULT:
                return code;
            default:
                return 0;
        }
    }

    static Stack<String> category(GrailsConsole console) {
        return console.category;
    }

    static GrailsConsole console() {
        try {
            return GrailsConsole.createInstance();
        } catch (IOException e) {
            return null;
        }
    }

    static GrailsConsole eclipseConsole() {
        try {
            return new GrailsEclipseConsole();
        } catch (IOException e) {
            return null;
        }
    }

    static Object[] constructors() {
        return new Object[] {
            new CandidateListCompletionHandler(), new CandidateListCompletionHandler(null),
            new GrailsConsoleAntBuilder(), new GrailsConsoleAntBuilder(null),
            new GrailsConsoleBuildListener(), new GrailsConsoleBuildListener(null),
            new GrailsConsoleErrorPrintStream(System.err), new GrailsConsolePrintStream(System.out)
        };
    }

}
