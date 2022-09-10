/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 * Copyright 2018 SmartBear Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openapitools.codegen.languages;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.*;
import org.openapitools.codegen.meta.features.*;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

import static org.openapitools.codegen.utils.StringUtils.dashize;
import static org.openapitools.codegen.utils.StringUtils.underscore;

public class ClojureServerCodegen extends DefaultCodegen implements CodegenConfig {
    private final Logger LOGGER = LoggerFactory.getLogger(ClojureServerCodegen.class);
    private static final String PROJECT_NAME = "projectName";
    private static final String PROJECT_DESCRIPTION = "projectDescription";
    private static final String PROJECT_VERSION = "projectVersion";
    private static final String PROJECT_URL = "projectUrl";
    private static final String PROJECT_LICENSE_NAME = "projectLicenseName";
    private static final String PROJECT_LICENSE_URL = "projectLicenseUrl";
    private static final String BASE_NAMESPACE = "baseNamespace";

    static final String VENDOR_EXTENSION_X_BASE_SPEC = "x-base-spec";
    static final String X_MODELS = "x-models";

    protected String projectName;
    protected String projectDescription;
    protected String projectVersion;
    protected String baseNamespace;
    protected Set<String> baseSpecs;
    protected Set<String> models = new HashSet<>();

    protected String sourceFolder = "src";

    public ClojureServerCodegen() {
        super();

        modifyFeatureSet(features -> features.includeDocumentationFeatures(DocumentationFeature.Readme));

        cliOptions.add(new CliOption(BASE_NAMESPACE,
                "the base/top namespace (Default: generated from projectName)"));

        // clear import mapping (from default generator)
        importMapping.clear();

        outputFolder = "generated-code" + File.separator + "clojure-server";
        embeddedTemplateDir = templateDir = "clojure-server";
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    @Override
    public String getName() {
        System.out.println("getName: returning clojure-server");
        return "clojure-server";
    }

    @Override
    public String getHelp() {
        return "Generates a Clojure server library.";
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        super.preprocessOpenAPI(openAPI);

        if (additionalProperties.containsKey(BASE_NAMESPACE)) {
            baseNamespace = ((String) additionalProperties.get(BASE_NAMESPACE));
        }

        if (projectName == null) {
            projectName = "openapi-clj-server";
        }
        if (projectVersion == null) {
            projectVersion = "1.0.0";
        }
        if (projectDescription == null) {
            projectDescription = "Client library of " + projectName;
        }
        if (baseNamespace == null) {
            baseNamespace = dashize(projectName);
        }
        apiPackage = baseNamespace + ".routes";
        modelPackage = baseNamespace + ".schema";

        additionalProperties.put(PROJECT_NAME, projectName);
        additionalProperties.put(PROJECT_DESCRIPTION, escapeText(projectDescription));
        additionalProperties.put(PROJECT_VERSION, projectVersion);
        additionalProperties.put(BASE_NAMESPACE, baseNamespace);
        additionalProperties.put(CodegenConstants.API_PACKAGE, apiPackage);
        additionalProperties.put(CodegenConstants.MODEL_PACKAGE, modelPackage);

        supportingFiles.add(new SupportingFile("schema.mustache", "", "schema.clj"));
        supportingFiles.add(new SupportingFile("routes.mustache", "", "routes.clj"));

        typeMapping.put("integer", "int?");
        typeMapping.put("long", "int?");
        typeMapping.put("short", "int?");
        typeMapping.put("number", "float?");
        typeMapping.put("float", "float?");
        typeMapping.put("double", "float?");
        typeMapping.put("array", "list?");
        typeMapping.put("map", "map?");
        typeMapping.put("boolean", "boolean?");
        typeMapping.put("string", "string?");
        typeMapping.put("char", "char?");
        typeMapping.put("date", "string?");
        typeMapping.put("DateTime", "string?");
        typeMapping.put("UUID", "uuid?");
        typeMapping.put("URI", "string?");
    }

    @Override
    public String apiFileFolder() {
        return outputFolder;
    }

    @Override
    public String modelFileFolder() {
        return outputFolder;
    }

    @Override
    public String toModelName(String name) {
        return dashize(name);
    }

    @Override
    public String getTypeDeclaration(Schema p) {
        if (p instanceof ArraySchema) {
            ArraySchema ap = (ArraySchema) p;
            Schema inner = ap.getItems();

            return "[:sequential " + getTypeDeclaration(inner) + "]";
        } else if (ModelUtils.isMapSchema(p)) {
            Schema inner = (Schema) p.getAdditionalProperties();

            return "[:map-of :string" + getTypeDeclaration(inner) + "]";
        }

        if (!typeMapping.containsKey(super.getSchemaType(p))) {
            return dashize(super.getTypeDeclaration(p));
        } else {
            return super.getTypeDeclaration(p);
        }
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap operations, List<ModelMap> allModels) {
        OperationMap objs = operations.getOperations();
        List<CodegenOperation> ops = objs.getOperation();
        for (CodegenOperation op : ops) {
            // Convert httpMethod to lower case, e.g. "get", "post"
            op.httpMethod = ":" + op.httpMethod.toLowerCase(Locale.ROOT);
            op.nickname = ":" + dashize(op.nickname);
            op.path = op.path.replace("{", ":").replace("}", "");

            for (CodegenParameter param : op.allParams) {
                if (!param.dataType.contains("?")) {
                    param.dataType = ":" + dashize(param.dataType);
                }
            }

            if (op.returnType != null) {
                if (!op.returnType.contains("?")) {
                    op.returnType = ":" + dashize(op.returnType);
                }
            }
        }
        return operations;
    }

    @Override
    public String escapeQuotationMark(String input) {
        // remove " to avoid code injection
        return input.replace("\"", "");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        // ref: https://clojurebridge.github.io/community-docs/docs/clojure/comment/
        return input.replace("(comment", "(_comment");
    }

    @Override
    public GeneratorLanguage generatorLanguage() { return GeneratorLanguage.CLOJURE; }
}
