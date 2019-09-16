/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
import { createSelector } from 'reselect';
import { createFeatureSelector, State } from '@ngrx/store';

import * as fromRoot from 'reducers';
import * as fromWizard from './wizard';
import * as fromEntities from './entities';
import * as fromDebug from './debug';
import * as fromExternalData from './externalData';
import * as fromWriters from './writers';

import { WizardEdge, WizardNode } from '@app/wizard/models/node';
import { WizardAnnotation } from '@app/shared/wizard/components/wizard-annotation/wizard-annotation.model';
import { StepType } from '@models/enums';

export interface WizardState {
  wizard: fromWizard.State;
  entities: fromEntities.State;
  debug: fromDebug.State;
  externalData: fromExternalData.State;
  writers: fromWriters.State;
}

export interface State extends fromRoot.State {
  wizard: WizardState;
}

export const reducers = {
  wizard: fromWizard.reducer,
  entities: fromEntities.reducer,
  debug: fromDebug.reducer,
  externalData: fromExternalData.reducer,
  writers: fromWriters.reducer
};

export const getWizardFeatureState = createFeatureSelector<WizardState>('wizard');

export const getWizardState = createSelector(
  getWizardFeatureState,
  state => state.wizard
);

export const getDebugState = createSelector(
  getWizardFeatureState,
  state => state.debug
);

export const getEntitiesState = createSelector(
  getWizardFeatureState,
  state => state.entities
);

export const getExternalDataState = createSelector(
  getWizardFeatureState,
  state => state.externalData
);

export const getEdges = createSelector(
  getWizardState,
  (state) => state.edges
);

export const getWritersState = createSelector(
  getWizardFeatureState,
  state => state.writers
);

export const getWorkflowNodes = createSelector(
  getWizardState,
  (state) => state.nodes
);

export const getEdgeOptions = createSelector(
  getWizardState,
  (state) => state.edgeOptions
);

export const getWizardNofications = createSelector(
  getEntitiesState,
  (state) => state.notification
);

export const getDebugResult = createSelector(
  getDebugState,
  (state) => state.lastDebugResult
);

export const getServerStepValidation = createSelector(
  getWizardState,
  (state) => state.serverStepValidations
);

export const getNodesMap = createSelector(
  getWorkflowNodes,
  (nodes) => nodes.reduce(function (map, obj) {
    map[obj.name] = obj;
    return map;
  }, {})
);

export const getWorkflowEdges = createSelector(
  getEdges,
  getNodesMap,
  (edges: WizardEdge[], nodesMap: any) => {
    return edges.map((edge: WizardEdge) => ({
      origin: nodesMap[edge.origin],
      destination: nodesMap[edge.destination],
      dataType: edge.dataType
    }));
  }
);

export const getErrorsManagementOutputs = createSelector(
  getWorkflowNodes,
  (wNodes) => wNodes.reduce((filtered: Array<string>, workflowNode) => {
    if (workflowNode.stepType === 'Output' && workflowNode.configuration.errorSink) {
      filtered.push(workflowNode.name);
    }
    return filtered;
  }, [])
);

export const getSelectedNodeData = createSelector(getWizardState, fromWizard.getSelectedEntityData);

export const getSelectedNodeSchemas = createSelector(
  getSelectedNodeData,
  getDebugResult,
  getEdges,
  (selectedNode: WizardNode, debugResult: any, edges: any) => {
    if (edges && edges.length && debugResult && debugResult.steps && selectedNode) {
      return {
        inputs: edges.filter(edge => edge.destination === selectedNode.name)
          .map(edge => edge.dataType === 'ValidData' ? debugResult.steps[edge.origin] : debugResult.steps[edge.origin + '_Discard']).filter(input => input).sort(),
        output: debugResult.steps[selectedNode.name],
        outputs: Object.keys(debugResult.steps)
          .map(key => debugResult.steps[key])
          .filter(output => output.error || (output.result.step && (output.result.step === selectedNode.name || output.result.step === selectedNode.name + '_Discard')))
          .sort((a, b) => a.result && b.result && a.result.step && a.result.step > b.result.step ? 1 : -1)
      };
    } else {
      return null;
    }
  });

export const isPipelinesNodeSelected = createSelector(
  getSelectedNodeData,
  (selectedNode: WizardNode) => {
    return !!(selectedNode && selectedNode.classPrettyName === 'MlPipeline');
  }
);

