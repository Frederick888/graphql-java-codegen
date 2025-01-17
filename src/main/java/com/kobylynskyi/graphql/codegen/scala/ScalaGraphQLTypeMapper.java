package com.kobylynskyi.graphql.codegen.scala;

import com.kobylynskyi.graphql.codegen.mapper.DataModelMapper;
import com.kobylynskyi.graphql.codegen.mapper.GraphQLTypeMapper;
import com.kobylynskyi.graphql.codegen.mapper.ValueMapper;
import com.kobylynskyi.graphql.codegen.model.MappingContext;
import com.kobylynskyi.graphql.codegen.model.NamedDefinition;
import com.kobylynskyi.graphql.codegen.model.graphql.GraphQLOperation;
import com.kobylynskyi.graphql.codegen.utils.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.kobylynskyi.graphql.codegen.java.JavaGraphQLTypeMapper.JAVA_UTIL_LIST;
import static java.util.Arrays.asList;

/**
 * Mapper class for converting GraphQL types to Scala types
 */
public class ScalaGraphQLTypeMapper implements GraphQLTypeMapper {

    private static final String SCALA_UTIL_LIST = "scala.Seq";
    private static final String SCALA_UTIL_OPTIONAL = "scala.Option";
    private static final Set<String> SCALA_PRIMITIVE_TYPES = new HashSet<>(asList(
            "Byte", "Short", "Int", "Long", "Float", "Double", "Char", "Boolean"));

    private final ValueMapper valueMapper;

    public ScalaGraphQLTypeMapper(ValueMapper valueMapper) {
        this.valueMapper = valueMapper;
    }

    public static boolean isScalaPrimitive(String scalaType) {
        return SCALA_PRIMITIVE_TYPES.contains(scalaType);
    }

    public static boolean isScalaOption(String scalaType) {
        return scalaType.startsWith(SCALA_UTIL_OPTIONAL + "[") && scalaType.endsWith("]");
    }

    public static boolean isScalaCollection(String scalaType) {
        return scalaType.startsWith(SCALA_UTIL_LIST + "[") && scalaType.endsWith("]");
    }

    public static String getGenericParameter(String scalaType) {
        return scalaType.substring(SCALA_UTIL_LIST.length() + 1, scalaType.length() - 1);
    }

    @Override
    public String wrapIntoList(MappingContext mappingContext, String type, boolean mandatory) {
        return getGenericsString(mappingContext, SCALA_UTIL_LIST, type);
    }

    @Override
    public String wrapSuperTypeIntoList(MappingContext mappingContext, String type, boolean mandatory) {
        return getGenericsString(mappingContext, SCALA_UTIL_LIST, "_ <: " + type);
    }

    @Override
    public String wrapApiReturnTypeIfRequired(MappingContext mappingContext,
                                              NamedDefinition namedDefinition,
                                              String parentTypeName) {
        String computedTypeName = namedDefinition.getJavaName();
        if (parentTypeName.equalsIgnoreCase(GraphQLOperation.SUBSCRIPTION.name()) &&
                Utils.isNotBlank(mappingContext.getSubscriptionReturnType())) {
            // in case it is subscription and subscriptionReturnType is set
            return getGenericsString(mappingContext, mappingContext.getSubscriptionReturnType(), computedTypeName);
        }

        if (Boolean.TRUE.equals(mappingContext.getUseOptionalForNullableReturnTypes())
                && !namedDefinition.isMandatory()
                && !computedTypeName.startsWith(SCALA_UTIL_LIST)
                && !computedTypeName.startsWith(JAVA_UTIL_LIST)
                && !computedTypeName.startsWith(SCALA_UTIL_OPTIONAL)) {
            // Kotlin/Scala: primitive types is Option by default
            // wrap the type into scala.Option (except java list and scala list)
            computedTypeName = getGenericsString(mappingContext, SCALA_UTIL_OPTIONAL, computedTypeName);
        }

        if (computedTypeName.startsWith(SCALA_UTIL_LIST) &&
                Utils.isNotBlank(mappingContext.getApiReturnListType())) {
            // in case it is query/mutation, return type is list and apiReturnListType is set
            return computedTypeName.replace(SCALA_UTIL_LIST, mappingContext.getApiReturnListType());
        }
        if (Utils.isNotBlank(mappingContext.getApiReturnType())) {
            // in case it is query/mutation and apiReturnType is set
            return getGenericsString(mappingContext, mappingContext.getApiReturnType(), computedTypeName);
        }
        return getTypeConsideringPrimitive(mappingContext, namedDefinition, computedTypeName);
    }

    @Override
    public boolean isPrimitive(String scalaType) {
        return isScalaPrimitive(scalaType);
    }

    @Override
    public String getGenericsString(MappingContext mappingContext, String genericType, String typeParameter) {
        if (genericType.contains("%s")) {
            return String.format(genericType, typeParameter);
        } else {
            return String.format("%s[%s]", genericType, typeParameter);
        }
    }

    @Override
    public boolean addModelValidationAnnotationForType(String possiblyPrimitiveType) {
        return !ScalaGraphQLTypeMapper.isScalaPrimitive(possiblyPrimitiveType);
    }

    @Override
    public ValueMapper getValueMapper() {
        return valueMapper;
    }

    @Override
    public String getJacksonResolverTypeIdAnnotation(String modelPackageName) {
        return "com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver(classOf[" + modelPackageName +
                "GraphqlJacksonTypeIdResolver])";
    }

    @Override
    public List<String> getAdditionalAnnotations(MappingContext mappingContext, String typeName) {
        List<String> defaults = new ArrayList<>();
        String typeNameWithPrefixAndSuffix = (mappingContext.getModelNamePrefix() == null ? ""
                : mappingContext.getModelNamePrefix())
                + typeName
                + (mappingContext.getModelNameSuffix() == null ? "" : mappingContext.getModelNameSuffix());
        boolean exists = null != mappingContext.getEnumImportItSelfInScala()
                && mappingContext.getEnumImportItSelfInScala()
                .contains(typeNameWithPrefixAndSuffix);
        // todo use switch
        // Inspired by the pr https://github.com/kobylynskyi/graphql-java-codegen/pull/637/files
        if (exists) {
            String modelPackageName = DataModelMapper.getModelPackageName(mappingContext);
            if (modelPackageName == null) {
                modelPackageName = "";
            } else if (Utils.isNotBlank(modelPackageName)) {
                modelPackageName += ".";
            }
            defaults.add("com.fasterxml.jackson.module.scala.JsonScalaEnumeration(classOf[" + modelPackageName
                    + typeNameWithPrefixAndSuffix + "TypeRefer])");
        }
        return defaults;
    }
}
