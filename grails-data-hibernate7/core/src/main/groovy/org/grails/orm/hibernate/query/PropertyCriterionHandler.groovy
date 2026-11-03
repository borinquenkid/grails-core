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
import groovy.transform.PackageScope
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Subquery
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.hibernate.query.criteria.JpaSubQuery

import grails.gorm.DetachedCriteria
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.query.Projections
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

/**
 * Handles criteria targeting a persistent property, such as equality, relational comparisons,
 * pattern matching, collection size, and IN/BETWEEN checks.
 *
 * @since 8.0.1
 */
@CompileStatic
class PropertyCriterionHandler implements InternalCriterionHandler<Query.PropertyCriterion> {

    @Override
    @SuppressWarnings(['unchecked', 'rawtypes'])
    Predicate handle(CriterionContext context, Query.PropertyCriterion pc) {
        String propertyName = pc.property
        Expression<?> propertyPath = context.resolvePath(propertyName)
        if (propertyPath == null) {
            String className = context.entity != null ? context.entity.javaClass.name : 'unknown'
            throw new ConfigurationException("Cannot use comparison criteria on non-existent property [${propertyName}] of class [${className}]".toString())
        }

        HibernateCriteriaBuilder criteriaBuilder = context.criteriaBuilder

        if (pc instanceof EqualsIgnoreCase) {
            return handleEqualsIgnoreCase(context, pc, propertyPath)
        } else if (pc instanceof Query.Equals) {
            return handleEquals(context, pc, propertyPath)
        } else if (pc instanceof Query.NotEquals) {
            return handleNotEquals(context, pc, propertyPath)
        } else if (pc instanceof Query.ILike) {
            return criteriaBuilder.ilike((Expression<String>) propertyPath, (String) context.convertValue(propertyName, pc.value, propertyPath))
        } else if (pc instanceof Query.RLike) {
            return handleRLike(criteriaBuilder, (Expression<String>) propertyPath, (Query.RLike) pc)
        } else if (pc instanceof Query.Like) {
            return criteriaBuilder.like((Expression<String>) propertyPath, (String) context.convertValue(propertyName, pc.value, propertyPath))
        } else if (pc instanceof Query.GreaterThan) {
            if (pc.value instanceof QueryableCriteria) {
                Subquery<?> subquery = buildSubquery(context, propertyPath, (QueryableCriteria<?>) pc.value, propertyName)
                return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression) subquery)
            }
            Object value = context.convertComparisonValue(propertyName, pc.value, propertyPath)
            return value instanceof Comparable ?
                criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Comparable) value) :
                criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression) context.asExpression(value, propertyPath))
        } else if (pc instanceof Query.GreaterThanEquals) {
            if (pc.value instanceof QueryableCriteria) {
                Subquery<?> subquery = buildSubquery(context, propertyPath, (QueryableCriteria<?>) pc.value, propertyName)
                return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) subquery)
            }
            Object value = context.convertComparisonValue(propertyName, pc.value, propertyPath)
            return value instanceof Comparable ?
                criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Comparable) value) :
                criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) context.asExpression(value, propertyPath))
        } else if (pc instanceof Query.LessThan) {
            if (pc.value instanceof QueryableCriteria) {
                Subquery<?> subquery = buildSubquery(context, propertyPath, (QueryableCriteria<?>) pc.value, propertyName)
                return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression) subquery)
            }
            Object value = context.convertComparisonValue(propertyName, pc.value, propertyPath)
            return value instanceof Comparable ?
                criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Comparable) value) :
                criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression) context.asExpression(value, propertyPath))
        } else if (pc instanceof Query.LessThanEquals) {
            if (pc.value instanceof QueryableCriteria) {
                Subquery<?> subquery = buildSubquery(context, propertyPath, (QueryableCriteria<?>) pc.value, propertyName)
                return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) subquery)
            }
            Object value = context.convertComparisonValue(propertyName, pc.value, propertyPath)
            return value instanceof Comparable ?
                criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Comparable) value) :
                criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression) context.asExpression(value, propertyPath))
        } else if (pc instanceof Query.In) {
            return handleIn(context, pc, propertyPath, propertyName)
        } else if (pc instanceof Query.Between) {
            Query.Between between = (Query.Between) pc
            return criteriaBuilder.between((Expression<? extends Comparable>) propertyPath,
                (Comparable) context.convertValue(propertyName, between.from, propertyPath),
                (Comparable) context.convertValue(propertyName, between.to, propertyPath))
        } else if (pc instanceof Query.SizeEquals) {
            return criteriaBuilder.equal(criteriaBuilder.size(context.resolveCollectionPath(propertyPath)), (Integer) pc.value)
        } else if (pc instanceof Query.SizeNotEquals) {
            return criteriaBuilder.notEqual(criteriaBuilder.size(context.resolveCollectionPath(propertyPath)), (Integer) pc.value)
        } else if (pc instanceof Query.SizeGreaterThan) {
            Expression sizeExpr = criteriaBuilder.size(context.resolveCollectionPath(propertyPath))
            return criteriaBuilder.greaterThan(sizeExpr, (Comparable) pc.value)
        } else if (pc instanceof Query.SizeGreaterThanEquals) {
            Expression sizeExpr = criteriaBuilder.size(context.resolveCollectionPath(propertyPath))
            return criteriaBuilder.greaterThanOrEqualTo(sizeExpr, (Comparable) pc.value)
        } else if (pc instanceof Query.SizeLessThan) {
            Expression sizeExpr = criteriaBuilder.size(context.resolveCollectionPath(propertyPath))
            return criteriaBuilder.lessThan(sizeExpr, (Comparable) pc.value)
        } else if (pc instanceof Query.SizeLessThanEquals) {
            Expression sizeExpr = criteriaBuilder.size(context.resolveCollectionPath(propertyPath))
            return criteriaBuilder.lessThanOrEqualTo(sizeExpr, (Comparable) pc.value)
        }

        throw new UnsupportedOperationException("Unsupported criterion: ${pc.class.name}".toString())
    }

    @SuppressWarnings(['unchecked', 'rawtypes'])
    private static Predicate handleEquals(CriterionContext context, Query.PropertyCriterion pc, Expression<?> propertyPath) {
        if (pc.value instanceof QueryableCriteria) {
            Subquery<?> subquery = buildSubquery(context, propertyPath, (QueryableCriteria<?>) pc.value, pc.property)
            return context.criteriaBuilder.equal(propertyPath, subquery)
        } else {
            return context.criteriaBuilder.equal(propertyPath, context.convertComparisonValue(pc.property, pc.value, propertyPath))
        }
    }

    @SuppressWarnings('unchecked')
    private static Predicate handleEqualsIgnoreCase(CriterionContext context, Query.PropertyCriterion pc, Expression<?> propertyPath) {
        Object value = pc.value
        if (String != propertyPath.javaType || !(value instanceof CharSequence)) {
            return handleEquals(context, pc, propertyPath)
        }
        return context.criteriaBuilder.equal(
                context.criteriaBuilder.lower((Expression<String>) propertyPath),
                value.toString().toLowerCase(Locale.ROOT))
    }

    @SuppressWarnings(['unchecked', 'rawtypes'])
    private static Predicate handleNotEquals(CriterionContext context, Query.PropertyCriterion pc, Expression<?> propertyPath) {
        Object value = pc.value
        HibernateCriteriaBuilder criteriaBuilder = context.criteriaBuilder
        if (value == null) {
            return criteriaBuilder.isNotNull(propertyPath)
        }
        if (value instanceof QueryableCriteria) {
            Subquery<?> subquery = buildSubquery(context, propertyPath, (QueryableCriteria<?>) value, pc.property)
            return criteriaBuilder.notEqual(propertyPath, subquery)
        }
        return criteriaBuilder.notEqual(propertyPath, context.convertComparisonValue(pc.property, value, propertyPath))
    }

    @SuppressWarnings(['unchecked', 'rawtypes'])
    private static Predicate handleIn(CriterionContext context, Query.PropertyCriterion pc, Expression<?> propertyPath, String propertyName) {
        HibernateCriteriaBuilder criteriaBuilder = context.criteriaBuilder
        Object value = pc.value
        if (value instanceof QueryableCriteria) {
            Subquery<?> subquery = buildSubquery(context, propertyPath, (QueryableCriteria<?>) value, propertyName)
            return propertyPath.in((Expression) subquery)
        }

        Collection<?> collection = value instanceof Collection ? (Collection<?>) value : Collections.singletonList(value)
        List<Object> converted = []
        for (Object v : collection) {
            if (v != null) {
                converted.add(context.convertValue(propertyName, v, propertyPath))
            }
        }

        if (converted.isEmpty()) {
            return criteriaBuilder.disjunction()
        }

        if (context.isCollectionPath(propertyPath)) {
            List<Predicate> memberOfPredicates = []
            for (Object v : converted) {
                memberOfPredicates.add(criteriaBuilder.isMember((Object) v, (Expression) context.resolveCollectionPath(propertyPath)))
            }
            return criteriaBuilder.or(memberOfPredicates.toArray(new Predicate[0]))
        }

        return propertyPath.in(converted)
    }

    @PackageScope
    @SuppressWarnings(['unchecked', 'rawtypes'])
    static Subquery<?> buildSubquery(CriterionContext context, Expression<?> propertyPath, QueryableCriteria<?> qc, String propertyName) {
        QueryableCriteria<?> materialized = qc.materializeLazyCriteria()
        Query.ProjectionList projectionList = new Query.ProjectionList()
        for (Object p : materialized.projections) {
            projectionList.add((Query.Projection) p)
        }

        if (projectionList.isEmpty()) {
            String propName = propertyName
            if (propName != null && propName.contains(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)) {
                propName = propName.split(grails.orm.HibernateCriteriaBuilder.ALIAS_SEPARATOR)[1]
            }
            if (propName != null) {
                PersistentProperty prop = materialized.persistentEntity.getPropertyByName(propName)
                if (prop != null) {
                    projectionList.add(Projections.property(propName))
                }
            }
        }

        Class<?> expectedType = resolveSubqueryType(propertyPath, materialized, projectionList)
        Subquery<?> subquery = context.criteriaQuery.subquery(expectedType)
        JpaCriteriaQueryCreator creator = new JpaCriteriaQueryCreator(projectionList, context.criteriaBuilder, (GrailsHibernatePersistentEntity) materialized.persistentEntity, (DetachedCriteria) materialized, context.conversionService)
        creator.setParentContext(context.queryContext)
        creator.populateSubquery((JpaSubQuery) subquery)
        return subquery
    }

    @PackageScope
    @SuppressWarnings(['unchecked', 'rawtypes'])
    static Class<?> resolveSubqueryType(Expression<?> propertyPath, QueryableCriteria<?> qc, Query.ProjectionList projectionList) {
        List<Query.Projection> projections = projectionList != null ? projectionList.projectionList : qc.projections
        if (!projections.isEmpty()) {
            Object first = projections.get(0)
            if (first instanceof Query.CountProjection || first instanceof Query.CountDistinctProjection) {
                return Long
            } else if (first instanceof Query.AvgProjection) {
                return Double
            } else if (first instanceof Query.IdProjection) {
                PersistentProperty identity = qc.persistentEntity.identity
                return identity != null ? identity.type : Object
            } else if (first instanceof Query.PropertyProjection) {
                PersistentProperty prop = qc.persistentEntity.getPropertyByName(((Query.PropertyProjection) first).propertyName)
                if (prop != null) {
                    return prop.type
                }
            }
        }
        return propertyPath != null ? propertyPath.javaType : qc.persistentEntity.javaClass
    }

    @PackageScope
    static Predicate handleRLike(HibernateCriteriaBuilder criteriaBuilder, Expression<String> propertyPath, Query.RLike c) {
        String pattern = c.value.toString().replaceAll('^/|/$', '')
        return criteriaBuilder.equal(
            criteriaBuilder.function(
                GrailsRLikeFunctionContributor.RLIKE,
                Boolean,
                propertyPath,
                criteriaBuilder.literal(pattern)),
            true)
    }
}