export const getSelectedEntityData = createSelector(
  getSelectedNodeData,
  getDebugResult,
  getServerStepValidation,
  getSelectedNodeSchemas,
  (selectedNode: WizardNode, debugResult: any, serverStepValidation: Array<any>, schemas: any) => {
    if (selectedNode) {
      const entityData = debugResult && debugResult.steps && debugResult.steps[selectedNode.name] ? {
        ...selectedNode,
        debugResult: debugResult.steps[selectedNode.name]
      } : selectedNode;

      return {
        ...entityData,
        schemas: schemas,
        serverValidationError: (serverStepValidation[selectedNode.name]) ? serverStepValidation[selectedNode.name].errors : null
      };
    } else {
      return {};
    }
  }
);



export const getEditionConfigState = createSelector(getWizardState, state => state.editionConfig);
export const getEditionTypeState = createSelector(getWizardState, state => state.editionConfigType);
export const getIsPipelinesEdition = createSelector(getWizardState, state => state.isPipelineEdition);

export const getEditionConfig = createSelector(
  getEditionConfigState,
  getEditionTypeState,
  getIsPipelinesEdition,
  (isEdition, editionType, isPipelinesEdition) => ({
    isEdition,
    editionType,
    isPipelinesEdition
  }));

export const getEditionConfigMode = createSelector(
  getEditionConfig,
  getDebugResult,
  getServerStepValidation,
  getSelectedNodeSchemas,
  getEdges, (editionConfig: any, debugResult: any, stepValidation, schemas: any, edges: WizardEdge[]) => {
    return editionConfig && editionConfig.isEdition ?
      {
        ...editionConfig,
        serverValidation: (stepValidation[editionConfig.editionType.data.name]) ? stepValidation[editionConfig.editionType.data.name].errors : null,
        serverValidationInternalErrors: (stepValidation[editionConfig.editionType.data.name]) ? stepValidation[editionConfig.editionType.data.name].internalErrors : null,
        inputSteps: edges.filter(edge => edge.destination === editionConfig.editionType.data.name)
          .map(edge => edge.origin),
        debugResult: debugResult && debugResult.steps && debugResult.steps[editionConfig.editionType.data.name],
        schemas: schemas,
      } : editionConfig;
  }
);
export const showDebugConsole = createSelector(getDebugState, state => state.showDebugConsole);
export const getConsoleDebugEntity = createSelector(getDebugState, state => state.showedDebugDataEntity);


export const getConsoleDebugEntityData = createSelector(
  showDebugConsole,
  getConsoleDebugEntity,
  getSelectedNodeData,
  getDebugResult,
  (showConsole, debugEntity, selectedNode, debugResult) => {
    if (!showConsole || !debugResult.steps) {
      return null;
    } else {
      if (selectedNode && debugEntity && debugEntity.length) {
        return {
          ...debugResult.steps[debugEntity],
          debugEntityName: debugEntity
        };
      } else if (selectedNode) {
        return {
          ...debugResult.steps[selectedNode.name],
          debugEntityName: selectedNode.name
        };
      } else {
        return null;
      }
    }
  });

