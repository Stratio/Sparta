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

import { Injectable } from '@angular/core';
import { Observable } from 'rxjs/Rx';
import { ApiService} from './api.service';
import { Store } from '@ngrx/store';
import * as fromRoot from 'reducers';
import { HttpClient } from '@angular/common/http';

@Injectable()
export class TemplatesService extends ApiService {

    constructor(private _http: HttpClient, _store: Store<fromRoot.State>) {
        super(_http, _store);
    }

    getAllTemplates(): Observable<any> {
        const options: any = {};
        return this.request('template', 'get', options);
    }

    getTemplateList(templateType: string): Observable<any> {
        const options: any = {};
        return this.request('template/' + templateType, 'get', options);
    }

    deleteTemplate(templateType: string, templateId: string): Observable<any> {
        const options: any = {};
        return this.request('template/' + templateType + '/id/' + templateId, 'delete', options);
    }

    validateTemplateName(templateType: string, templateName: string): Observable<any> {
        const options: any = {};
        return this.request('template/' + templateType + '/name/' + templateName, 'get', options);
    }

    createTemplate(templateData: any): Observable<any> {
        const options: any = {
            body: templateData
        };
        return this.request('template', 'post', options);
    }

    updateFragment(fragmentData: any): Observable<any> {
        const options: any = {
            body: fragmentData
        };
        return this.request('template', 'put', options);
    }
}
