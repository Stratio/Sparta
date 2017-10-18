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

import { ConfigService } from '@app/core';
import { Injectable } from '@angular/core';
import { Response } from '@angular/http';
import { Observable } from 'rxjs/Rx';
import { ApiService, ApiRequestOptions } from './api.service';
import { Http } from '@angular/http';
import { Store } from '@ngrx/store';
import * as fromRoot from 'reducers';

@Injectable()
export class OutputService extends ApiService {

    constructor(private _http: Http, private configService: ConfigService, _store: Store<fromRoot.State>) {
        super(_http, _store);
    }

    getOutputList(): Observable<any> {

        const options: ApiRequestOptions = {
            method: 'get'
        };
        return this.request('template/output', options);
    }

    deleteOutput(outputId: string): Observable<any> {
        const options: ApiRequestOptions = {
            method: 'delete'
        };

        return this.request('template/output/id/' + outputId, options);
    }

    validateOutputName(outputName: string): Observable<any> {
        const options: ApiRequestOptions = {
            method: 'get'
        };

        return this.request('template/output/name/' + outputName, options);
    }

    createFragment(fragmentData: any): Observable<any> {
        const options: ApiRequestOptions = {
            method: 'post',
            body: fragmentData
        };

        return this.request('template', options);
    }

    updateFragment(fragmentData: any): Observable<any> {
        const options: ApiRequestOptions = {
            method: 'put',
            body: fragmentData
        };

        return this.request('template', options);
    }
}