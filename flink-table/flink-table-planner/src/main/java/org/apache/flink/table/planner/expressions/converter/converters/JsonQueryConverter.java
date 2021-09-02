/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.planner.expressions.converter.converters;

import org.apache.flink.table.api.JsonQueryOnEmptyOrError;
import org.apache.flink.table.api.JsonQueryWrapper;
import org.apache.flink.table.api.TableException;
import org.apache.flink.table.expressions.CallExpression;
import org.apache.flink.table.expressions.ValueLiteralExpression;
import org.apache.flink.table.functions.BuiltInFunctionDefinitions;
import org.apache.flink.table.planner.expressions.converter.CallExpressionConvertRule;
import org.apache.flink.table.planner.functions.sql.FlinkSqlOperatorTable;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlJsonQueryEmptyOrErrorBehavior;
import org.apache.calcite.sql.SqlJsonQueryWrapperBehavior;

import java.util.LinkedList;
import java.util.List;

/** Conversion for {@link BuiltInFunctionDefinitions#JSON_QUERY}. */
class JsonQueryConverter extends CustomizedConverter {
    @Override
    public RexNode convert(CallExpression call, CallExpressionConvertRule.ConvertContext context) {
        checkArgumentNumber(call, 5);

        final List<RexNode> operands = new LinkedList<>();
        operands.add(context.toRexNode(call.getChildren().get(0)));
        operands.add(context.toRexNode(call.getChildren().get(1)));

        final SqlJsonQueryWrapperBehavior wrappingBehavior =
                ((ValueLiteralExpression) call.getChildren().get(2))
                        .getValueAs(JsonQueryWrapper.class)
                        .map(this::convertWrappingBehavior)
                        .orElseThrow(
                                () ->
                                        new TableException(
                                                "Missing argument for wrapping behavior."));
        final SqlJsonQueryEmptyOrErrorBehavior onEmpty =
                ((ValueLiteralExpression) call.getChildren().get(3))
                        .getValueAs(JsonQueryOnEmptyOrError.class)
                        .map(this::convertEmptyOrErrorBehavior)
                        .orElseThrow(
                                () ->
                                        new TableException(
                                                "Missing argument for ON EMPTY behavior."));
        final SqlJsonQueryEmptyOrErrorBehavior onError =
                ((ValueLiteralExpression) call.getChildren().get(4))
                        .getValueAs(JsonQueryOnEmptyOrError.class)
                        .map(this::convertEmptyOrErrorBehavior)
                        .orElseThrow(
                                () ->
                                        new TableException(
                                                "Missing argument for ON ERROR behavior."));

        operands.add(context.getRelBuilder().getRexBuilder().makeFlag(wrappingBehavior));
        operands.add(context.getRelBuilder().getRexBuilder().makeFlag(onEmpty));
        operands.add(context.getRelBuilder().getRexBuilder().makeFlag(onError));

        return context.getRelBuilder()
                .getRexBuilder()
                .makeCall(FlinkSqlOperatorTable.JSON_QUERY, operands);
    }

    private SqlJsonQueryWrapperBehavior convertWrappingBehavior(JsonQueryWrapper wrappingBehavior) {
        switch (wrappingBehavior) {
            case WITHOUT_ARRAY:
                return SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY;
            case CONDITIONAL_ARRAY:
                return SqlJsonQueryWrapperBehavior.WITH_CONDITIONAL_ARRAY;
            case UNCONDITIONAL_ARRAY:
                return SqlJsonQueryWrapperBehavior.WITH_UNCONDITIONAL_ARRAY;
            default:
                throw new TableException("Unknown wrapping behavior: " + wrappingBehavior);
        }
    }

    private SqlJsonQueryEmptyOrErrorBehavior convertEmptyOrErrorBehavior(
            JsonQueryOnEmptyOrError onEmptyOrError) {
        switch (onEmptyOrError) {
            case NULL:
                return SqlJsonQueryEmptyOrErrorBehavior.NULL;
            case EMPTY_ARRAY:
                return SqlJsonQueryEmptyOrErrorBehavior.EMPTY_ARRAY;
            case EMPTY_OBJECT:
                return SqlJsonQueryEmptyOrErrorBehavior.EMPTY_OBJECT;
            case ERROR:
                return SqlJsonQueryEmptyOrErrorBehavior.ERROR;
            default:
                throw new TableException("Unknown ON EMPTY/ERROR behavior: " + onEmptyOrError);
        }
    }
}
