/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

import { Injectable } from '@angular/core';
import { Actions, Effect, ofType } from '@ngrx/effects';
import { Store, Action } from '@ngrx/store';
import { Observable, timer, of, forkJoin, from, iif } from 'rxjs';

import * as errorActions from 'actions/errors';
import * as executionsActions from '../actions/executions';
import * as fromRoot from '../reducers';
import { ExecutionService } from 'services/execution.service';
import { isEqual } from 'lodash';

import { ExecutionHelperService } from 'app/services/helpers/execution.service';
import { switchMap, takeUntil, concatMap, catchError, withLatestFrom, mergeMap, map } from 'rxjs/operators';

const normalizeFilter = (filter, archived) => {
    return {
        archived,
        status: filter.statusFilter || undefined,
        executionEngine: filter.typeFilter || undefined,
        searchText: filter.searchQuery || undefined,
        page: filter.pagination.currentPage - 1,
        offset: filter.pagination.perPage,
        date: filter.timeIntervalFilter || undefined
    };
};

@Injectable()
export class ExecutionsEffect {

   private _lastExecutionsValue: any;

   @Effect()
   getExecutionsList$: Observable<any> = this.actions$
      .ofType(executionsActions.LIST_EXECUTIONS)
      .pipe(switchMap(() => timer(0, 5000)
         .pipe(takeUntil(this.actions$.ofType(executionsActions.CANCEL_EXECUTION_POLLING)))
         .pipe(withLatestFrom(this.store.select(state => state.executions.executions)))
         .pipe(concatMap( ([status, filter]) => this._executionService.getExecutionsByQuery(normalizeFilter(filter, false))
            .pipe(map(executions => {
                if (isEqual(executions, this._lastExecutionsValue)) {
                    return new executionsActions.ListExecutionsEmptyAction();
                }
                this._lastExecutionsValue = executions;
                return new executionsActions.ListExecutionsCompleteAction(
                    executions.map(execution => this._executionHelperService.normalizeExecution(execution)));
                }))
            .pipe(catchError(err => of(new executionsActions.ListExecutionsFailAction())))))));


   @Effect()
   getArhivedExecutionsList: Observable<any> = this.actions$
      .ofType(executionsActions.LIST_ARCHIVED_EXECUTIONS)
      .pipe(withLatestFrom(this.store.select(state => state.executions.executions)))
      .pipe(switchMap(([executions, filter]) => this._executionService.getExecutionsByQuery(normalizeFilter(filter, true))
         .map((archived: Array<any>) => new executionsActions.ListArchivedExecutionsCompleteAction(archived.map(execution =>
            this._executionHelperService.normalizeExecution(execution))))))
      .pipe(catchError(error => of(new executionsActions.ListArchivedExecutionsFailAction())));

   @Effect()
   deleteExecution: Observable<any> = this.actions$
      .ofType(executionsActions.DELETE_EXECUTION)
      .pipe(map((action: any) => action.executionId))
      .pipe(withLatestFrom(this.store.select(state => state.executions.executions.isArchivedPage)))
      .pipe(switchMap(([executionId, isArchivedPage]: [string, boolean]) => this._executionService.deleteExecution(executionId)
         .pipe(mergeMap((() => [new executionsActions.DeleteExecutionCompleteAction(executionId), isArchivedPage ? new executionsActions.ListArchivedExecutionsAction() : new executionsActions.ListExecutionsAction()])))
      .catch((error) => from([new executionsActions.DeleteExecutionErrorAction(), new errorActions.ServerErrorAction(error)]))));



   @Effect()
   archiveExecutions: Observable<any> = this.actions$
      .ofType(executionsActions.ARCHIVE_EXECUTIONS)
      .pipe(withLatestFrom(this.store.select(state => state.executions.executions.selectedExecutionsIds)))
      .pipe(switchMap(([action, ids]) => {
         const observables: any = [];
         ids.forEach(id => observables.push(this._executionService.archiveExecution(id, true)));
         return forkJoin(observables)
            .pipe(mergeMap(() => [new executionsActions.ListExecutionsAction(), new executionsActions.ArchiveExecutionsCompleteAction()]))
            .pipe(catchError(error => of(new executionsActions.ArchiveExecutionsFailAction())));
      }));

   @Effect()
   unarchiveExecutions: Observable<any> = this.actions$
      .ofType(executionsActions.UNARCHIVE_EXECUTIONS)
      .pipe(withLatestFrom(this.store.select(state => state.executions.executions.selectedExecutionsIds)))
      .pipe(switchMap(([action, ids]) => {
         const observables: any = [];
         ids.forEach(id => observables.push(this._executionService.archiveExecution(id, false)));
         return forkJoin(observables)
            .pipe(mergeMap(() => [
               new executionsActions.ListArchivedExecutionsAction(),
               new executionsActions.UnarchiveExecutionsCompleteAction()]))
            .pipe(catchError(error => of(new executionsActions.UnarchiveExecutionsFailAction())));
      }));


   @Effect()
   selectStatusFilter: Observable<any> = this.actions$
      .ofType(executionsActions.SELECT_STATUS_FILTER, executionsActions.SELECT_TYPE_FILTER, executionsActions.SELECT_TIME_INTERVAL_FILTER, executionsActions.CHANGE_PAGINATION, executionsActions.SEARCH_EXECUTION)
      .pipe(withLatestFrom(this.store.select(state => state.executions.executions.isArchivedPage)))
      .pipe(switchMap(([action, archived]: [any, boolean]) =>
        iif(() => archived,
            of(new executionsActions.ListArchivedExecutionsAction()),
            of(new executionsActions.ListExecutionsAction()))));

    @Effect()
   stopExecutionsList$: Observable<any> = this.actions$
      .ofType(executionsActions.STOP_EXECUTIONS_ACTION)
      .pipe(withLatestFrom(this.store.select(state => state.executions.executions.selectedExecutionsIds)))
      .pipe(switchMap(([action, ids]) => {
         const observables: any = [];
         ids.forEach(id => observables.push(this._executionService.stopExecutionsById(id)));

         return forkJoin(observables)
            .pipe(mergeMap((results: any) => {
               const actions: Array<Action> = [];
               if (results.length) {
                  actions.push(new executionsActions.ListExecutionsAction());
               }
               return actions;
            }))
            .pipe(catchError(error => of(new executionsActions.ListExecutionsFailAction())));
      }));


   @Effect()
   getExecutionInfo$: Observable<Action> = this.actions$
      .ofType(executionsActions.GET_WORKFLOW_EXECUTION_INFO)
      .pipe(switchMap((data: any) => this._executionService.getWorkflowExecutionInfo(data.payload.id)
         .map((response: any) => new executionsActions.GetExecutionInfoCompleteAction({
            ...response,
            name: data.payload.name
         })).catch(error => from([new executionsActions.GetExecutionInfoErrorAction(), new errorActions.ServerErrorAction(error)]))));

   constructor(
      private actions$: Actions,
      private _executionHelperService: ExecutionHelperService,
      private store: Store<fromRoot.State>,
      private _executionService: ExecutionService
   ) { }
}
