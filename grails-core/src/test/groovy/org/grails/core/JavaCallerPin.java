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
package org.grails.core;

import grails.core.ApplicationAttributes;
import grails.core.DefaultGrailsApplication;
import grails.core.GrailsApplication;
import grails.util.CacheEntry;
import grails.util.GrailsClassUtils;
import grails.util.Holders;
import org.apache.grails.core.aot.GeneratedBeanDefinitions;
import org.grails.transaction.ChainedTransactionManager;

/**
 * Compiled together with the Groovy sources, so the Java-facing API is pinned at compile time: a constant that stops
 * being a field, or a package-private one that becomes a public property, fails the build of this class, and so does a
 * public constructor that disappears.
 */
final class JavaCallerPin {

    static final String APP_CTX = ApplicationAttributes.APPLICATION_CONTEXT;
    static final String APP_ID = GrailsApplication.APPLICATION_ID;
    static final String DECLARED_TYPE = GeneratedBeanDefinitions.DECLARED_TYPE_ATTRIBUTE;

    private JavaCallerPin() {
    }

    static GrailsApplication newApplication() {
        return new DefaultGrailsApplication();
    }

    static Object holdersContext() {
        return Holders.getApplicationContext();
    }

    static boolean isAssignable(Class<?> target, Class<?> source) {
        return GrailsClassUtils.isAssignableOrConvertibleFrom(target, source);
    }

    static <T> CacheEntry<T> newCacheEntry(T val) {
        return new CacheEntry<>(val);
    }

    static ChainedTransactionManager newTxManager() {
        return new ChainedTransactionManager();
    }
}
