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

import groovy.transform.CompileStatic
import jakarta.persistence.criteria.AbstractQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.ParameterExpression
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import org.hibernate.query.criteria.HibernateCriteriaBuilder

import org.springframework.core.convert.ConversionService

import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty

/**
 * Encapsulates the execution context for converting a {@link Query.QueryElement} into a JPA {@link Predicate}.
 * Holds the query, root, query context, persistent entity, criteria builder, conversion service,
 * and predicate generator reference, providing helper methods for property resolution and value conversion.
 *
 * @since 8.0.1
 */
@CompileStatic
class CriterionContext {

    private final AbstractQuery<?> criteriaQuery
    private final From<?, ?> root
    private final JpaQueryContext queryContext
    private final GrailsHibernatePersistentEntity entity
    private final HibernateCriteriaBuilder criteriaBuilder
    private final ConversionService conversionService
    private final PredicateGenerator generator

    CriterionContext(
            AbstractQuery<?> criteriaQuery,
            From<?, ?> root,
            JpaQueryContext queryContext,
            GrailsHibernatePersistentEntity entity,
            HibernateCriteriaBuilder criteriaBuilder,
            ConversionService conversionService,
            PredicateGenerator generator) {
        this.criteriaQuery = criteriaQuery
        this.root = root
        this.queryContext = queryContext
        this.entity = entity
        this.criteriaBuilder = criteriaBuilder
        this.conversionService = conversionService
        this.generator = generator
    }

    AbstractQuery<?> getCriteriaQuery() {
        return criteriaQuery
    }

    From<?, ?> getRoot() {
        return root
    }

    JpaQueryContext getQueryContext() {
        return queryContext
    }

    GrailsHibernatePersistentEntity getEntity() {
        return entity
    }

    HibernateCriteriaBuilder getCriteriaBuilder() {
        return criteriaBuilder
    }

    ConversionService getConversionService() {
        return conversionService
    }

    PredicateGenerator getGenerator() {
        return generator
    }

    CriterionContext withRoot(From<?, ?> newRoot, JpaQueryContext newQueryContext, GrailsHibernatePersistentEntity newEntity) {
        return new CriterionContext(criteriaQuery, newRoot, newQueryContext, newEntity, criteriaBuilder, conversionService, generator)
    }

    Expression<?> resolvePath(String propertyName) {
        return queryContext != null ? queryContext.getFullyQualifiedExpression(propertyName) : null
    }

    boolean isCollectionPath(Expression<?> expression) {
        if (expression instanceof Path) {
            return Collection.isAssignableFrom(((Path<?>) expression).javaType)
        }
        return false
    }

    @SuppressWarnings('unchecked')
    Expression<Collection<?>> resolveCollectionPath(Expression<?> expression) {
        if (expression instanceof Join) {
            Join<?, ?> join = (Join<?, ?>) expression
            if (join.parent != null && join.attribute != null) {
                return (Expression<Collection<?>>) (Expression<?>) join.parent.get(join.attribute.name)
            }
        }
        return (Expression<Collection<?>>) expression
    }

    Object convertValue(String propertyName, Object value, Expression<?> propertyPath) {
        if (value == null) {
            throw new ConfigurationException("Null value for property [${propertyName}] is not allowed in comparison criteria.".toString())
        }

        Class<?> targetType = propertyPath != null ? propertyPath.javaType : null

        if (targetType == null && entity != null) {
            HibernatePersistentProperty prop = (HibernatePersistentProperty) entity.getPropertyByName(propertyName)
            if (prop != null) {
                targetType = prop.type
            }
        }

        if (targetType != null && Collection.isAssignableFrom(targetType) && entity != null) {
            HibernatePersistentProperty prop = (HibernatePersistentProperty) entity.getPropertyByName(propertyName)
            if (prop instanceof HibernateToManyProperty) {
                targetType = ((HibernateToManyProperty) prop).componentType
            }
        }

        if (targetType != null && conversionService != null && conversionService.canConvert(value.class, targetType)) {
            if (!Collection.isAssignableFrom(targetType) || Collection.isAssignableFrom(value.class)) {
                return conversionService.convert(value, targetType)
            }
        }
        return value
    }

    /**
     * Returns an arithmetic expression for a {@link PropertyArithmetic}, otherwise the converted value itself.
     * A plain value is not wrapped in a literal, so the comparison overload that takes it infers its type from
     * the property path, as HQL does for a parameter. A literal is typed from the value's class instead, which a
     * property mapped with a custom {@code UserType} cannot be compared with when that class is
     * {@link java.io.Serializable}.
     */
    @SuppressWarnings('unchecked')
    Object convertComparisonValue(String propertyName, Object value, Expression<?> propertyPath) {
        if (value instanceof PropertyArithmetic) {
            PropertyArithmetic pa = (PropertyArithmetic) value
            Expression<Number> left = (Expression<Number>) queryContext.getFullyQualifiedExpression(pa.propertyName())
            Expression<Number> right = (Expression<Number>) criteriaBuilder.literal(pa.operand())

            switch (pa.operator()) {
                case PropertyArithmetic.Operator.MULTIPLY:
                    return criteriaBuilder.prod(left, right)
                case PropertyArithmetic.Operator.DIVIDE:
                    return criteriaBuilder.quot(left, right)
                case PropertyArithmetic.Operator.ADD:
                    return criteriaBuilder.sum(left, right)
                case PropertyArithmetic.Operator.SUBTRACT:
                    return criteriaBuilder.diff(left, right)
                default:
                    throw new IllegalStateException("Unexpected operator: ${pa.operator()}".toString())
            }
        }
        return convertValue(propertyName, value, propertyPath)
    }

    /**
     * Returns the value as an expression for a comparison overload that takes an expression, which the JPA ordering
     * overloads need for a value that is not {@link Comparable}. The value becomes a criteria parameter of the
     * property's Java type, bound once the query is created, so Hibernate types it from the property it is compared
     * with, as it does for an HQL parameter.
     */
    Expression<?> asExpression(Object value, Expression<?> propertyPath) {
        if (value instanceof Expression) {
            return (Expression<?>) value
        }
        ParameterExpression<?> parameter = criteriaBuilder.parameter(propertyPath.javaType)
        queryContext.bindParameter(parameter, value)
        return parameter
    }

    Predicate handle(Query.QueryElement criterion) {
        return generator.handleCriterion(criteriaQuery, root, queryContext, entity, criterion)
    }

    Predicate[] getPredicates(List<? extends Query.QueryElement> criteria) {
        return generator.getPredicates(criteriaQuery, root, criteria, queryContext, entity)
    }
}
