/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  NgZone,
  Input,
  EventEmitter,
  Output,
  ViewChild,
} from '@angular/core';
import { InitializeStepService } from '@app/wizard/services/initialize-step.service';
import { WizardNode, WizardEdge } from '@app/wizard/models/node';
import { ZoomTransform, DrawingConnectorStatus } from '@app/wizard/models/drag';
import { WizardEdgeModel } from '@app/wizard/components/wizard-edge/wizard-edge.model';
import { GraphEditorComponent } from '@app/shared/components/graph-editor/graph-editor.component';

@Component({
  selector: 'wizard-editor',
  styleUrls: ['wizard-editor.component.scss'],
  templateUrl: 'wizard-editor.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})

export class WizardEditorComponent {

  @Input() workflowNodes: Array<WizardNode> = [];
  @Input() workflowEdges: Array<WizardEdgeModel> = [];
  @Input() editorPosition: ZoomTransform;
  @Input() serverStepValidations: any = {};
  @Input() selectedNodeNames = [];
  @Input() multiselectionMode: boolean;
  @Input() creationMode: any;
  @Input() selectedEdge: any;
  @Input() debugResult: any;

  @Output() setEditorDirty = new EventEmitter();
  @Output() deselectEntityCreation = new EventEmitter();
  @Output() createNode = new EventEmitter<any>();
  @Output() onCreateEdge = new EventEmitter<WizardEdge>();
  @Output() selectNode = new EventEmitter<WizardNode>();
  @Output() editEntity = new EventEmitter<WizardNode>();
  @Output() showEdgeOptions = new EventEmitter<any>();
  @Output() selectEdge = new EventEmitter<any>();

  @ViewChild(GraphEditorComponent) editor: GraphEditorComponent;

  public drawingConnectionStatus: DrawingConnectorStatus = {
    status: false,
    name: ''
  };

  public connectorOrigin = '';
  public connectorPosition: ZoomTransform = null;

  constructor(
    private _initializeStepService: InitializeStepService,
    private _cd: ChangeDetectorRef,
    private _ngZone: NgZone
  ) { }

  createEdge(edgeEvent) {
    this.connectorOrigin = edgeEvent.name;
    this.drawingConnectionStatus = {
      status: true,
      name: edgeEvent.name,
      initPosition: {
        x: edgeEvent.event.clientX,
        y: edgeEvent.event.clientY
      }
    };
  }

  removeConnector() {
    this.connectorOrigin = '';
    this.drawingConnectionStatus = {
      status: false,
      name: ''
    };
  }

  finishConnector(destinationEntity: any) {
    if (this.connectorOrigin.length) {
      this.onCreateEdge.emit({
        origin: this.connectorOrigin,
        destination: destinationEntity.name
      });
    }
  }

  getEditorRef() {
    return this.editor;
  }

  trackBySegmentFn(index: number, item: any) {
    return index; // or item.id
  }
}
