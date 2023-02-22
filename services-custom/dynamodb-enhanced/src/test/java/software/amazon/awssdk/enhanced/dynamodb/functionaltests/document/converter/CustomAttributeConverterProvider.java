/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.enhanced.dynamodb.functionaltests.document.converter;

import java.util.Map;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.internal.converter.attribute.EnhancedAttributeValue;
import software.amazon.awssdk.enhanced.dynamodb.internal.converter.string.IntegerStringConverter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.testbeans.SingleConverterProvidersBean;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.utils.ImmutableMap;

public class CustomAttributeConverterProvider implements AttributeConverterProvider {

    private final Map<EnhancedType<?>, AttributeConverter<?>> converterCache = ImmutableMap.of(
        EnhancedType.of(CustomClass.class), new CustomClassAttributeConverter(),
        EnhancedType.of(Integer.class), new CustomIntegerAttributeConverter()
    );

    @SuppressWarnings("unchecked")
    @Override
    public <T> AttributeConverter<T> converterFor(EnhancedType<T> enhancedType) {
        return (AttributeConverter<T>) converterCache.get(enhancedType);
    }

    public static CustomAttributeConverterProvider create(){
        return new CustomAttributeConverterProvider();
    }


    private static class CustomStringAttributeConverter implements AttributeConverter<String> {

        final static String DEFAULT_SUFFIX = "-custom";

        @Override
        public AttributeValue transformFrom(String input) {
            return EnhancedAttributeValue.fromString(input + DEFAULT_SUFFIX).toAttributeValue();
        }

        @Override
        public String transformTo(AttributeValue input) {
            return input.s();
        }

        @Override
        public EnhancedType<String> type() {
            return EnhancedType.of(String.class);
        }

        @Override
        public AttributeValueType attributeValueType() {
            return AttributeValueType.S;
        }
    }

    private static class CustomIntegerAttributeCo implements AttributeConverter<Integer> {

        final static Integer DEFAULT_INCREMENT = 10;

        @Override
        public AttributeValue transformFrom(Integer input) {
            return EnhancedAttributeValue.fromNumber(IntegerStringConverter.create().toString(input + DEFAULT_INCREMENT))
                                         .toAttributeValue();
        }

        @Override
        public Integer transformTo(AttributeValue input) {
            return Integer.valueOf(input.n());
        }

        @Override
        public EnhancedType<Integer> type() {
            return EnhancedType.of(Integer.class);
        }

        @Override
        public AttributeValueType attributeValueType() {
            return AttributeValueType.N;
        }
    }

}