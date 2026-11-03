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
package org.grails.orm.hibernate.query

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import jakarta.persistence.criteria.AbstractQuery
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import org.hibernate.query.criteria.HibernateCriteriaBuilder

import org.springframework.core.convert.ConversionService

import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

/**
 * Converts Grails Datastore query criteria into JPA {@link Predicate} instances.
 * Dispatches criteria to dedicated {@link InternalCriterionHandler} implementations
 * while supporting custom criterion extension via {@link CriterionHandler}.
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class PredicateGenerator {

    /**
     * Extension point for user-defined criterion handlers. Register a handler for a custom
     * {@link Query.Criterion} subclass to have it converted to a JPA {@link Predicate} during
     * query execution. Registered handlers are checked first, before any built-in criterion
     * handling, so a handler can also override built-in behavior.
     *
     * <p>Example registration (e.g., in {@code BootStrap.groovy}):
     * <pre>
     *     PredicateGenerator.registerCriterionHandler(MyCustomCriterion) { query, root, cb, criterion -&gt;
     *         def c = criterion as MyCustomCriterion
     *         cb.like(cb.cast(root.get(c.property), String), c.value)
     *     }
     * </pre>
     *
     * @param <C> the specific criterion type
     */
    @FunctionalInterface
    static interface CriterionHandler {

        Predicate handle(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            HibernateCriteriaBuilder criteriaBuilder,
            Query.Criterion criterion
        )

        /**
         * Converts a criterion with the context of the query it belongs to. The context resolves a property path
         * the way the built-in criteria do: {@link QueryPathContext#getFullyQualifiedPath(String)} turns
         * {@code alias.property}, for an alias created with {@code createAlias}, or {@code association.property}
         * into a path on the join GORM created for that association, so a custom criterion and a GORM criterion on
         * the same association restrict the same row instead of each joining the association again. This default
         * ignores the context and calls {@link #handle(AbstractQuery, From, HibernateCriteriaBuilder, Query.Criterion)};
         * a handler that needs the context implements {@link ContextualCriterionHandler}.
         *
         * @param criteriaQuery the query or subquery the predicate is for
         * @param root the root of that query
         * @param criteriaBuilder the criteria builder
         * @param criterion the criterion to convert
         * @param context the context of the query: its root, its joins and its aliases
         * @return the predicate, or {@code null} to add none
         * @since 8.1
         */
        default Predicate handle(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            HibernateCriteriaBuilder criteriaBuilder,
            Query.Criterion criterion,
            QueryPathContext context
        ) {
            handle(criteriaQuery, root, criteriaBuilder, criterion)
        }
    }

    /**
     * A {@link CriterionHandler} that is given the context of the query, through which it resolves property paths
     * with {@link QueryPathContext#getFullyQualifiedPath(String)}. A closure with five parameters registered with
     * {@link #registerCriterionHandler(Class, Closure)} becomes one, with no {@code as}:
     * <pre>
     *     PredicateGenerator.registerCriterionHandler(MyCriterion) { query, root, cb, criterion, context -&gt;
     *         def c = criterion as MyCriterion
     *         cb.like(context.getFullyQualifiedPath(c.property) as Expression&lt;String&gt;, c.value as String)
     *     }
     * </pre>
     *
     * @since 8.1
     */
    @FunctionalInterface
    static interface ContextualCriterionHandler extends CriterionHandler {

        @Override
        Predicate handle(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            HibernateCriteriaBuilder criteriaBuilder,
            Query.Criterion criterion,
            QueryPathContext context
        )

        /**
         * Called by code that has no context to give; the handler then receives {@code null} for it.
         */
        @Override
        default Predicate handle(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            HibernateCriteriaBuilder criteriaBuilder,
            Query.Criterion criterion
        ) {
            handle(criteriaQuery, root, criteriaBuilder, criterion, null)
        }
    }

    /**
     * Extension point for ordering a criteria query by something other than a property. Register a handler for a
     * {@link Query.Order} subclass to have it converted to a JPA {@link jakarta.persistence.criteria.Order}, in
     * the place the order has among the query's orders. The order reaches the query through
     * {@code order(Query.Order)} of the criteria builder or of a detached criteria.
     *
     * <pre>
     *     class ByRandom extends Query.Order {
     *         ByRandom() { super('random') }
     *     }
     *     PredicateGenerator.registerOrderHandler(ByRandom) { query, root, cb, order, context -&gt;
     *         cb.asc(cb.function('random', Double))
     *     }
     * </pre>
     *
     * @since 8.1
     */
    @FunctionalInterface
    static interface OrderHandler {

        /**
         * @param criteriaQuery the query, whose result type tells a query for the entity from a projection
         * @param root the root of the query
         * @param criteriaBuilder the criteria builder
         * @param order the order to convert
         * @param context the context of the query: its root, its joins and its aliases
         * @return the JPA order, or {@code null} to leave this order out of the query
         */
        jakarta.persistence.criteria.Order handle(
            CriteriaQuery<?> criteriaQuery,
            From<?, ?> root,
            HibernateCriteriaBuilder criteriaBuilder,
            Query.Order order,
            QueryPathContext context
        )
    }

    /**
     * SPI for contributing an {@link OrderHandler} via {@link ServiceLoader}. Register an implementation in
     * {@code META-INF/services/org.grails.orm.hibernate.query.PredicateGenerator$OrderHandlerProvider}; it is
     * discovered on first query execution, like a {@link CriterionHandlerProvider}.
     *
     * @since 8.1
     */
    static interface OrderHandlerProvider {

        Class<? extends Query.Order> orderType()

        OrderHandler orderHandler()
    }

    /**
     * SPI for contributing a {@link CriterionHandler} via {@link ServiceLoader}.
     *
     * <p>Create an implementation and register it in
     * {@code META-INF/services/org.grails.orm.hibernate.query.PredicateGenerator$CriterionHandlerProvider}
     * so that it is discovered automatically on first query execution — no BootStrap registration needed.
     *
     * <pre>
     *     // Example implementation (Groovy)
     *     class MyHandlerProvider implements PredicateGenerator.CriterionHandlerProvider {
     *         Class&lt;? extends Query.Criterion&gt; criterionType() { MyCriterion }
     *         PredicateGenerator.CriterionHandler criterionHandler() {
     *             { query, root, cb, criterion -&gt; ... } as PredicateGenerator.CriterionHandler
     *         }
     *     }
     * </pre>
     */
    static interface CriterionHandlerProvider {

        Class<? extends Query.Criterion> criterionType()

        CriterionHandler criterionHandler()

    }

    public static final Map<Class<? extends Query.Criterion>, CriterionHandler> CUSTOM_HANDLERS =
        new ConcurrentHashMap<>()

    private static final Map<Class<? extends Query.QueryElement>, InternalCriterionHandler<?>> BUILTIN_HANDLERS =
        new ConcurrentHashMap<>()

    static {
        registerBuiltinHandlers()
    }

    private static void registerBuiltinHandlers() {
        registerHandler(Query.IdEquals, { CriterionContext context, Query.IdEquals idEquals ->
            String propertyName = context.entity.identity.name
            Expression<?> propertyPath = context.resolvePath(propertyName)
            return context.criteriaBuilder.equal(propertyPath, context.convertValue(propertyName, idEquals.value, propertyPath))
        } as InternalCriterionHandler<Query.IdEquals>)
        registerHandler(SqlRestriction, new SqlRestrictionCriterionHandler())
        registerHandler(Query.DistinctProjection, { CriterionContext context, Query.DistinctProjection criterion ->
            context.criteriaBuilder.conjunction()
        } as InternalCriterionHandler<Query.DistinctProjection>)
        registerHandler(HibernateAlias, { CriterionContext context, HibernateAlias criterion -> null } as InternalCriterionHandler<HibernateAlias>)
        registerHandler(DetachedAssociationCriteria, { CriterionContext context, DetachedAssociationCriteria criterion ->
            AssociationCriterionHandler.handleDetachedAssociationCriteria(context, criterion)
        } as InternalCriterionHandler<DetachedAssociationCriteria>)
        registerHandler(HibernateAssociationQuery, { CriterionContext context, HibernateAssociationQuery criterion ->
            AssociationCriterionHandler.handleHibernateAssociationQuery(context, criterion)
        } as InternalCriterionHandler<HibernateAssociationQuery>)
        registerHandler(FunctionCallingCriterion, new FunctionCallingCriterionHandler())
        registerHandler(Query.Exists, new ExistsCriterionHandler())
        registerHandler(Query.NotExists, new NotExistsCriterionHandler())
        for (Class<? extends Query.QueryElement> type : [Query.Junction, Query.Conjunction, Query.Disjunction, Query.Negation]) {
            registerHandler(type, new JunctionCriterionHandler())
        }
        for (Class<? extends Query.QueryElement> type : [
                Query.SubqueryCriterion, Query.EqualsAll, Query.NotEqualsAll, Query.GreaterThanAll,
                Query.GreaterThanSome, Query.GreaterThanEqualsAll, Query.GreaterThanEqualsSome,
                Query.LessThanAll, Query.LessThanSome, Query.LessThanEqualsAll, Query.LessThanEqualsSome,
                Query.NotIn]) {
            registerHandler(type, new SubqueryCriterionHandler())
        }
        for (Class<? extends Query.QueryElement> type : [
                Query.PropertyNameCriterion, Query.IsNull, Query.IsNotNull, Query.IsEmpty, Query.IsNotEmpty]) {
            registerHandler(type, new PropertyNameCriterionHandler())
        }
        for (Class<? extends Query.QueryElement> type : [
                Query.PropertyComparisonCriterion, Query.EqualsProperty, Query.NotEqualsProperty,
                Query.GreaterThanProperty, Query.GreaterThanEqualsProperty, Query.LessThanProperty,
                Query.LessThanEqualsProperty]) {
            registerHandler(type, new PropertyComparisonCriterionHandler())
        }
        for (Class<? extends Query.QueryElement> type : [
                Query.PropertyCriterion, Query.Equals, Query.NotEquals, Query.Like, Query.ILike, Query.RLike,
                Query.GreaterThan, Query.GreaterThanEquals, Query.LessThan, Query.LessThanEquals, Query.Between,
                Query.In, Query.SizeEquals, Query.SizeNotEquals, Query.SizeGreaterThan,
                Query.SizeGreaterThanEquals, Query.SizeLessThan, Query.SizeLessThanEquals, EqualsIgnoreCase]) {
            registerHandler(type, new PropertyCriterionHandler())
        }
    }

    private static void registerHandler(Class<? extends Query.QueryElement> type, InternalCriterionHandler<?> handler) {
        BUILTIN_HANDLERS.put(type, handler)
    }

    @SuppressWarnings('unchecked')
    private static InternalCriterionHandler<Query.QueryElement> findHandler(Class<?> criterionClass) {
        InternalCriterionHandler<?> handler = BUILTIN_HANDLERS.get(criterionClass)
        if (handler != null) {
            return (InternalCriterionHandler<Query.QueryElement>) handler
        }

        for (Class<?> current = criterionClass.superclass; current != null && Query.QueryElement.isAssignableFrom(current); current = current.superclass) {
            InternalCriterionHandler<?> matched = BUILTIN_HANDLERS.get(current)
            if (matched != null) {
                return (InternalCriterionHandler<Query.QueryElement>) matched
            }
        }
        return null
    }

    private static final Map<Class<? extends Query.Order>, OrderHandler> CUSTOM_ORDER_HANDLERS =
        new ConcurrentHashMap<>()

    private static final Object SERVICE_LOADER_LOCK = new Object()

    private static volatile boolean serviceLoadersInitialized = false

    /**
     * Loads the handler providers once. A caller that arrives while another thread is loading them waits until the
     * loading is complete, so no query decides on a handler before the discovered ones are registered. Discovery uses
     * {@code putIfAbsent}, so a discovered handler does not replace one that is already registered; registering in
     * code uses {@code put}, so a handler registered in code replaces a discovered one.
     */
    private static void loadServiceProviders() {
        if (serviceLoadersInitialized) {
            return
        }
        synchronized (SERVICE_LOADER_LOCK) {
            if (serviceLoadersInitialized) {
                return
            }
            ClassLoader classLoader = Thread.currentThread().contextClassLoader
            for (CriterionHandlerProvider p : ServiceLoader.load(CriterionHandlerProvider, classLoader)) {
                CUSTOM_HANDLERS.putIfAbsent(p.criterionType(), p.criterionHandler())
            }
            for (OrderHandlerProvider p : ServiceLoader.load(OrderHandlerProvider, classLoader)) {
                CUSTOM_ORDER_HANDLERS.putIfAbsent(p.orderType(), p.orderHandler())
            }
            serviceLoadersInitialized = true
        }
    }

    /**
     * Registers an {@link OrderHandler} for the given order type. The handler converts every order of exactly
     * that type; programmatic registration takes precedence over a {@link ServiceLoader}-discovered handler.
     *
     * @param type the exact order class to handle
     * @param handler the handler that converts the order to a JPA order
     * @since 8.1
     */
    static void registerOrderHandler(Class<? extends Query.Order> type, OrderHandler handler) {
        CUSTOM_ORDER_HANDLERS.put(type, handler)
    }

    /**
     * Returns the handler registered for an order type, loading the {@link ServiceLoader} providers first.
     *
     * @param type the exact order class
     * @return the handler, or {@code null} when the type is ordered by its property as usual
     * @since 8.1
     */
    static OrderHandler orderHandler(Class<? extends Query.Order> type) {
        loadServiceProviders()
        CUSTOM_ORDER_HANDLERS.get(type)
    }

    /**
     * Removes all registered custom order handlers and resets the ServiceLoader flag. Useful for test cleanup.
     *
     * @since 8.1
     */
    static void clearCustomOrderHandlers() {
        synchronized (SERVICE_LOADER_LOCK) {
            CUSTOM_ORDER_HANDLERS.clear()
            serviceLoadersInitialized = false
        }
    }

    /**
     * Registers a {@link CriterionHandler} for the given criterion type. The handler is called
     * before any built-in criterion logic when a criterion of exactly that type is encountered.
     * Programmatic registration takes precedence over {@link ServiceLoader}-discovered handlers.
     *
     * @param type    the exact criterion class to handle
     * @param handler the handler that converts the criterion to a JPA {@link Predicate}
     */
    static void registerCriterionHandler(Class<? extends Query.Criterion> type, CriterionHandler handler) {
        CUSTOM_HANDLERS.put(type, handler)
    }

    /**
     * Registers a closure as the {@link CriterionHandler} for the given criterion type. A closure with five
     * parameters, {@code query, root, criteriaBuilder, criterion, context}, becomes a
     * {@link ContextualCriterionHandler}; any other closure becomes a {@link CriterionHandler} and is called with
     * four arguments. This is what makes the two shapes work without {@code as}, as {@link #registerOrderHandler} does
     * for an order handler.
     *
     * @param type    the exact criterion class to handle
     * @param handler the closure that converts the criterion to a JPA {@link Predicate}
     * @since 8.1
     */
    static void registerCriterionHandler(Class<? extends Query.Criterion> type, Closure<?> handler) {
        if (handler.maximumNumberOfParameters == 5) {
            registerCriterionHandler(type, (ContextualCriterionHandler) { AbstractQuery<?> query, From<?, ?> root, HibernateCriteriaBuilder cb, Query.Criterion criterion, QueryPathContext context ->
                (Predicate) handler.call(query, root, cb, criterion, context)
            })
        } else {
            registerCriterionHandler(type, (CriterionHandler) { AbstractQuery<?> query, From<?, ?> root, HibernateCriteriaBuilder cb, Query.Criterion criterion ->
                (Predicate) handler.call(query, root, cb, criterion)
            })
        }
    }

    /**
     * Removes all registered custom criterion handlers and resets the ServiceLoader flag.
     * Useful for test cleanup.
     */
    static void clearCustomCriterionHandlers() {
        synchronized (SERVICE_LOADER_LOCK) {
            CUSTOM_HANDLERS.clear()
            serviceLoadersInitialized = false
        }
    }

    private final HibernateCriteriaBuilder criteriaBuilder
    private final ConversionService conversionService

    PredicateGenerator(HibernateCriteriaBuilder criteriaBuilder, ConversionService conversionService) {
        this.criteriaBuilder = criteriaBuilder
        this.conversionService = conversionService
    }

    Predicate[] getPredicates(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            List<? extends Query.QueryElement> criteria,
            JpaQueryContext fromsByProvider,
            GrailsHibernatePersistentEntity entity) {
        List<Predicate> result = []
        for (Query.QueryElement c : criteria) {
            Predicate p = handleCriterion(criteriaQuery, root, fromsByProvider, entity, c)
            if (p != null) {
                result.add(p)
            }
        }
        return result.toArray(new Predicate[0])
    }

    Predicate handleCriterion(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            JpaQueryContext fromsByProvider,
            GrailsHibernatePersistentEntity entity,
            Query.QueryElement criterion) {

        loadServiceProviders()

        if (criterion instanceof Query.Criterion) {
            Query.Criterion c = (Query.Criterion) criterion
            CriterionHandler customHandler = CUSTOM_HANDLERS.get(c.class)
            if (customHandler != null) {
                return customHandler.handle(criteriaQuery, root, criteriaBuilder, c, fromsByProvider)
            }
        }

        InternalCriterionHandler<Query.QueryElement> handler = findHandler(criterion.getClass())
        if (handler != null) {
            CriterionContext context = new CriterionContext(
                    criteriaQuery, root, fromsByProvider, entity, criteriaBuilder, conversionService, this)
            return handler.handle(context, criterion)
        }

        throw new IllegalArgumentException("Unsupported criterion: ${criterion}".toString())
    }

    Predicate generate(
            AbstractQuery<?> cq, From<?, ?> root, List<Query.Criterion> criteriaList, JpaQueryContext tablesByName, GrailsHibernatePersistentEntity entity) {
        Predicate[] predicates = getPredicates(cq, root, criteriaList, tablesByName, entity)
        return criteriaBuilder.and(predicates)
    }

    /**
     * Returns a column of the entity's own table, whose table alias replaces {@code {alias}} in native SQL:
     * its identifier column, or the first one of a composite identifier. An identifier that is an association
     * stands for its foreign key columns, which are resolved the same way from the associated entity.
     */
    private static Expression<?> aliasColumn(From<?, ?> root, GrailsHibernatePersistentEntity entity) {
        Path<?> path = root
        PersistentEntity current = entity
        while (true) {
            PersistentProperty<?> identity = firstIdentifierProperty(current)
            if (identity == null) {
                throw new ConfigurationException('Cannot use {alias} in native SQL on class [' + entity.javaClass.name + '] without an identifier')
            }
            path = path.get(identity.name)
            if (!(identity instanceof Association) || ((Association<?>) identity).associatedEntity == null) {
                return path
            }
            current = ((Association<?>) identity).associatedEntity
        }
    }

    private static PersistentProperty<?> firstIdentifierProperty(PersistentEntity entity) {
        if (entity.identity != null) {
            return entity.identity
        }
        PersistentProperty<?>[] compositeIdentity = entity.compositeIdentity
        return compositeIdentity == null || compositeIdentity.length == 0 ? null : compositeIdentity[0]
    }

    /**
     * Returns the first arguments of a function that renders native SQL: the SQL as a literal and, if it contains
     * {@code {alias}}, a column of the entity's own table whose table alias replaces it.
     */
    @PackageScope static List<Expression<?>> nativeSqlArguments(
            CriteriaBuilder criteriaBuilder, String sql, From<?, ?> root, GrailsHibernatePersistentEntity entity) {
        List<Expression<?>> arguments = []
        arguments.add(criteriaBuilder.literal(sql))
        if (sql.contains(GrailsSqlRestrictionFunction.ALIAS_PLACEHOLDER)) {
            arguments.add(aliasColumn(root, entity))
        }
        return arguments
    }
}
