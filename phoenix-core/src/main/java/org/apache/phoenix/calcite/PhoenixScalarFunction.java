/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.calcite;

import java.lang.reflect.Constructor;
import java.util.List;

import org.apache.calcite.adapter.enumerable.CallImplementor;
import org.apache.calcite.adapter.enumerable.RexToLixTranslator;
import org.apache.calcite.adapter.enumerable.RexImpTable.NullAs;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.schema.FunctionParameter;
import org.apache.calcite.schema.ImplementableFunction;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.phoenix.expression.function.FunctionExpression;
import org.apache.phoenix.parse.FunctionParseNode;
import org.apache.phoenix.parse.PFunction;
import org.apache.phoenix.parse.PFunction.FunctionArgument;
import org.apache.phoenix.schema.types.PDataType;
import org.apache.phoenix.schema.types.PDataTypeFactory;
import org.apache.phoenix.schema.types.PDate;
import org.apache.phoenix.schema.types.PVarchar;
import org.apache.phoenix.util.SchemaUtil;

import com.google.common.collect.Lists;

public class PhoenixScalarFunction implements ScalarFunction, ImplementableFunction {
    private final PFunction functionInfo;
    private final PDataType returnType;
    private final List<FunctionParameter> parameters;
    
    public PhoenixScalarFunction(PFunction functionInfo) {
        this.functionInfo = functionInfo;
        this.returnType =
                PDataType.fromSqlTypeName(SchemaUtil.normalizeIdentifier(functionInfo.getReturnType()));
        this.parameters = Lists.newArrayListWithExpectedSize(functionInfo.getFunctionArguments().size());
        for (final FunctionArgument arg : functionInfo.getFunctionArguments()) {
            parameters.add(
                    new FunctionParameter() {
                        public int getOrdinal() {
                            return arg.getArgPosition();
                        }

                        public String getName() {
                            return getArgumentName(arg.getArgPosition());
                        }

                        public RelDataType getType(RelDataTypeFactory typeFactory) {
                            PDataType dataType =
                                    arg.isArrayType() ? PDataType.fromTypeId(PDataType.sqlArrayType(SchemaUtil
                                            .normalizeIdentifier(SchemaUtil.normalizeIdentifier(arg
                                                    .getArgumentType())))) : PDataType.fromSqlTypeName(SchemaUtil
                                            .normalizeIdentifier(arg.getArgumentType()));
                            return typeFactory.createJavaType(dataType.getJavaClass());
                        }

                        public boolean isOptional() {
                            return arg.getDefaultValue() != null;
                        }
                    });
        }
    }

    public static PhoenixScalarFunction createBuiltinFunction(FunctionParseNode.BuiltInFunctionInfo info) {
        List<FunctionArgument> args = Lists.newArrayList();

        //TODO: add aggregate function support
        if(info.isAggregate()){
            throw new UnsupportedOperationException();
        }

        for (FunctionParseNode.BuiltInFunctionArgInfo argInfo : info.getArgs()) {
            Class<? extends PDataType>[] allowedTypes = argInfo.getAllowedTypes();
            String argType = allowedTypes.length > 0 ? PDataTypeFactory.getInstance().instanceFromClass(allowedTypes[0]).toString() : null;

            if(argType == null){
                throw new RuntimeException("function can't be converted");
            }
            FunctionArgument arg = new FunctionArgument(
                    argType,
                    false,
                    argInfo.isConstant(),
                    argInfo.getDefaultValue(),
                    argInfo.getMinValue(),
                    argInfo.getMaxValue());
            args.add(arg);
        }

        Class<? extends FunctionExpression> clazz = info.getFunc();
        PDataType dType = null;

        try {
            FunctionExpression func = clazz.newInstance();
            dType = func.getDataType();
        } catch(Exception e){
            System.out.println("return type error" + info.getName());
            throw new RuntimeException(e);
        }

        return new PhoenixScalarFunction(new PFunction(info.getName(), args, dType.getSqlTypeName(), clazz.getName(), null));
    }

    @Override
    public RelDataType getReturnType(RelDataTypeFactory typeFactory) {
        return typeFactory.createJavaType(returnType.getJavaClass());
    }

    @Override
    public List<FunctionParameter> getParameters() {
        return parameters;
    }
    
    public PFunction getFunctionInfo() {
        return functionInfo;
    }

    private static String getArgumentName(int ordinal) {
        return "arg" + ordinal;
    }

    @Override
    public CallImplementor getImplementor() {
        return new CallImplementor() {
            public Expression implement(RexToLixTranslator translator, RexCall call, NullAs nullAs) {
                return Expressions.constant(null);
            }
        };
    }
}