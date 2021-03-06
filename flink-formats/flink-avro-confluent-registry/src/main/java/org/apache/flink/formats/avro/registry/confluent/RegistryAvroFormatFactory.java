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

package org.apache.flink.formats.avro.registry.confluent;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.formats.avro.AvroRowDataDeserializationSchema;
import org.apache.flink.formats.avro.AvroRowDataSerializationSchema;
import org.apache.flink.formats.avro.AvroToRowDataConverters;
import org.apache.flink.formats.avro.RowDataToAvroConverters;
import org.apache.flink.formats.avro.typeutils.AvroSchemaConverter;
import org.apache.flink.table.api.ValidationException;
import org.apache.flink.table.connector.ChangelogMode;
import org.apache.flink.table.connector.format.DecodingFormat;
import org.apache.flink.table.connector.format.EncodingFormat;
import org.apache.flink.table.connector.sink.DynamicTableSink;
import org.apache.flink.table.connector.source.DynamicTableSource;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.factories.DeserializationFormatFactory;
import org.apache.flink.table.factories.DynamicTableFactory;
import org.apache.flink.table.factories.FactoryUtil;
import org.apache.flink.table.factories.SerializationFormatFactory;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.RowType;

import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.apache.flink.formats.avro.registry.confluent.RegistryAvroOptions.BASIC_AUTH_CREDENTIALS_SOURCE;
import static org.apache.flink.formats.avro.registry.confluent.RegistryAvroOptions.BASIC_AUTH_USER_INFO;
import static org.apache.flink.formats.avro.registry.confluent.RegistryAvroOptions.BEARER_AUTH_CREDENTIALS_SOURCE;
import static org.apache.flink.formats.avro.registry.confluent.RegistryAvroOptions.BEARER_AUTH_TOKEN;
import static org.apache.flink.formats.avro.registry.confluent.RegistryAvroOptions.SCHEMA_REGISTRY_SUBJECT;
import static org.apache.flink.formats.avro.registry.confluent.RegistryAvroOptions.SCHEMA_REGISTRY_URL;
import static org.apache.flink.formats.avro.registry.confluent.RegistryAvroOptions.SSL_KEYSTORE_LOCATION;
import static org.apache.flink.formats.avro.registry.confluent.RegistryAvroOptions.SSL_KEYSTORE_PASSWORD;
import static org.apache.flink.formats.avro.registry.confluent.RegistryAvroOptions.SSL_TRUSTSTORE_LOCATION;
import static org.apache.flink.formats.avro.registry.confluent.RegistryAvroOptions.SSL_TRUSTSTORE_PASSWORD;

/**
 * Table format factory for providing configured instances of Schema Registry Avro to RowData {@link
 * SerializationSchema} and {@link DeserializationSchema}.
 */
