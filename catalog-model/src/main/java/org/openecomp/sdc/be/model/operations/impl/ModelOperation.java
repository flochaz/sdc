/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2021 Nordix Foundation
 *  ================================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 *  ============LICENSE_END=========================================================
 */
package org.openecomp.sdc.be.model.operations.impl;

import fj.data.Either;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.onap.sdc.tosca.services.YamlUtil;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.dao.cassandra.ToscaModelImportCassandraDao;
import org.openecomp.sdc.be.dao.graph.datatype.GraphEdge;
import org.openecomp.sdc.be.dao.graph.datatype.GraphRelation;
import org.openecomp.sdc.be.dao.janusgraph.JanusGraphGenericDao;
import org.openecomp.sdc.be.dao.janusgraph.JanusGraphOperationStatus;
import org.openecomp.sdc.be.dao.jsongraph.GraphVertex;
import org.openecomp.sdc.be.dao.janusgraph.JanusGraphDao;
import org.openecomp.sdc.be.dao.jsongraph.types.VertexTypeEnum;
import org.openecomp.sdc.be.dao.neo4j.GraphEdgeLabels;
import org.openecomp.sdc.be.data.model.ToscaImportByModel;
import org.openecomp.sdc.be.datatypes.enums.GraphPropertyEnum;
import org.openecomp.sdc.be.datatypes.enums.ModelTypeEnum;
import org.openecomp.sdc.be.datatypes.enums.NodeTypeEnum;
import org.openecomp.sdc.be.model.Model;
import org.openecomp.sdc.be.model.jsonjanusgraph.operations.exception.ModelOperationExceptionSupplier;
import org.openecomp.sdc.be.model.jsonjanusgraph.operations.exception.OperationException;
import org.openecomp.sdc.be.model.operations.api.DerivedFromOperation;
import org.openecomp.sdc.be.model.operations.api.StorageOperationStatus;
import org.openecomp.sdc.be.resources.data.ModelData;
import org.openecomp.sdc.common.log.enums.EcompLoggerErrorCode;
import org.openecomp.sdc.common.log.wrappers.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component("model-operation")
public class ModelOperation {

    private static final Logger log = Logger.getLogger(ModelOperation.class);
    private static final String ADDITIONAL_TYPE_DEFINITIONS = "additional_type_definitions.yml";

    private final JanusGraphGenericDao janusGraphGenericDao;
    private final JanusGraphDao janusGraphDao;
    private final ToscaModelImportCassandraDao toscaModelImportCassandraDao;
    private final DerivedFromOperation derivedFromOperation;

    @Autowired
    public ModelOperation(final JanusGraphGenericDao janusGraphGenericDao,
                          final JanusGraphDao janusGraphDao,
                          final ToscaModelImportCassandraDao toscaModelImportCassandraDao,
                          final DerivedFromOperation derivedFromOperation) {
        this.janusGraphGenericDao = janusGraphGenericDao;
        this.janusGraphDao = janusGraphDao;
        this.toscaModelImportCassandraDao = toscaModelImportCassandraDao;
        this.derivedFromOperation = derivedFromOperation;
    }

    public Model createModel(final Model model, final boolean inTransaction) {
        Model result = null;
        final var modelData = new ModelData(model.getName(), UniqueIdBuilder.buildModelUid(model.getName()), model.getModelType());
        try {
            final Either<ModelData, JanusGraphOperationStatus> createNode = janusGraphGenericDao.createNode(modelData, ModelData.class);
            if (createNode.isRight()) {
                final var janusGraphOperationStatus = createNode.right().value();
                log.error(EcompLoggerErrorCode.DATA_ERROR, ModelOperation.class.getName(), "Problem while creating model, reason {}",
                    janusGraphOperationStatus);
                if (janusGraphOperationStatus == JanusGraphOperationStatus.JANUSGRAPH_SCHEMA_VIOLATION) {
                    throw ModelOperationExceptionSupplier.modelAlreadyExists(model.getName()).get();
                }
                throw new OperationException(ActionStatus.GENERAL_ERROR,
                    String.format("Failed to create model %s on JanusGraph with %s error", model, janusGraphOperationStatus));
            }
            addDerivedFromRelation(model);
            result = new Model(createNode.left().value().getName(), model.getDerivedFrom(), model.getModelType());
            return result;
        } finally {
            if (!inTransaction) {
                if (Objects.nonNull(result)) {
                    janusGraphGenericDao.commit();
                } else {
                    janusGraphGenericDao.rollback();
                }
            }
        }
    }

