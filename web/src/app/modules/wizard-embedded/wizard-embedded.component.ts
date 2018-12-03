/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnDestroy,
  OnInit,
  Output,
  ViewChild,
  ViewContainerRef,
  EventEmitter,
  ChangeDetectorRef
} from '@angular/core';
import { WorkflowData } from '@app/wizard/wizard.models';
import { EditionConfigMode, WizardEdge, WizardNode } from '@app/wizard/models/node';
import { FloatingMenuModel } from '@app/shared/components/floating-menu/floating-menu.component';
import { StModalButton, StModalResponse, StModalService } from '@stratio/egeo';
import { CreationData } from '@app/wizard/models/drag';
import { filter as _filter, cloneDeep as _cloneDeep } from 'lodash';
import { NodeHelpersService } from '@app/wizard-embedded/_services/node-helpers.service';
import { InitializeStepService } from '@app/wizard/services/initialize-step.service';

import { GraphEditorComponent } from '@app/shared/components/graph-editor/graph-editor.component';

@Component({
  selector: 'wizard-embedded',
  styleUrls: ['wizard-embedded.styles.scss'],
  templateUrl: 'wizard-embedded.template.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})

export class WizardEmbeddedComponent implements OnInit, OnDestroy {
  public nodeName: string;
  public settingsPipelinesWorkflow: boolean;
  public editedNode: WizardNode;
  public editedNodeEditionMode: any;
  public nodeDataEdited: EditionConfigMode;
  public selectedNode = '';
  public selectedEdge;
  public creationMode: CreationData = {
    active: false,
    data: null
  };

  public nodes: WizardNode[] = [];
  public edges: WizardEdge[] = [];
  public svgPosition = { x: 0, y: 0, k: 1 };
  public nodesEditionMode: any[] = [];
  public isDirtyEditor = false;
  public isShowedInfo = false;
  public serverStepValidations: any;

  /** draw connector path */
  public drawingConnectionStatus: any;
  public connectorInitPosition: any;

  @Input() workflowData: WorkflowData;
  @Input() nodeData: EditionConfigMode;
  @Input() menuData: Array<FloatingMenuModel>;

  @Output() savePipelinesWorkflow = new EventEmitter<any>();

  @ViewChild('wizardPipelinesModal', {read: ViewContainerRef}) target: any;
  @ViewChild(GraphEditorComponent) editor: GraphEditorComponent;

  private _connectorOrigin = '';

  constructor(
    private _modalService: StModalService,
    private _cd: ChangeDetectorRef,
    private _initializeStepService: InitializeStepService,
    private _nodeHelpers: NodeHelpersService) {}

  ngOnInit(): void {
    this.nodeDataEdited = _cloneDeep(this.nodeData);
    this.editedNodeEditionMode = this.nodeDataEdited;
    this._nodeHelpers.parentNode = this.nodeDataEdited;
    this.readData();
    if (this.nodeDataEdited && this.nodeDataEdited.editionType && this.nodeDataEdited.editionType.data) {
      this.nodeName = this.nodeDataEdited.editionType.data.name;
    }
    this._modalService.container = this.target;
  }

  private readData() {
    const pipelines = this.nodeDataEdited.editionType.data.configuration.pipeline || {};
    const updatePipelinesWorkflow = (typeof pipelines === 'object') ? Object.keys(pipelines).length : pipelines.length;
    if (updatePipelinesWorkflow) {
      const pipelinesConfig = (typeof pipelines === 'object') ? pipelines : JSON.parse(pipelines);
      this.nodes = pipelinesConfig.nodes;
      this.edges = pipelinesConfig.edges;
      this.svgPosition = pipelinesConfig.svgPosition;
    }
    this._wrapNodeEditionMode();
  }

  private _wrapNodeEditionMode() {
    const internalErrors = (Object.prototype.hasOwnProperty.call(this.nodeDataEdited, 'serverValidationInternalErrors'));
    if (internalErrors && this.nodeDataEdited.serverValidationInternalErrors && this.nodeDataEdited.serverValidationInternalErrors.length) {
      this.serverStepValidations = this.nodeDataEdited.serverValidationInternalErrors
        .filter(message => message.step)
        .reduce((a, e) => {
          if (a[e.subStep]) {
            a[e.subStep].push(e.message);
          } else {
            a[e.subStep] = [e.message];
          }
          return a;
        }, {});
    }
    this.nodesEditionMode = this.nodes.map(e => {
      if (e.errors && e.errors.length) {
        if (this.serverStepValidations && Object.keys(this.serverStepValidations).length) {
          if (Object.prototype.hasOwnProperty.call(this.serverStepValidations, e.name)) {
            this.serverStepValidations[e.name] = this.serverStepValidations[e.name].concat(e.errors.map(err => err.message));
          } else {
            this.serverStepValidations[e.name] = e.errors.map(err => err.message);
          }
        } else {
          this.serverStepValidations = Object.create({
            [e.name]: e.errors.map(err => err.message)
          });
        }
      }
      return {
        editionType: {
          data: e
        },
        serverValidation: (this.serverStepValidations && Object.prototype.hasOwnProperty.call(this.serverStepValidations, e.name)) ? this.serverStepValidations[e.name] : null
      };
    });
  }

  weSavePipelinesWorkflow(save: boolean = true) {
    const data = this.nodeDataEdited.editionType.data;
    if (save) {
      data.configuration.pipeline = {
        nodes: this.nodes,
        edges: this.edges,
        svgPosition: this.editor.editorPosition
      };
    }
    data.createdNew = false;
    this.savePipelinesWorkflow.emit({
      name: this.nodeData.editionType.data.name,
      data,
      save
    });
    this.isDirtyEditor = !save;
  }

  onSelectedOption(o: any) {
    this.creationMode.active = true;
    this.creationMode.data = o;
  }

  weEditNode(event: WizardNode | string) {
    if (typeof event === 'string') {
      event = this.nodes.find(node => node.name === event);
    }
    this.editedNode = event;
  }

  weSetEditorDirty() {
    this.isDirtyEditor = true;
    this._cd.markForCheck();
  }

  weEditSettings() {
    this.settingsPipelinesWorkflow = true;
  }

  weSelectNode(event: WizardNode) {
    this.deselectAll();
    this._nodeHelpers.nodes = this.nodes;
    this._nodeHelpers.edges = this.edges;

    this.selectedNode = event.name;
    this.editedNodeEditionMode = this.nodesEditionMode.find(node => node.editionType.data.name === event.name);
    this.editedNodeEditionMode['schemas'] = {
      type: 'pipelines',
      tree: this._nodeHelpers.getInputFields(this.nodes.find(node => node.name === this.selectedNode))
    };
  }

  weSelectEdge(event: WizardEdge) {
    this.deselectAll();
    this.selectedEdge = event;
  }

  weDuplicateNode(): void {
    if (this.selectedNode) {
      this.creationMode = {
        active: true,
        data: {
          data: {
            ...this.nodes.find(node => node.name === this.selectedNode),
            name: this._initializeStepService.getNewEntityName(this.selectedNode, this.nodes)
          },
          type: 'copy'
        }
      };
    }
  }

  weDeleteSelection() {
    if (this.selectedNode && this.selectedNode.length) {
      this.deleteConfirmModal('Delete node', 'This node and its relations will be deleted.', () => {
        const nodeToDelete = this.nodes.find(element => element.name === this.selectedNode);
        if (nodeToDelete) {
          const edgesToDelete = _filter(this.edges, edge => {
            return (edge.origin === this.selectedNode || edge.destination === this.selectedNode);
          });
          edgesToDelete.forEach(edge => {
            this.edges.splice(this.edges.indexOf(edge), 1);
          });
          this.nodes.splice(this.nodes.indexOf(nodeToDelete), 1);

          this._wrapNodeEditionMode();

          this.isDirtyEditor = true;
        }
        this.deselectAll();
      });
    }
    if (this.selectedEdge) {
      const edgeToDelete: any = _filter(this.edges, this.selectedEdge).pop();
      if (edgeToDelete) {
        this.edges.splice(this.edges.indexOf(edgeToDelete), 1);
        this.isDirtyEditor = true;
      }
      this.deselectAll();
    }
  }


  private deleteConfirmModal(modalTitle: string, modalMessage: string, handler: any): void {
    const buttons: StModalButton[] = [
      { label: 'Cancel', responseValue: StModalResponse.NO, closeOnClick: true, classes: 'button-secondary-gray' },
      { label: 'Delete', responseValue: StModalResponse.YES, classes: 'button-critical', closeOnClick: true }
    ];
    this._modalService.container = this.target;
    this._modalService.show({
      modalTitle: modalTitle,
      buttons: buttons,
      maxWidth: 500,
      messageTitle: 'Are you sure?',
      message: modalMessage,
    }).take(1).subscribe((response: any) => {
      if (response === 1) {
        this._modalService.close();
      } else if (response === 0) {
        handler();
      }
    });
  }

  getEdgesMap() {
    const nodesMap = this.nodes.reduce(function (map, obj) {
      map[obj.name] = obj;
      return map;
    }, {});
    return this.edges.map((edge: WizardEdge) => ({
      origin: nodesMap[edge.origin],
      destination: nodesMap[edge.destination],
      dataType: edge.dataType
    }));
  }

  createNode(event: any) {
    const creationMode = event.creationMode;
    let entity: any = {};
    const entityData = creationMode.data;
    if (entityData.type === 'copy') {
      // if its a copy, only xsets the position
      entity = entityData.data;
    } else {
      /**  get the initial model value and validation errors */
      entity = this._initializeStepService.initializeEntity(
          this.workflowData.type,
          entityData,
          this.nodes
      );
    }
    entity.uiConfiguration = {
      position: event.position
    };
    this.creationMode.active = false;
    this.creationMode.data = null;
    entity.createdNew = true;
    this.nodes = [...this.nodes, entity];

    this._wrapNodeEditionMode();
  }

  deselectAll() {
    this.selectedNode = '';
    this.selectedEdge = null;
    this.editedNodeEditionMode = this.nodeDataEdited;
  }

  weCloseEdition() {
    this.editedNode =  null;
  }

  saveEdition(event) {
    this.nodes = this.nodes.map(e => {
      if (e.name === this.selectedNode) {
        const selectedNode = event;
        selectedNode.createdNew = false;
        return selectedNode;
      } else {
        return e;
      }
    });
    this.edges = this.edges.map(e => {
      const edge = e;
      if (e.origin === this.selectedNode) {
        edge.origin = event.name;
      }
      if (e.destination === this.selectedNode) {
        edge.destination = event.name;
      }
      return edge;
    });

    this.weCloseEdition();
    this._wrapNodeEditionMode();
    this.isDirtyEditor = true;
  }

  closeSettingsEdition() {
    this.settingsPipelinesWorkflow = false;
  }

  saveSettingsEdition(event) {
    const data = this.nodeDataEdited.editionType.data;
    Object.keys(event.configuration).map(key => {
      if (key !== 'pipeline') {
        data.configuration[key] = event.configuration[key];
      }
    });
    data.name = event.name;
    data.description = event.description;
    this.isDirtyEditor = true;
    this.closeSettingsEdition();
    this.weSavePipelinesWorkflow(false);
  }

  drawEdge(edgeEvent) {
    this._connectorOrigin = edgeEvent.name;
    this.connectorInitPosition = {
        x: edgeEvent.event.clientX,
        y: edgeEvent.event.clientY
    };
    this.drawingConnectionStatus = {
      status: true,
      name: edgeEvent.name,
    };
  }

  removeConnector() {
    this._connectorOrigin = '';
    this.connectorInitPosition = null;
    this.drawingConnectionStatus = null;
  }

  onCreateEdge(destinationEntity: any) {
    if (!this._connectorOrigin.length) {
       return;
    }
    const edge: WizardEdge = {
       origin: this._connectorOrigin,
       destination: destinationEntity.name
    };

    const existsOrigin = _filter(this.edges, {origin: edge.origin});
    const existsDestination = _filter(this.edges, {destination: edge.destination});
    if (!existsOrigin.length && !existsDestination.length) {
      this.edges = [...this.edges, edge];
      this.isDirtyEditor = true;
    }
  }

  trackByEdgeFn(index: number, item: any) {
    return index;
  }

  ngOnDestroy(): void {
    console.info('DESTROY: WizardEmbeddedComponent');
  }
}
