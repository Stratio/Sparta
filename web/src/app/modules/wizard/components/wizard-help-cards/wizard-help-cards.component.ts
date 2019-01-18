/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

import {
  ChangeDetectionStrategy,
  Component,
  ViewEncapsulation,
  Output,
  EventEmitter,
  HostListener
} from '@angular/core';
import { TranslateService } from '@ngx-translate/core';


@Component({
  selector: 'wizard-help-cards',
  styleUrls: ['wizard-help-cards.component.scss'],
  templateUrl: 'wizard-help-cards.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None
})
export class WizardHelpCardsComponent {

  @Output() closeCardsModal = new EventEmitter<boolean>();

  public selectedCard = 0;
  public doNotShow = false;

  public cards = [
    {
      title: 'Multiple steps can be selected at once',
      image: 'assets/images/card-selection.png',
      description: 'You can select all the objects within a rectangular area of the canvas by dragging the mouse over that area where these steps are located or using the CTRL key to select them one by one.'
    },
    {
      title: 'How to zoom in and zoom out',
      image: 'assets/images/card-zoom.png',
      description: 'Clicking on the percentage indicator you will display a popup menu with the full zoom options available.'
    },
    {
      title: 'How To Navigate Around Workflow Canvas',
      image: 'assets/images/card-navigate.png',
      description: 'To pan the entire workflow, simply press and hold down the spacebar or Alt and move the mouse cursor over the canvas view.'
    },
    {
      title: 'Copy and paste any workflow box anywhere',
      image: 'assets/images/card-copy.png',
      description: 'Press Ctrl+C to copy and Ctrl+V to paste any single or multiple box selected in any workflow you want.'
    }
  ];

  constructor(private _translateService: TranslateService) { }

  @HostListener('document:keydown', ['$event'])
  onKeydownHandler(event: KeyboardEvent) {
    switch (event.keyCode) {
      case 39:
        this.selectedCard = this.selectedCard + 1 === this.cards.length ? 0 : this.selectedCard + 1;
        event.preventDefault();
        break;
      case 37:
        this.selectedCard = this.selectedCard === 0 ? this.cards.length - 1 : this.selectedCard - 1;
        event.preventDefault();
        break;
      case 27:
        this.closeCardsModal.emit(false);
        event.preventDefault();
    }
  }

  toggleDoNotShow(event) {
    this.doNotShow = event.checked;
  }
}