public class RegistryAvroFormatFactory
        implements DeserializationFormatFactory, SerializationFormatFactory {

    public static final String IDENTIFIER = "avro-confluent";

    @Override
    public DecodingFormat<DeserializationSchema<RowData>> createDecodingFormat(
            DynamicTableFactory.Context context, ReadableConfig formatOptions) {
        FactoryUtil.validateFactoryOptions(this, formatOptions);

        String schemaRegistryURL = formatOptions.get(SCHEMA_REGISTRY_URL);
        Map<String, ?> optionalPropertiesMap = buildOptionalPropertiesMap(formatOptions);

        return new DecodingFormat<DeserializationSchema<RowData>>() {
            @Override
            public DeserializationSchema<RowData> createRuntimeDecoder(
                    DynamicTableSource.Context context, DataType producedDataType) {
                final RowType rowType = (RowType) producedDataType.getLogicalType();
                final TypeInformation<RowData> rowDataTypeInfo =
                        context.createTypeInformation(producedDataType);
                return new AvroRowDataDeserializationSchema(
                        ConfluentRegistryAvroDeserializationSchema.forGeneric(
                                AvroSchemaConverter.convertToSchema(rowType),
                                schemaRegistryURL,
                                optionalPropertiesMap),
                        AvroToRowDataConverters.createRowConverter(rowType),
                        rowDataTypeInfo);
            }

            @Override
            public ChangelogMode getChangelogMode() {
                return ChangelogMode.insertOnly();
            }
        };
    }

    @Override
    public EncodingFormat<SerializationSchema<RowData>> createEncodingFormat(
            DynamicTableFactory.Context context, ReadableConfig formatOptions) {
        FactoryUtil.validateFactoryOptions(this, formatOptions);

        String schemaRegistryURL = formatOptions.get(SCHEMA_REGISTRY_URL);
        Optional<String> subject = formatOptions.getOptional(SCHEMA_REGISTRY_SUBJECT);
        Map<String, ?> optionalPropertiesMap = buildOptionalPropertiesMap(formatOptions);

        if (!subject.isPresent()) {
            throw new ValidationException(
                    String.format(
                            "Option %s.%s is required for serialization",
                            IDENTIFIER, SCHEMA_REGISTRY_SUBJECT.key()));
        }

        return new EncodingFormat<SerializationSchema<RowData>>() {
            @Override
            public SerializationSchema<RowData> createRuntimeEncoder(
                    DynamicTableSink.Context context, DataType consumedDataType) {
                final RowType rowType = (RowType) consumedDataType.getLogicalType();
                return new AvroRowDataSerializationSchema(
                        rowType,
                        ConfluentRegistryAvroSerializationSchema.forGeneric(
                                subject.get(),
                                AvroSchemaConverter.convertToSchema(rowType),
                                schemaRegistryURL,
                                optionalPropertiesMap),
                        RowDataToAvroConverters.createConverter(rowType));
            }

            @Override
            public ChangelogMode getChangelogMode() {
                return ChangelogMode.insertOnly();
            }
        };
    }

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        Set<ConfigOption<?>> options = new HashSet<>();
        options.add(SCHEMA_REGISTRY_URL);
        return options;
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        Set<ConfigOption<?>> options = new HashSet<>();
        options.add(SCHEMA_REGISTRY_SUBJECT);
        options.add(SSL_KEYSTORE_LOCATION);
        options.add(SSL_KEYSTORE_PASSWORD);
        options.add(SSL_TRUSTSTORE_LOCATION);
        options.add(SSL_TRUSTSTORE_PASSWORD);
        options.add(BASIC_AUTH_CREDENTIALS_SOURCE);
        options.add(BASIC_AUTH_USER_INFO);
        options.add(BEARER_AUTH_CREDENTIALS_SOURCE);
        options.add(BEARER_AUTH_TOKEN);
        return options;
    }

    public static @Nullable Map<String, String> buildOptionalPropertiesMap(
            ReadableConfig formatOptions) {
        final Map<String, String> properties = new HashMap<>();

        formatOptions
                .getOptional(SSL_KEYSTORE_LOCATION)
                .ifPresent(v -> properties.put("schema.registry.ssl.keystore.location", v));
        formatOptions
                .getOptional(SSL_KEYSTORE_PASSWORD)
                .ifPresent(v -> properties.put("schema.registry.ssl.keystore.password", v));
        formatOptions
                .getOptional(SSL_TRUSTSTORE_LOCATION)
                .ifPresent(v -> properties.put("schema.registry.ssl.truststore.location", v));
        formatOptions
                .getOptional(SSL_TRUSTSTORE_PASSWORD)
                .ifPresent(v -> properties.put("schema.registry.ssl.truststore.password", v));
        formatOptions
                .getOptional(BASIC_AUTH_CREDENTIALS_SOURCE)
                .ifPresent(v -> properties.put("basic.auth.credentials.source", v));
        formatOptions
                .getOptional(BASIC_AUTH_USER_INFO)
                .ifPresent(v -> properties.put("basic.auth.user.info", v));
        formatOptions
                .getOptional(BEARER_AUTH_CREDENTIALS_SOURCE)
                .ifPresent(v -> properties.put("bearer.auth.credentials.source", v));
        formatOptions
                .getOptional(BEARER_AUTH_TOKEN)
                .ifPresent(v -> properties.put("bearer.auth.token", v));

        if (properties.isEmpty()) {
            return null;
        }
        return properties;
    }
}