    private void addDerivedFromRelation(final Model model) {
        final String derivedFrom = model.getDerivedFrom();
        if (derivedFrom == null) {
            return;
        }
        log.debug("Adding derived from relation between model {} to its parent {}",
            model.getName(), derivedFrom);
        final Optional<Model> derivedFromModelOptional = this.findModelByName(derivedFrom);
        if (derivedFromModelOptional.isPresent()) {
            final Either<GraphRelation, StorageOperationStatus> result = derivedFromOperation.addDerivedFromRelation(
                UniqueIdBuilder.buildModelUid(model.getName()),
                UniqueIdBuilder.buildModelUid(derivedFromModelOptional.get().getName()), NodeTypeEnum.Model);
            if (result.isRight()) {
                throw new OperationException(ActionStatus.GENERAL_ERROR,
                    String.format("Failed to create relationship from model %s to derived from model %s on JanusGraph with %s error", model,
                        derivedFrom, result.right().value()));
            }
        }
    }

    public Optional<GraphVertex> findModelVertexByName(final String name) {
        if (StringUtils.isEmpty(name)) {
            return Optional.empty();
        }
        final Map<GraphPropertyEnum, Object> props = new EnumMap<>(GraphPropertyEnum.class);
        props.put(GraphPropertyEnum.NAME, name);
        props.put(GraphPropertyEnum.UNIQUE_ID, UniqueIdBuilder.buildModelUid(name));
        final List<GraphVertex> modelVerticesList = findModelVerticesByCriteria(props);
        if (modelVerticesList.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(modelVerticesList.get(0));
    }

    public Optional<Model> findModelByName(final String name) {
        if (StringUtils.isEmpty(name)) {
            return Optional.empty();
        }
        final Optional<GraphVertex> modelVertexOpt = findModelVertexByName(name);
        if (modelVertexOpt.isEmpty()) {
            return Optional.empty();
        }

        final GraphVertex graphVertex = modelVertexOpt.get();
        return Optional.of(convertToModel(graphVertex));
    }

    public void createModelImports(final String modelId, final Map<String, byte[]> zipContent) {
        if (MapUtils.isEmpty(zipContent)) {
            return;
        }
        final List<ToscaImportByModel> toscaImportByModelList = zipContent.entrySet().stream()
            .map(entry -> {
                final String path = entry.getKey();
                final byte[] bytes = entry.getValue();
                final String content = new String(bytes, StandardCharsets.UTF_8);
                final var toscaImportByModel = new ToscaImportByModel();
                toscaImportByModel.setModelId(modelId);
                toscaImportByModel.setFullPath(path);
                toscaImportByModel.setContent(content);
                return toscaImportByModel;
            }).collect(Collectors.toList());
        toscaModelImportCassandraDao.importAll(modelId, toscaImportByModelList);
    }

    /**
     * Find all the model default imports, with the option to include the default imports from the parent model.
     *
     * @param modelId       the model id
     * @param includeParent a flag to include the parent model imports.
     * @return the list of model default imports, or an empty list if no imports were found.
     */
    public List<ToscaImportByModel> findAllModelImports(final String modelId, final boolean includeParent) {
        final List<ToscaImportByModel> toscaImportByModelList = toscaModelImportCassandraDao.findAllByModel(modelId);
        if (includeParent) {
            findModelByName(modelId).ifPresent(model -> {
                if (model.getDerivedFrom() != null) {
                    toscaImportByModelList.addAll(toscaModelImportCassandraDao.findAllByModel(model.getDerivedFrom()));
                }
            });
        }
        return toscaImportByModelList;
    }

    /**
     * Finds all the models.
     *
     * @return the list of models
     */
    public List<Model> findAllModels() {
        return findModelsByCriteria(Collections.emptyMap());
    }
    
    public List<Model> findModels(final ModelTypeEnum modelType) {
        final Map<GraphPropertyEnum, Object> propertyCriteria = new EnumMap<>(GraphPropertyEnum.class);
        propertyCriteria.put(GraphPropertyEnum.MODEL_TYPE, modelType.getValue());
        
        return findModelsByCriteria(propertyCriteria);
    }

    private List<Model> findModelsByCriteria(final Map<GraphPropertyEnum, Object> propertyCriteria) {
        final List<GraphVertex> modelVerticesByCriteria = findModelVerticesByCriteria(propertyCriteria);
        if (modelVerticesByCriteria.isEmpty()) {
            return Collections.emptyList();
        }

        return modelVerticesByCriteria.stream().map(this::convertToModel).collect(Collectors.toList());
    }

    private List<GraphVertex> findModelVerticesByCriteria(final Map<GraphPropertyEnum, Object> propertyCriteria) {
        final Either<List<GraphVertex>, JanusGraphOperationStatus> result = janusGraphDao.getByCriteria(VertexTypeEnum.MODEL, propertyCriteria);
        if (result.isRight()) {
            final var janusGraphOperationStatus = result.right().value();
            if (janusGraphOperationStatus == JanusGraphOperationStatus.NOT_FOUND) {
                return Collections.emptyList();
            }
            final var operationException = ModelOperationExceptionSupplier.failedToRetrieveModels(janusGraphOperationStatus).get();
            log.error(EcompLoggerErrorCode.DATA_ERROR, this.getClass().getName(), operationException.getMessage());
            throw operationException;
        }
        return result.left().value();
    }

    private Model convertToModel(final GraphVertex modelGraphVertex) {
        final String modelName = (String) modelGraphVertex.getMetadataProperty(GraphPropertyEnum.NAME);
        final String modelTypeProperty = (String) modelGraphVertex.getMetadataProperty(GraphPropertyEnum.MODEL_TYPE);
        final ModelTypeEnum modelType = StringUtils.isEmpty(modelTypeProperty) ? ModelTypeEnum.NORMATIVE : ModelTypeEnum.findByValue(modelTypeProperty).get();
        
        final Either<ImmutablePair<ModelData, GraphEdge>, JanusGraphOperationStatus> parentNode =
            janusGraphGenericDao.getChild(UniqueIdBuilder.getKeyByNodeType(NodeTypeEnum.Model), UniqueIdBuilder.buildModelUid(modelName),
                GraphEdgeLabels.DERIVED_FROM, NodeTypeEnum.Model, ModelData.class);
        log.debug("After retrieving DERIVED_FROM node of {}. status is {}", modelName, parentNode);
        if (parentNode.isRight()) {
            final JanusGraphOperationStatus janusGraphOperationStatus = parentNode.right().value();
            if (janusGraphOperationStatus != JanusGraphOperationStatus.NOT_FOUND) {
                final var operationException = ModelOperationExceptionSupplier.failedToRetrieveModels(janusGraphOperationStatus).get();
                log.error(EcompLoggerErrorCode.DATA_ERROR, this.getClass().getName(), operationException.getMessage());
                throw operationException;
            }
            return new Model((String) modelGraphVertex.getMetadataProperty(GraphPropertyEnum.NAME), modelType);
        } else {
            final ModelData parentModel = parentNode.left().value().getKey();
            return new Model((String) modelGraphVertex.getMetadataProperty(GraphPropertyEnum.NAME), parentModel.getName(), modelType);
        }
    }

    public void addTypesToDefaultImports(final String typesYaml, final String modelName) {
        final List<ToscaImportByModel> allSchemaImportsByModel = toscaModelImportCassandraDao.findAllByModel(modelName);
        final Optional<ToscaImportByModel> additionalTypeDefinitionsOptional = allSchemaImportsByModel.stream()
            .filter(t -> ADDITIONAL_TYPE_DEFINITIONS.equals(t.getFullPath())).findAny();
        final ToscaImportByModel toscaImportByModelAdditionalTypeDefinitions;
        final List<ToscaImportByModel> schemaImportsByModel;
        if (additionalTypeDefinitionsOptional.isPresent()) {
            toscaImportByModelAdditionalTypeDefinitions = additionalTypeDefinitionsOptional.get();
            schemaImportsByModel = allSchemaImportsByModel.stream()
                .filter(toscaImportByModel -> !ADDITIONAL_TYPE_DEFINITIONS.equals(toscaImportByModel.getFullPath()))
                .collect(Collectors.toList());
        } else {
            toscaImportByModelAdditionalTypeDefinitions = new ToscaImportByModel();
            toscaImportByModelAdditionalTypeDefinitions.setModelId(modelName);
            toscaImportByModelAdditionalTypeDefinitions.setFullPath(ADDITIONAL_TYPE_DEFINITIONS);
            toscaImportByModelAdditionalTypeDefinitions.setContent(typesYaml);
            schemaImportsByModel = new ArrayList<>(allSchemaImportsByModel);
        }

        final List<ToscaImportByModel> toscaImportByModels = removeExistingDefaultImports(typesYaml, schemaImportsByModel);

        final Map<String, Object> originalContent = (Map<String, Object>) new Yaml().load(toscaImportByModelAdditionalTypeDefinitions.getContent());
        toscaImportByModelAdditionalTypeDefinitions.setContent(buildAdditionalTypeDefinitionsContent(typesYaml, originalContent).toString());
        toscaImportByModels.add(toscaImportByModelAdditionalTypeDefinitions);

        toscaModelImportCassandraDao.importOnly(modelName, toscaImportByModels);
    }

    private List<ToscaImportByModel> removeExistingDefaultImports(final String typesYaml, final List<ToscaImportByModel> schemaImportsByModel) {
        final List<ToscaImportByModel> toscaImportByModels = new ArrayList<>();
        schemaImportsByModel.forEach(toscaImportByModel -> {
            final ToscaImportByModel toscaImportByModelNew = new ToscaImportByModel();
            toscaImportByModelNew.setModelId(toscaImportByModel.getModelId());
            toscaImportByModelNew.setFullPath(toscaImportByModel.getFullPath());

            final Map<String, Object> existingImportYamlMap = (Map<String, Object>) new Yaml().load(toscaImportByModel.getContent());

            ((Map<String, Object>) new Yaml().load(typesYaml)).keySet().forEach(existingImportYamlMap::remove);

            final StringBuilder stringBuilder = new StringBuilder();
            existingImportYamlMap.forEach((key, value) -> {
                final Map<Object, Object> hashMap = new HashMap<>();
                hashMap.put(key, value);
                stringBuilder.append("\n").append(new YamlUtil().objectToYaml(hashMap));
            });

            toscaImportByModelNew.setContent(stringBuilder.toString());
            toscaImportByModels.add(toscaImportByModelNew);
        });
        return toscaImportByModels;
    }

    private StringBuilder buildAdditionalTypeDefinitionsContent(final String typesYaml, final Map<String, Object> originalContent) {
        final var stringBuilder = new StringBuilder();

        final Map<String, Object> typesYamlMap = (Map<String, Object>) new Yaml().load(typesYaml);
        final Set<String> typeYmlKeySet = typesYamlMap.keySet();

        originalContent.forEach((key, value) -> {
            final Map<Object, Object> hashMap = new HashMap<>();
            if (typeYmlKeySet.contains(key)) {
                hashMap.put(key, typesYamlMap.get(key));
            } else {
                hashMap.put(key, value);
            }
            final String newContent = new YamlUtil().objectToYaml(hashMap);
            stringBuilder.append("\n").append(newContent);
        });
        return stringBuilder;
    }

}
