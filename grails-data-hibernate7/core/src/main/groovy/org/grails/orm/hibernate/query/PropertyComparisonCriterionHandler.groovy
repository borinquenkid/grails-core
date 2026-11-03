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
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.metamodel.Bindable
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute
import org.hibernate.query.criteria.HibernateCriteriaBuilder

import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.query.Query

/**
 * Handles criteria comparing two properties against each other.
 *
 * @since 8.0.1
 */
@CompileStatic
class PropertyComparisonCriterionHandler implements InternalCriterionHandler<Query.PropertyComparisonCriterion> {

    @Override
    @SuppressWarnings(['unchecked', 'rawtypes'])
    Predicate handle(CriterionContext context, Query.PropertyComparisonCriterion c) {
        Expression<?> propertyPath = context.resolvePath(c.property)
        if (propertyPath == null) {
            String className = context.entity != null ? context.entity.javaClass.name : 'unknown'
            throw new ConfigurationException("Cannot use property comparison criteria on non-existent property [${c.property}] of class [${className}]".toString())
        }
        Expression<?> otherPropertyPath = context.resolvePath(c.otherProperty)
        if (otherPropertyPath == null) {
            String className = context.entity != null ? context.entity.javaClass.name : 'unknown'
            throw new ConfigurationException("Cannot use property comparison criteria on non-existent property [${c.otherProperty}] of class [${className}]".toString())
        }

        Expression<?> left = identifierIfCompared(propertyPath, otherPropertyPath)
        Expression<?> right = identifierIfCompared(otherPropertyPath, propertyPath)
        propertyPath = left
        otherPropertyPath = right

        HibernateCriteriaBuilder criteriaBuilder = context.criteriaBuilder

        if (c instanceof Query.EqualsProperty) {
            return criteriaBuilder.equal(propertyPath, otherPropertyPath)
        } else if (c instanceof Query.NotEqualsProperty) {
            return criteriaBuilder.notEqual(propertyPath, otherPropertyPath)
        } else if (c instanceof Query.GreaterThanProperty) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath)
        } else if (c instanceof Query.GreaterThanEqualsProperty) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath)
        } else if (c instanceof Query.LessThanProperty) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath)
        } else if (c instanceof Query.LessThanEqualsProperty) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) propertyPath, (Expression<? extends Comparable>) otherPropertyPath)
        }

        throw new UnsupportedOperationException("Unsupported property comparison criterion: ${c.class.name}".toString())
    }

    /**
     * An association compared with a value that is not an instance of it, such as {@code id == team.club},
     * is compared by its identifier, as Hibernate 5 does.
     */
    @SuppressWarnings(['unchecked', 'rawtypes'])
    private static Expression<?> identifierIfCompared(Expression<?> expression, Expression<?> other) {
        if (!(expression instanceof Path)) {
            return expression
        }
        Path<?> path = (Path<?>) expression
        Bindable<?> model = path.model
        if (!(model instanceof SingularAttribute)) {
            return expression
        }
        SingularAttribute<?, ?> attribute = (SingularAttribute<?, ?>) model
        if (!(attribute.type instanceof EntityType)) {
            return expression
        }
        EntityType<?> entityType = (EntityType<?>) attribute.type
        if (path.javaType.isAssignableFrom(other.javaType) || other.javaType.isAssignableFrom(path.javaType)) {
            return expression
        }
        Class idType = entityType.idType.javaType
        return path.get(entityType.getId(idType).name)
    }
}
