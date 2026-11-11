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
package org.apache.grails.core.aot

import groovy.transform.CompileStatic
import org.jspecify.annotations.Nullable

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

/**
 * Registers the JDK types the framework's dynamically compiled code calls.
 *
 * <p>A plugin descriptor's closures, the bean DSL and much of the framework's older Groovy are compiled
 * without static compilation, so a call on a map, a string or a number in them is resolved through Groovy's
 * metaclass for whatever it is made on, and made reflectively -- and so is a {@code new ArrayList()}, and
 * so is reading a constant such as {@code File.separator}. An
 * image keeps a JDK method or constructor for that only when something asked for it, and Groovy's own
 * metadata covers the types Groovy owns rather than the JDK's. An image built without these stopped as the
 * plugins declared their beans, on a call nobody wrote on a type everybody uses.</p>
 *
 * <p>The types are the ones that code calls, which are few and stable: the collections and their entries,
 * strings, numbers and their wrappers, and the static helpers it reaches for. Where an interface declares
 * the method, the interface is named and its implementations are reached through it.</p>
 *
 * @since 9.0
 */
@CompileStatic
class JdkDispatchRuntimeHints implements RuntimeHintsRegistrar {

    /** What dynamically compiled code calls methods on and reads constants from. */
    private static final Class<?>[] CALLED = [
        Object,
        String,
        CharSequence,
        Comparable,
        Number,
        Boolean,
        Enum,
        Iterable,
        Iterator,
        Collection,
        List,
        Set,
        Map,
        Map.Entry,
        System,
        Arrays,
        Collections,
        Objects,
        File
    ] as Class<?>[]

    /** What it creates with {@code new}. */
    private static final Class<?>[] CREATED = [
        ArrayList,
        HashMap,
        LinkedHashMap,
        TreeMap,
        HashSet,
        LinkedHashSet,
        TreeSet,
        BigDecimal,
        BigInteger
    ] as Class<?>[]

    @Override
    void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (Class<?> type : CALLED) {
            hints.reflection().registerType(type, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.ACCESS_PUBLIC_FIELDS)
        }
        for (Class<?> type : CREATED) {
            hints.reflection().registerType(type, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS)
        }
    }

}
