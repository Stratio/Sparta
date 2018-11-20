/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StTableModule } from '@stratio/egeo';
import { TranslateModule } from '@ngx-translate/core';

import { RepositoryTableComponent } from './repository-table.component';
import { RepositoryTableContainer } from './repository-table.container';

import { MenuOptionsListModule } from '@app/shared/components/menu-options-list/menu-options-list.module';
import { SpTooltipModule } from '@app/shared/components/sp-tooltip/sp-tooltip.module';


@NgModule({
   exports: [
      RepositoryTableContainer
   ],
    declarations: [
      RepositoryTableComponent,
      RepositoryTableContainer
    ],
    imports: [
       CommonModule,
       MenuOptionsListModule,
       SpTooltipModule,
       StTableModule,
       TranslateModule
    ]
})

export class RepositoryTableModule { }