// wizard
export const getDebugFile = createSelector(getWizardState, state => state.debugFile);
export const getWorkflowId = createSelector(getWizardState, state => state.workflowId);
export const isCreationMode = createSelector(getEntitiesState, fromEntities.isCreationMode);
export const getMenuOptions = createSelector(getEntitiesState, fromEntities.getMenuOptions);
export const getWorkflowType = createSelector(getEntitiesState, fromEntities.getWorkflowType);
export const getTemplates = createSelector(getEntitiesState, fromEntities.getTemplates);
export const isShowedEntityDetails = createSelector(getWizardState, state => state.showEntityDetails);
export const getSelectedEntities = createSelector(getWizardState, state => state.selectedEntities);
export const showSettings = createSelector(getWizardState, state => state.showSettings);
export const isEntitySaved = createSelector(getWizardState, state => state.editionSaved);
export const getWorkflowSettings = createSelector(getWizardState, state => state.settings);
export const getWorkflowPosition = createSelector(getWizardState, state => state.svgPosition);
export const isSavedWorkflow = createSelector(getWizardState, state => state.savedWorkflow);
export const getSelectedRelation = createSelector(getWizardState, state => state.selectedEdge);
export const areUndoRedoEnabled = createSelector(getWizardState, fromWizard.areUndoRedoEnabled);
export const getValidationErrors = createSelector(getWizardState, state => state.validationErrors);
export const isPristine = createSelector(getWizardState, state => state.pristineWorkflow);
export const isLoading = createSelector(getWizardState, state => state.loading);
export const getWorkflowHeaderData = createSelector(getWizardState, fromWizard.getWorkflowHeaderData);
export const getValidatedEntityName = createSelector(getWizardState, state => state.entityNameValidation);
export const isShowedCrossdataCatalog = createSelector(getWizardState, state => state.isShowedCrossdataCatalog);
export const isWorkflowDebugging = createSelector(getDebugState, state => state.isDebugging);
export const getDebugConsoleSelectedTab = createSelector(getDebugState, state => state.debugConsoleSelectedTab);
export const getEnvironmentList = createSelector(getExternalDataState, state => state.environmentVariables);
export const getCustomGroups = createSelector(getExternalDataState, state => state.customGroups);
export const isShowingDebugConfig = createSelector(getDebugState, state => state.showExecutionConfig);
export const getExecutionContexts = createSelector(getDebugState, state => state.executionContexts);
export const getMultiselectionMode = createSelector(getWizardState, state => state.multiselectionMode);
export const getDraggableMode = createSelector(getWizardState, state => state.draggableMode);
export const getMlModels = createSelector(getExternalDataState, state => state.mlModels);
export const getParameters = createSelector(getExternalDataState, state => ({
  globalVariables: state.globalVariables,
  environmentVariables: state.environmentVariables,
  customGroups: state.customGroups
}));


export const getActiveAnnotation = createSelector(getWizardState, state => state.activeAnnotation);
export const getCreateNote = createSelector(getEntitiesState, state => state.createNote);

/** Annotations selectors */

export const getAnnotations = createSelector(getWizardState, state => state.annotations);
export const getCreatedAnnotation = createSelector(getWizardState, state => state.createdAnnotation);
export const showAnnotations = createSelector(getWizardState, state => state.showAnnotations);

export const getAnnotationsWithNumbers = createSelector(
  getAnnotations,
  annotations => annotations.map((annotation: WizardAnnotation, index: number): WizardAnnotation => ({
    ...annotation,
    number: index
  })));

export const getAnnotationsWithTemporal = createSelector(
  getAnnotationsWithNumbers,
  getCreatedAnnotation,
  showAnnotations,
  (annotations, createdAnnotation, isShowedAnnoteations) => isShowedAnnoteations ? [...annotations, {
    ...createdAnnotation,
    number: annotations.length
  }] : []
);


export const getNodeAnnotationsMap = createSelector(
  getAnnotationsWithTemporal,
  (annotations: Array<WizardAnnotation>) => annotations
    .filter(annotation => annotation.stepName && annotation.stepName.length)
    .reduce(function (map, obj) {
      map[obj.stepName] = obj;
      return map;
    }, {}));

export const getEdgeAnnotationsMap = createSelector(
  getAnnotationsWithTemporal,
  (annotations: Array<WizardAnnotation>) => annotations
    .filter(annotation => annotation.edge)
    .reduce(function (map, obj) {
      map[obj.edge.origin + '////' + obj.edge.destination] = obj;
      return map;
    }, {}));

export const getDraggableAnnotations = createSelector(
  getAnnotationsWithTemporal,
  (annotations: Array<WizardAnnotation>) => annotations.length ? annotations
    .filter(annotation => annotation.position) : []
);

export const getSelectedNodeAnnotations = createSelector(
  getSelectedNodeData,
  getAnnotationsWithNumbers,
  (entityData: WizardNode, annotations: WizardAnnotation[]) => entityData ? annotations.filter(annotation => {
    if (annotation.stepName && annotation.stepName === entityData.name) {
      return true;
    }
    if (annotation.edge && (annotation.edge.origin === entityData.name || annotation.edge.destination === entityData.name)) {
      return true;
    }
    return false;
  }) : null
);

export const getStepNamesFromIDs = (stepsIDs: Array<string>) => createSelector(
  getWorkflowNodes,
  (nodes: WizardNode[]) => {
    return nodes.reduce((acc, node) => {
      if (stepsIDs.includes(node.id)) {
        acc[node.id] = {
          name: node.name,
          classPrettyName: node.classPrettyName
        };
      }
      return acc;
    }, {});
  });
