///
/// Copyright (C) 2015 Stratio (http://stratio.com)
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///         http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { Component, ChangeDetectorRef, OnInit, OnDestroy } from '@angular/core';
import { Store } from '@ngrx/store';
import * as fromRoot from 'reducers';
import { Observable, Subscription } from 'rxjs/Rx';
import { TranslateService } from '@ngx-translate/core';
import * as wizardActions from 'actions/wizard';


@Component({
    selector: 'wizard',
    styleUrls: ['wizard.styles.scss'],
    templateUrl: 'wizard.template.html'
})

export class WizardComponent implements OnInit, OnDestroy {

    public creationMode$: Observable<any>;
    public editionConfigMode$: Observable<any>;

    constructor(
        private _cd: ChangeDetectorRef,
        private store: Store<fromRoot.State>,
        private translate: TranslateService) {
    }

    ngOnInit(): void {
        this.creationMode$ = this.store.select(fromRoot.isCreationMode);
        this.editionConfigMode$ = this.store.select(fromRoot.getEditionConfigMode);
    }



    ngOnDestroy(): void {
    }
}
