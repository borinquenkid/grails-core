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
import jakarta.persistence.criteria.ParameterExpression
import jakarta.persistence.criteria.Predicate
import org.hibernate.query.criteria.HibernateCriteriaBuilder

/**
 * Handles raw native SQL restriction criteria with optional parameter binding and alias substitution.
 *
 * @since 8.0.1
 */
@CompileStatic
class SqlRestrictionCriterionHandler implements InternalCriterionHandler<SqlRestriction> {

    @Override
    Predicate handle(CriterionContext context, SqlRestriction restriction) {
        HibernateCriteriaBuilder criteriaBuilder = context.criteriaBuilder
        List<Expression<?>> arguments = PredicateGenerator.nativeSqlArguments(
                criteriaBuilder, restriction.sql(), context.root, context.entity)

        for (Object value : restriction.values()) {
            ParameterExpression<?> parameter = criteriaBuilder.parameter(value.getClass())
            context.queryContext.bindParameter(parameter, value)
            arguments.add(parameter)
        }

        return criteriaBuilder.isTrue(criteriaBuilder.function(
                GrailsSqlRestrictionFunction.NAME, Boolean, arguments.toArray(new Expression<?>[0])))
    }
}
