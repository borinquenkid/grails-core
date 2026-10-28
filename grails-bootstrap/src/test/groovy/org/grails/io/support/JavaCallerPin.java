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
package org.grails.io.support;

import java.io.IOException;
import java.util.LinkedHashMap;

import org.apache.commons.logging.Log;

import grails.plugins.GrailsPluginInfo;
import grails.plugins.InvalidVersionException;
import grails.plugins.exceptions.PluginException;
import org.grails.exceptions.reporting.DefaultStackTraceFilterer;
import org.grails.exceptions.reporting.StackTraceFilterer;
import org.grails.io.watch.DirectoryWatcher;

/**
 * Compiled together with the Groovy sources, so the Java-facing API is pinned at compile time: a constant that stops
 * being a compile-time constant field, a package-private field or constructor that becomes a public property or
 * disappears, a public constructor that disappears and a checked exception that is no longer declared each fail the
 * build of this class.
 * <p>
 * A one-character String constant is not a compile-time constant in Groovy bytecode (Groovy emits no ConstantValue
 * attribute for it), so those constants are pinned as fields only, not as case labels.
 */
final class JavaCallerPin {

    static final String TRAILING_NAME = GrailsPluginInfo.TRAILING_NAME;

    static final String NAME = GrailsPluginInfo.NAME;

    static final String VERSION = GrailsPluginInfo.VERSION;

    static final String STACK_LOG_NAME = DefaultStackTraceFilterer.STACK_LOG_NAME;

    static final Log STACK_LOG = DefaultStackTraceFilterer.STACK_LOG;

    static final String FULL_STACK_TRACE_MESSAGE = StackTraceFilterer.FULL_STACK_TRACE_MESSAGE;

    static final String SYS_PROP_DISPLAY_FULL_STACKTRACE = StackTraceFilterer.SYS_PROP_DISPLAY_FULL_STACKTRACE;

    static final String DEFAULT_PATH_SEPARATOR = AntPathMatcher.DEFAULT_PATH_SEPARATOR;

    static final String WILDCARD = ResourceLocator.WILDCARD;

    static final String FILE_SEPARATOR = ResourceLocator.FILE_SEPARATOR;

    static final String CLOSURE_MARKER = ResourceLocator.CLOSURE_MARKER;

    static final String WEB_APP_DIR = ResourceLocator.WEB_APP_DIR;

    static final Resource NULL_RESOURCE = ResourceLocator.NULL_RESOURCE;

    static final String SVN_DIR_NAME = DirectoryWatcher.SVN_DIR_NAME;

    private JavaCallerPin() {
    }

    static String constants(String value) {
        switch (value) {
            case GrailsPluginInfo.TRAILING_NAME:
            case StackTraceFilterer.FULL_STACK_TRACE_MESSAGE:
            case ResourceLocator.WEB_APP_DIR:
            case DirectoryWatcher.SVN_DIR_NAME:
                return value;
            default:
                return null;
        }
    }

    static ResourceLocator.PluginResourceInfo pluginResourceInfo() {
        ResourceLocator.PluginResourceInfo info = new ResourceLocator().new PluginResourceInfo();
        info.pluginName = "name";
        info.uri = "/uri";
        return info;
    }

    static AntPathMatcher.AntPathStringMatcher stringMatcher() {
        return new AntPathMatcher.AntPathStringMatcher("*", "a", new LinkedHashMap<>());
    }

    static Resource[] resources(PathMatchingResourcePatternResolver resolver) {
        try {
            return resolver.getResources("classpath:*");
        } catch (IOException e) {
            return new Resource[0];
        }
    }

    static Object[] constructors() {
        return new Object[] {
            new InvalidVersionException(), new InvalidVersionException("message"),
            new InvalidVersionException("message", new RuntimeException()), new InvalidVersionException(new RuntimeException()),
            new PluginException(), new PluginException("message"),
            new PluginException("message", new RuntimeException()), new PluginException(new RuntimeException()),
            new DefaultStackTraceFilterer(), new DefaultStackTraceFilterer(true),
            new AntPathMatcher(), new FileSystemResourceLoader(),
            new PathMatchingResourcePatternResolver(), new PathMatchingResourcePatternResolver(JavaCallerPin.class.getClassLoader()),
            new PathMatchingResourcePatternResolver(new FileSystemResourceLoader()),
            new ResourceLocator(), new StaticResourceLoader(ResourceLocator.NULL_RESOURCE), new DirectoryWatcher()
        };
    }

}
