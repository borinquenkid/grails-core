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
package org.grails.compiler.beans;

import org.codehaus.groovy.transform.ASTTransformation;

/**
 * Compiled together with the Groovy sources, so the Java-facing API is pinned at compile time: a constant that stops
 * being a field, or a package-private one that becomes a public property, fails the build of this class, and so does a
 * public constructor that disappears.
 */
final class JavaCallerPin {

    static final String IMPORTS_LOCATION = AutoConfigurationImportsWriter.IMPORTS_LOCATION;

    static final String SOURCE_IMPORTS_LOCATION = AutoConfigurationImportsWriter.SOURCE_IMPORTS_LOCATION;

    static final String RESOLVED_TARGET_DIRECTORY_METADATA =
            GrailsBeansASTTransformation.RESOLVED_TARGET_DIRECTORY_METADATA;

    static final String UNIT_TEST_TRAIT_NAME = GrailsBeansASTTransformation.UNIT_TEST_TRAIT_NAME;

    static final String SPOCK_SHARED_BEANS_FIELD = GrailsBeansASTTransformation.SPOCK_SHARED_BEANS_FIELD;

    private JavaCallerPin() {
    }

    static ASTTransformation transformation() {
        return new GrailsBeansASTTransformation();
    }

    static OnGrailsEnvCondition condition() {
        return new OnGrailsEnvCondition();
    }

}
