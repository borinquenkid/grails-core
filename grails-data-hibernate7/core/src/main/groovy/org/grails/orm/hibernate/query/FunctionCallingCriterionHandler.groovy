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

import org.springframework.core.convert.ConversionService

import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion

/**
 * Handles criteria that apply a SQL/HQL function to a property before comparison,
 * such as {@code upper(name) == 'A'} or {@code year(birthDate) > 2000}.
 *
 * @since 8.0.1
 */
@CompileStatic
class FunctionCallingCriterionHandler implements InternalCriterionHandler<FunctionCallingCriterion> {

    @Override
    @SuppressWarnings(['unchecked', 'rawtypes'])
    Predicate handle(CriterionContext context, FunctionCallingCriterion criterion) {
        String functionName = criterion.functionName
        if (criterion.isOnValue()) {
            throw new UnsupportedOperationException("A function on the value of a comparison is not supported: ${functionName}".toString())
        }
        Expression<?> propertyPath = context.resolvePath(criterion.property)
        if (propertyPath == null) {
            throw new ConfigurationException("Cannot resolve property [${criterion.property}] in a ${functionName} function call".toString())
        }
        HibernateCriteriaBuilder criteriaBuilder = context.criteriaBuilder
        Expression<?> left = applyFunction(criteriaBuilder, functionName, propertyPath)
        Query.PropertyCriterion wrapped = criterion.propertyCriterion
        if (wrapped instanceof Query.In) {
            Query.In inCriterion = (Query.In) wrapped
            if (inCriterion.subquery != null) {
                Subquery<?> subquery = PropertyCriterionHandler.buildSubquery(context, left, inCriterion.subquery, criterion.property)
                return left.in((Expression) subquery)
            }
            Collection<Object> values = new ArrayList<>()
            for (Object v : inCriterion.values) {
                if (v != null) {
                    values.add(convertFunctionValue(context.conversionService, functionName, v))
                }
            }
            if (values.isEmpty()) {
                return criteriaBuilder.disjunction()
            }
            return left.in(values)
        }

        Object rawValue = wrapped.value
        if (wrapped instanceof Query.Equals) {
            if (rawValue == null) {
                return criteriaBuilder.isNull(left)
            }
            if (rawValue instanceof QueryableCriteria) {
                Subquery<?> subquery = PropertyCriterionHandler.buildSubquery(context, left, (QueryableCriteria<?>) rawValue, criterion.property)
                return criteriaBuilder.equal(left, subquery)
            }
            Object value = convertFunctionValue(context.conversionService, functionName, rawValue)
            return criteriaBuilder.equal(left, value)
        } else if (wrapped instanceof Query.NotEquals) {
            if (rawValue == null) {
                return criteriaBuilder.isNotNull(left)
            }
            if (rawValue instanceof QueryableCriteria) {
                Subquery<?> subquery = PropertyCriterionHandler.buildSubquery(context, left, (QueryableCriteria<?>) rawValue, criterion.property)
                return criteriaBuilder.notEqual(left, subquery)
            }
            Object value = convertFunctionValue(context.conversionService, functionName, rawValue)
            return criteriaBuilder.notEqual(left, value)
        }

        Object value = convertFunctionValue(context.conversionService, functionName, rawValue)
        if (wrapped instanceof Query.ILike) {
            return criteriaBuilder.ilike((Expression<String>) left, (String) value)
        } else if (wrapped instanceof Query.RLike) {
            return PropertyCriterionHandler.handleRLike(criteriaBuilder, (Expression<String>) left, (Query.RLike) wrapped)
        } else if (wrapped instanceof Query.Like) {
            return criteriaBuilder.like((Expression<String>) left, (String) value)
        } else if (wrapped instanceof Query.GreaterThan) {
            return criteriaBuilder.greaterThan((Expression<? extends Comparable>) left, (Comparable) value)
        } else if (wrapped instanceof Query.GreaterThanEquals) {
            return criteriaBuilder.greaterThanOrEqualTo((Expression<? extends Comparable>) left, (Comparable) value)
        } else if (wrapped instanceof Query.LessThan) {
            return criteriaBuilder.lessThan((Expression<? extends Comparable>) left, (Comparable) value)
        } else if (wrapped instanceof Query.LessThanEquals) {
            return criteriaBuilder.lessThanOrEqualTo((Expression<? extends Comparable>) left, (Comparable) value)
        }
        throw new UnsupportedOperationException("Unsupported criterion in a function call: ${wrapped.class.name}".toString())
    }

    @PackageScope
    @SuppressWarnings('unchecked')
    static Expression<?> applyFunction(HibernateCriteriaBuilder criteriaBuilder, String functionName, Expression<?> expression) {
        switch (functionName) {
            case 'lower':
                return criteriaBuilder.lower((Expression<String>) expression)
            case 'upper':
                return criteriaBuilder.upper((Expression<String>) expression)
            case 'trim':
                return criteriaBuilder.trim((Expression<String>) expression)
            case 'length':
                return criteriaBuilder.length((Expression<String>) expression)
            default:
                return criteriaBuilder.function(functionName, Integer, expression)
        }
    }

    @PackageScope
    static Object convertFunctionValue(ConversionService conversionService, String functionName, Object value) {
        if (value == null) {
            return null
        }
        Class<?> targetType
        switch (functionName) {
            case 'lower':
            case 'upper':
            case 'trim':
                targetType = String
                break
            default:
                targetType = Integer
        }
        return conversionService != null && conversionService.canConvert(value.getClass(), targetType) ?
                conversionService.convert(value, targetType) :
                value
    }
}
